# Fixed-Income RFQ Trading Platform

A SpringBoot platform simulating a **fixed-income Request-for-Quote (RFQ) trading platform**, being migrated from a monolith to services using the strangler pattern.

Modules:
 - `monolith` — RFQ execution, counterparties, bonds (port `8080`)
 - `confirmation-service` — trade confirmations, extracted from the monolith (port `8070`)

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven (multi-module)

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Trade Confirmations](#trade-confirmations)
  - [Seed Data](#seed-data)
  - [Running the Application](#running-the-application)
  - [Testing](#testing)

## Domain Entities

Three domain entities:
 - **Counterparty**
   - name
   - lei (Legal Entity Identifier)
   - creditLimit (BigDecimal)
   - availableCredit (BigDecimal)
 - **Bond**
   - isin (unique, e.g. US912828YK15)
   - issuer
   - couponRate (BigDecimal)
   - maturityDate (LocalDate)
   - availableNotional (BigDecimal — par amount available for trading)
 - **RFQ** (Request for Quote)
   - counterparty (manyToOne)
   - bond (manyToOne)
   - notionalAmount (BigDecimal — par amount requested)
   - side (BUY / SELL)
   - status (PENDING / QUOTED / EXECUTED / REJECTED)
   - executionPrice (BigDecimal — total settlement amount)
   - createdAt (Instant)

Counterparty and Bond must be in place before executing an RFQ. If the bond has insufficient available notional or the counterparty has insufficient available credit, an exception will be thrown. The core logic is in `RFQExecutionSaga.java`, which attempts to execute an RFQ in a single transaction.

A PATCH method endpoint exists for both `Counterparty` and `Bond` controllers to update credit / notional inventory.

A trade confirmation is sent to a counterparty whenever credit is added — see [Trade Confirmations](#trade-confirmations).

## REST Endpoints

| Method | Path                   | Description                                      |
|--------|------------------------|--------------------------------------------------|
| GET    | `/counterparties`      | List all counterparties                          |
| POST   | `/counterparties`      | Create a counterparty                            |
| GET    | `/counterparties/{id}` | Get a counterparty by ID                         |
| PUT    | `/counterparties/{id}` | Update a counterparty                            |
| PATCH  | `/counterparties/{id}` | Add or deduct credit (JSON: `amount`, `operation`) |
| DELETE | `/counterparties/{id}` | Delete a counterparty                            |
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

The `confirmation-service` exposes:

| Method | Path                   | Description                                      |
|--------|------------------------|--------------------------------------------------|
| POST   | `/confirmations`       | Record a trade confirmation (JSON: `counterpartyName`, `creditAmount`) |
| GET    | `/confirmations`       | List all trade confirmations                     |
| GET    | `/confirmations/{id}`  | Get a trade confirmation by ID                   |

## Trade Confirmations

Trade confirmation is the first responsibility carved out of the monolith. The monolith
delivers confirmations through the `TradeConfirmationSender` seam, and the
`use.confirmation.service` toggle selects the implementation:

| `use.confirmation.service` | Implementation                  | Behaviour                                            |
|----------------------------|---------------------------------|------------------------------------------------------|
| `false` (default)          | `LocalTradeConfirmationSender`  | Confirmation handled in-process by the monolith       |
| `true`                     | `RemoteTradeConfirmationSender` | `POST {confirmation.service.url}confirmations/` to the `confirmation-service` |

Everything else — RFQ execution and its credit / notional adjustments, the
PENDING / QUOTED / EXECUTED / REJECTED statuses, and every HTTP response — is
identical in both modes.

## Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Running the Application

Run the monolith on its own (confirmations handled in-process):

```bash
cd monolith
./mvnw spring-boot:run
```

The application starts on port `8080`. Hit `/counterparties`, `/bonds`, and `/rfqs` to verify the REST endpoints.

To run against the extracted service, start it first:

```bash
./mvnw -pl confirmation-service spring-boot:run
```

The confirmation-service starts on port `8070`; hit `/confirmations` to verify it. Then start the
monolith with the toggle on:

```bash
cd monolith
./mvnw spring-boot:run -Dspring-boot.run.arguments=--use.confirmation.service=true
```

Adding credit (`PATCH /counterparties/{id}` with `{"amount":"250000.00","operation":"ADD"}`) now shows up
under `GET http://localhost:8070/confirmations`. Point the monolith at a different host with
`--confirmation.service.url=http://<host>:<port>/`.

## Testing

Build and test every module from the repository root:

```bash
./mvnw clean verify
```

See `IntegrationTest.java` for the use-cases covering RFQ execution, insufficient notional/credit, and
missing counterparty/bond scenarios. `AbstractConfirmationParityIntegrationTest.java` runs one suite
twice — once per value of `use.confirmation.service` — proving both paths behave identically, and
`ConfirmationApiIntegrationTest.java` covers the confirmation-service API.
