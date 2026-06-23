# RFQ Execution Service (WP C)

The **RFQ Execution bounded context** extracted from the monolith into an independent
orchestrator microservice. It owns the RFQ records and coordinates trade execution
across the other two services that were extracted earlier:

| Service                          | Default port | Responsibility            |
| -------------------------------- | ------------ | ------------------------- |
| Bond Inventory (WP A)            | `8071`       | Available notional        |
| Counterparty / Credit (WP B)     | `8072`       | Available credit          |
| **RFQ Execution (WP C, this)**   | `8073`       | RFQ records + saga        |

## Why a saga instead of `@Transactional`

In the monolith, `RFQExecutionSaga.executeRfq()` ran inside a single
`@Transactional` boundary: the bond notional deduction, the counterparty credit
deduction and the RFQ insert all committed or rolled back atomically against one
database.

Now that bond inventory and counterparty credit live in **separate services with
separate databases**, a single ACID transaction is no longer possible. The
orchestrator instead drives a **saga** with a **compensating transaction**.

## Execution flow

```
POST /rfqs
   │
   ├─ 1. Bond Service        PATCH /bonds/{bondId}
   │                         {"amount": notionalAmount, "operation": "DEDUCT"}
   │
   ├─ 2. Counterparty Service PATCH /counterparties/{counterpartyId}
   │                         {"amount": executionPrice, "operation": "DEDUCT"}
   │
   │       step 2 FAILS ─────► COMPENSATE
   │                         PATCH /bonds/{bondId}
   │                         {"amount": notionalAmount, "operation": "ADD"}
   │                         then re-throw the failure (no RFQ is persisted)
   │
   └─ 3. both steps OK ─────► save Rfq locally with status EXECUTED
```

1. **Deduct notional** on the Bond Service. If the bond is missing the call fails
   with `404` (→ `ResourceNotFoundException`); if there is not enough notional it
   fails with `400` (→ `InsufficientNotionalException`). In either case nothing has
   changed yet, so we simply propagate the error.
2. **Deduct credit** on the Counterparty Service.
3. **Compensation** — if step 2 fails for any reason, the orchestrator issues a
   compensating `ADD` to the Bond Service to restore the notional it deducted in
   step 1, then re-throws the original failure. No RFQ row is written.
4. **Persist** — only when both remote steps succeed is the `Rfq` saved locally
   with status `EXECUTED`.

Because the two remote calls are not atomic, there is a small window where the
notional has been deducted but the credit has not yet been confirmed. The
compensating `ADD` is what restores consistency when the second step fails. This
is the classic trade-off of the saga pattern: eventual consistency in exchange for
service autonomy.

## Data model

The `Rfq` entity no longer has JPA `@ManyToOne` relationships to `Counterparty`
and `Bond` (those entities live in other services / databases). It stores plain
identifiers instead:

```java
@NotNull private long counterpartyId;
@NotNull private long bondId;
```

All other fields are unchanged: `notionalAmount`, `side` (`BUY`/`SELL`),
`status`, `executionPrice`, and `createdAt` (set via `@PrePersist`).

## REST API

| Method   | Path          | Description                                            |
| -------- | ------------- | ------------------------------------------------------ |
| `GET`    | `/rfqs`       | List all RFQs (`RfqDto`)                                |
| `POST`   | `/rfqs`       | Execute an RFQ via the saga, returns `201 Created`     |
| `GET`    | `/rfqs/{id}`  | Get one RFQ                                             |
| `DELETE` | `/rfqs/{id}`  | Delete an RFQ                                           |

`RfqDto` payload:

```json
{
  "counterpartyId": 1,
  "bondId": 1,
  "notionalAmount": 1000000.00,
  "side": "BUY",
  "executionPrice": 998750.00
}
```

## Configuration (`application.properties`)

```properties
server.port=8073
bondms.url=http://localhost:8071/
counterpartyms.url=http://localhost:8072/
```

## Running

This service depends on the Bond Service (8071) and the Counterparty Service (8072)
being up. Start all three, then:

```bash
./mvnw spring-boot:run        # starts on http://localhost:8073

curl -X POST http://localhost:8073/rfqs \
  -H 'Content-Type: application/json' \
  -d '{"counterpartyId":1,"bondId":1,"notionalAmount":1000000.00,"side":"BUY","executionPrice":998750.00}'
```

## Build & test

```bash
./mvnw clean test       # run tests
./mvnw clean package    # build the jar
```
