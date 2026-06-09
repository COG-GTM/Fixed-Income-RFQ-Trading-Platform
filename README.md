# Fixed-Income RFQ Trading Platform

A SpringBoot system simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

The platform is split into two independently deployable Spring Boot applications that communicate over HTTP/REST:

- **`monolith/`** — RFQ and Bond management (port `8080`)
- **`counterparty-service/`** — Counterparty management, extracted as a standalone microservice (port `8081`)

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database (one per service)
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Architecture](#architecture)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Seed Data](#seed-data)
  - [Running the Application](#running-the-application)
  - [Testing](#testing)

## Architecture

The Counterparty Service has been extracted from the monolith into its own microservice with its own
H2 database. The monolith no longer holds a JPA relationship to `Counterparty`; instead an `Rfq` stores
the `counterpartyId` as a plain column, and the RFQ execution flow talks to the Counterparty Service over
HTTP via `CounterpartyServiceProxy` / `CounterpartyServiceRestClient`.

```
  ┌──────────────────────────┐         HTTP/REST          ┌────────────────────────────┐
  │  monolith  (port 8080)   │ ─────────────────────────▶ │ counterparty-service (8081) │
  │  - Bond                  │   POST /counterparties/    │  - Counterparty              │
  │  - Rfq (counterpartyId)  │        {id}/validate       │  - own H2 database           │
  │  - RFQExecutionSaga      │   POST /counterparties/    │  - trade confirmations       │
  │                          │        {id}/deduct-credit  │                              │
  └──────────────────────────┘                            └────────────────────────────┘
```

When executing an RFQ, the monolith:
1. Loads the bond locally and validates the counterparty over HTTP (`/validate`).
2. Deducts the bond notional locally.
3. Deducts the counterparty credit over HTTP (`/deduct-credit`).

Because the credit deduction is now a remote call, the operation is no longer a single ACID transaction.
The monolith fails the RFQ if either remote call returns an error (404 → `ResourceNotFoundException`,
400 → `InsufficientCreditException`).

## Domain Entities

**Counterparty** (owned by `counterparty-service`):
 - name
 - lei (Legal Entity Identifier)
 - creditLimit (BigDecimal)
 - availableCredit (BigDecimal)

**Bond** (owned by `monolith`):
 - isin (unique, e.g. US912828YK15)
 - issuer
 - couponRate (BigDecimal)
 - maturityDate (LocalDate)
 - availableNotional (BigDecimal — par amount available for trading)

**RFQ** (Request for Quote, owned by `monolith`):
 - counterpartyId (plain reference to a counterparty in the Counterparty Service)
 - bond (manyToOne)
 - notionalAmount (BigDecimal — par amount requested)
 - side (BUY / SELL)
 - status (PENDING / QUOTED / EXECUTED / REJECTED)
 - executionPrice (BigDecimal — total settlement amount)
 - createdAt (Instant)

A counterparty (in the Counterparty Service) and a bond (in the monolith) must be in place before
executing an RFQ. If the bond has insufficient available notional or the counterparty has insufficient
available credit, the RFQ is rejected. The core logic is in `RFQExecutionSaga.java`.

A PATCH method endpoint exists for both the `Counterparty` controller (in the Counterparty Service) and
the `Bond` controller (in the monolith) to update credit / notional inventory. A trade confirmation is
sent whenever credit is added to a counterparty, handled by `TradeConfirmationService.java` inside the
Counterparty Service.

## REST Endpoints

### Counterparty Service (port `8081`)

| Method | Path                              | Description                                          |
|--------|-----------------------------------|------------------------------------------------------|
| GET    | `/counterparties`                 | List all counterparties                              |
| POST   | `/counterparties`                 | Create a counterparty                                |
| GET    | `/counterparties/{id}`            | Get a counterparty by ID                             |
| PUT    | `/counterparties/{id}`            | Update a counterparty                                |
| PATCH  | `/counterparties/{id}`            | Add or deduct credit (JSON: `amount`, `operation`)   |
| POST   | `/counterparties/{id}/validate`   | Validate a counterparty exists (returns it)          |
| POST   | `/counterparties/{id}/deduct-credit` | Deduct credit (JSON: `amount`); 400 if insufficient |
| DELETE | `/counterparties/{id}`            | Delete a counterparty                                |

### Monolith (port `8080`)

| Method | Path                   | Description                                      |
|--------|------------------------|--------------------------------------------------|
| GET    | `/bonds`               | List all bonds                                   |
| POST   | `/bonds`               | Create a bond                                    |
| GET    | `/bonds/{id}`          | Get a bond by ID                                 |
| PUT    | `/bonds/{id}`          | Update a bond                                    |
| PATCH  | `/bonds/{id}`          | Add or deduct notional (JSON: `amount`, `operation`) |
| DELETE | `/bonds/{id}`          | Delete a bond                                    |
| GET    | `/rfqs`                | List all RFQs                                    |
| POST   | `/rfqs`                | Execute an RFQ                                   |
| GET    | `/rfqs/{id}`           | Get an RFQ by ID                                 |
| DELETE | `/rfqs/{id}`           | Delete an RFQ                                    |

## Seed Data

On startup the **Counterparty Service** loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000) — id `1`

On startup the **monolith** loads:
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED, referencing counterparty id `1`

## Running the Application

Start the Counterparty Service first so the monolith can reach it:

```bash
cd counterparty-service
./mvnw spring-boot:run
```

Then, in another terminal, start the monolith:

```bash
cd monolith
./mvnw spring-boot:run
```

The monolith starts on port `8080` and the Counterparty Service on port `8081`. The monolith reads the
Counterparty Service base URL from `counterpartyservice.url` in `application.properties`.

Hit `/bonds` and `/rfqs` on `8080`, and `/counterparties` on `8081`, to verify the REST endpoints.

## Testing

Each service has its own test suite:

```bash
cd monolith && ./mvnw clean test
cd counterparty-service && ./mvnw clean test
```

The monolith's `IntegrationTest.java` mocks `CounterpartyServiceProxy` so RFQ tests run without a live
Counterparty Service, covering RFQ execution, insufficient notional/credit, and missing counterparty/bond
scenarios. The Counterparty Service's `CounterpartyServiceIntegrationTest.java` covers CRUD, validation,
and credit deduction.
