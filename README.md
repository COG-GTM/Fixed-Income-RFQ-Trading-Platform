# Fixed-Income RFQ Trading Platform

A SpringBoot monolith simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
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

## Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Running the Application

```bash
cd monolith
./mvnw spring-boot:run
```

The application starts on port `8080`. Hit `/counterparties`, `/bonds`, and `/rfqs` to verify the REST endpoints.

## Testing

```bash
cd monolith
./mvnw clean test
```

See `IntegrationTest.java` for the full set of use-cases covering RFQ execution, insufficient notional/credit, and missing counterparty/bond scenarios.

## Strangler Extraction: Credit Service

The **counterparty credit-check capability** is being strangled out of the
monolith into a standalone [`credit-service`](credit-service/README.md) (port
`8060`). The extraction is transparent to callers of the monolith's `/rfqs` API.

### Seam chosen

Credit checks, not bond/reference data. Credit state is self-contained on
`Counterparty`, the reserve/release operations are exact inverses (so they can
be compensated), and a single invariant — "never overdraw available credit" —
can be owned end-to-end by the new service. See the
[credit-service README](credit-service/README.md#why-this-seam) for the full
rationale and API contract.

### The seam (port + adapters)

`RFQExecutionSaga` depends only on the `CreditService` port
(`monolith/.../credit/CreditService.java`), never on where credit lives. Two
adapters implement it, selected by a property:

| Adapter | Active when | Behavior |
|---------|-------------|----------|
| `LocalCreditService` | `rfq.credit.service.remote=false` (default) | In-process against the monolith's own `CounterpartyRepository`; joins the RFQ transaction — identical to the original monolith. |
| `RemoteCreditService` | `rfq.credit.service.remote=true` | HTTP calls to the credit-service at `rfq.credit.service.url`; maps `409 -> InsufficientCreditException`, `404 -> ResourceNotFoundException`. |

### Preserved invariants & atomicity

- **Credit invariant** — a counterparty never spends beyond available credit;
  enforced by whichever `CreditService` adapter is active.
- **Notional invariant** — a bond never sells more than its available notional;
  enforced locally by `Bond.deductNotional`.
- **Atomicity** — credit is reserved through the port first; bond-notional
  deduction and RFQ persistence then run in the local transaction. If that local
  step fails, the saga issues a compensating `releaseCredit`, so a reservation is
  never left dangling even when credit lives in a separate service outside the
  monolith's transaction. Caller-facing outcomes (`201` executed, `400`
  insufficient credit/notional, `404` unknown counterparty/bond) are unchanged.

### Migration steps

1. **Now (default):** `rfq.credit.service.remote=false` — `LocalCreditService`
   runs in-process; original behavior, no service dependency.
2. **Cut over:** start the credit-service, then set
   `rfq.credit.service.remote=true` and `rfq.credit.service.url` (default
   `http://localhost:8060/`). `RemoteCreditService` routes credit checks to the
   service; the saga's compensation now spans the process boundary.
3. **Roll back** at any time by flipping the property back to `false`.

### Tests

- `monolith/.../IntegrationTest.java` — original RFQ use-cases (unchanged), run
  against the default in-process adapter.
- `monolith/.../RemoteCreditRfqIntegrationTest.java` — drives `/rfqs` with
  `rfq.credit.service.remote=true`, emulating the credit-service with
  `MockRestServiceServer`: remote reserve + local notional deduction, remote
  insufficient credit (`409 -> 400`, notional untouched), and compensation
  (`releaseCredit`) when local notional is insufficient after a successful
  reservation.
- `credit-service/.../CreditServiceIntegrationTest.java` — the service's own
  reserve/release/query contract.
