# Credit Service

The **counterparty credit-check capability** extracted out of the RFQ monolith
using the **strangler pattern**. It owns counterparty credit state and the
reserve / release / query operations the monolith's `RFQExecutionSaga` depends
on. The monolith keeps calling the same in-process `CreditService` interface;
which adapter runs behind it (in-process vs. this service over HTTP) is a config
toggle, so the seam can be flipped without touching callers.

- Java 11, Spring Boot, Spring Data JPA, H2 in-memory database, Maven
- Runs on port **8060** (the monolith runs on 8080)

## Why this seam

Credit checks are the cleanest seam to extract first:

- **Self-contained state.** Credit lives entirely on `Counterparty`
  (`creditLimit`, `availableCredit`). The reserve/release operations need no
  bond or RFQ data, so the boundary is narrow.
- **Reversible operations.** `reserve` and `release` are exact inverses, which
  is what makes the trade saga able to compensate when a later local step
  fails. Bond/reference data, by contrast, is read on the hot path of every RFQ
  and offers no equally clean transactional boundary.
- **Single owner of an invariant.** The "never overdraw available credit"
  invariant becomes wholly owned by this service.

## Credit invariant

A counterparty's `availableCredit` may never go negative. It is enforced in one
place — `Counterparty.deductCredit` — which throws `InsufficientCreditException`
(rather than mutating state) when a reservation would overdraw. `reserve` and
`release` run inside `@Transactional` methods on `CreditRiskEngine`.

## API contract

Base URL: `http://localhost:8060`

| Method | Path                     | Body                          | Success | Errors |
|--------|--------------------------|-------------------------------|---------|--------|
| POST   | `/credit/reservations`   | `{ "counterpartyId", "amount" }` | `200` + credit view | `409` insufficient credit, `404` unknown counterparty |
| POST   | `/credit/releases`       | `{ "counterpartyId", "amount" }` | `200` + credit view | `404` unknown counterparty |
| GET    | `/credit/{counterpartyId}` | —                           | `200` + credit view | `404` unknown counterparty |

Counterparty CRUD is also exposed under `/counterparties` for administration and
seeding parity with the monolith.

**Request** (`CreditOperationRequest`):

```json
{ "counterpartyId": 1, "amount": 1000000.00 }
```

**Response** (`CreditView`):

```json
{ "counterpartyId": 1, "creditLimit": 50000000.00, "availableCredit": 49000000.00 }
```

`409 Conflict` carries reason `"Insufficient credit"`; the monolith's
`RemoteCreditService` maps `409 -> InsufficientCreditException` and
`404 -> ResourceNotFoundException`, so caller-facing behavior is identical to
the in-process path.

## Seed data

On startup the service seeds **Acme Asset Management**
(LEI `549300EXAMPLE12345678`, credit limit `$50,000,000`) so its counterparty id
matches the monolith's seed for local round-trip testing.

## Running

```bash
cd credit-service
./mvnw spring-boot:run
```

## Testing

```bash
cd credit-service
./mvnw clean test
```

`CreditServiceIntegrationTest` covers: reserve-then-release preserves the credit
invariant, reserving beyond available credit returns `409`, and reserving for an
unknown counterparty returns `404`.
