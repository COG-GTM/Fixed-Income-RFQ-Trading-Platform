# Fixed-Income RFQ Trading Platform

A **fixed-income Request-for-Quote (RFQ) trading platform** made of two Spring Boot
applications:

| Module           | Port   | Owns                                                     |
|------------------|--------|----------------------------------------------------------|
| `monolith/`      | `8080` | Bond inventory, RFQs, and the RFQ execution saga           |
| `credit-service/`| `8081` | The Counterparty aggregate and its credit reservations     |

Each application has its own H2 datastore and its own Maven build; the monolith
talks to the credit service over HTTP (`creditservice.url`).

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [RFQ Execution Saga](#rfq-execution-saga)
  - [REST Endpoints](#rest-endpoints)
  - [Seed Data](#seed-data)
  - [Running the Application](#running-the-application)
  - [Testing](#testing)

## Domain Entities

Owned by the **credit service**:
 - **Counterparty**
   - name
   - lei (Legal Entity Identifier)
   - creditLimit (BigDecimal)
   - availableCredit (BigDecimal)
 - **CreditReservation**
   - counterpartyId
   - amount (BigDecimal)
   - status (RESERVED / RELEASED)

Owned by the **monolith**:
 - **Bond**
   - isin (unique, e.g. US912828YK15)
   - issuer
   - couponRate (BigDecimal)
   - maturityDate (LocalDate)
   - availableNotional (BigDecimal — par amount available for trading)
 - **RFQ** (Request for Quote)
   - counterpartyId (reference to the counterparty in the credit service)
   - bond (manyToOne)
   - notionalAmount (BigDecimal — par amount requested)
   - side (BUY / SELL)
   - status (PENDING / QUOTED / EXECUTED / REJECTED)
   - executionPrice (BigDecimal — total settlement amount)
   - createdAt (Instant)

Counterparty and Bond must be in place before executing an RFQ. If the bond has insufficient available notional or the counterparty has insufficient available credit, an exception will be thrown.

## RFQ Execution Saga

Credit and notional live in two different databases, so a single local
`@Transactional` method can no longer keep them consistent. `RFQExecutionSaga.java`
runs the trade as a saga instead:

1. **Reserve** credit on the credit service (`POST /counterparties/{id}/reservations`).
   Insufficient credit is rejected there with `409 Conflict`, which
   `CreditServiceClient` translates back into `InsufficientCreditException`.
2. **Book** locally in one transaction: deduct the bond notional and persist the RFQ.
3. **Confirm**, or **compensate**: if the local booking fails (for example
   `InsufficientNotionalException`), the local transaction rolls back and the saga
   releases the remote reservation
   (`DELETE /counterparties/{id}/reservations/{reservationId}`) before rethrowing,
   so the counterparty is never left paying for a trade that did not happen.

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
| GET    | `/rfqs`                | List all RFQs                                    |
| POST   | `/rfqs`                | Execute an RFQ                                   |
| GET    | `/rfqs/{id}`           | Get an RFQ by ID                                 |
| DELETE | `/rfqs/{id}`           | Delete an RFQ                                    |

The monolith keeps every `/counterparties` endpoint above and proxies it to the
credit service, so existing callers are unaffected. The credit service exposes the
same counterparty endpoints on port `8081`, plus the reservation API:

| Method | Path                                                | Description                                        |
|--------|-----------------------------------------------------|----------------------------------------------------|
| POST   | `/counterparties/{id}/reservations`                  | Reserve credit; `409` when credit is insufficient  |
| GET    | `/counterparties/{id}/reservations/{reservationId}`  | Get a reservation                                  |
| DELETE | `/counterparties/{id}/reservations/{reservationId}`  | Release a reservation (compensation, idempotent)   |

## Seed Data

On startup the credit service loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)

and the monolith loads:
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Running the Application

Start the credit service first, since the monolith depends on it:

```bash
cd credit-service
./mvnw spring-boot:run
```

then, in another terminal:

```bash
cd monolith
./mvnw spring-boot:run
```

The monolith starts on port `8080` and the credit service on port `8081`. Hit
`/counterparties`, `/bonds`, and `/rfqs` on `8080` to verify the REST endpoints.

## Testing

Each module is built and tested independently:

```bash
cd credit-service
./mvnw clean test

cd ../monolith
./mvnw clean test
```

The credit service tests cover the counterparty and reservation API, including the
`409` on insufficient credit and the release endpoint used for compensation.

In the monolith, `IntegrationTest.java` covers RFQ execution, insufficient
notional/credit, and missing counterparty/bond scenarios, and
`RfqExecutionSagaIntegrationTest.java` covers the distributed flow: successful
execution, insufficient-credit rejection, and the compensation path. Both point
`creditservice.url` at `CreditServiceStub`, an in-process HTTP double of the credit
service, so the monolith build stays self-contained while still exercising the saga
over real HTTP.
