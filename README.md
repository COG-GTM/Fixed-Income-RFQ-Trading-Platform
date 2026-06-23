# Fixed-Income RFQ Trading Platform

A SpringBoot application simulating a **fixed-income Request-for-Quote (RFQ) trading platform**, being incrementally split out of a monolith into microservices.

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

## Modules

| Module        | Port | Responsibility                                              |
|---------------|------|-------------------------------------------------------------|
| `monolith`    | 8080 | Bond inventory & counterparty credit (origin monolith)      |
| `rfq-service` | 8073 | RFQ Execution orchestrator (saga) — see `rfq-service/README.md` |

The **RFQ Execution** bounded context has been extracted into the independent
`rfq-service` microservice (WP C). It no longer runs inside the monolith; instead
it orchestrates a distributed saga over the Bond Service (port 8071) and
Counterparty Service (port 8072), compensating the bond notional deduction if the
credit deduction fails. See [`rfq-service/README.md`](rfq-service/README.md).

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Seed Data](#seed-data)
  - [Running the Application](#running-the-application)
  - [Testing](#testing)

## Domain Entities

The monolith retains two domain entities:
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

The **RFQ** (Request for Quote) entity and its execution logic now live in the
`rfq-service` microservice. Counterparty and Bond must be in place before executing
an RFQ; if the bond has insufficient available notional or the counterparty has
insufficient available credit, the execution is rejected. The core logic is in
`rfq-service`'s `RFQExecutionSaga.java`, which orchestrates a distributed saga with
a compensating transaction.

A PATCH method endpoint exists for both `Counterparty` and `Bond` controllers to update credit / notional inventory.

A trade confirmation is sent to a counterparty whenever credit is added, handled by `TradeConfirmationService.java`.

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

The `/rfqs` endpoints are now served by the `rfq-service` microservice on port 8073.

## Seed Data

On startup the monolith loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)

## Running the Application

```bash
cd monolith
./mvnw spring-boot:run
```

The application starts on port `8080`. Hit `/counterparties` and `/bonds` to verify the REST endpoints. For the `/rfqs` endpoints, run the `rfq-service` (see [`rfq-service/README.md`](rfq-service/README.md)).

## Testing

```bash
cd monolith
./mvnw clean test
```

See `IntegrationTest.java` for the monolith's bond/counterparty use-cases. RFQ execution and saga compensation are covered by tests in `rfq-service`.
