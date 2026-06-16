# Extracting the Counterparty Credit bounded context (Strangler Fig — step 1)

This document describes the first strangler-fig step that carves the
**Counterparty Credit** bounded context out of the RFQ trading monolith into a
cleanly separated Spring module (`counterparty-credit-service`), without any
behaviour change to RFQ pricing or execution.

## Why Counterparty Credit first

It is the most self-contained context in the platform:

- A single aggregate (`Counterparty`) backed by a single table (`counterparties`).
- A small, well-defined set of operations (credit limit / available credit
  mutation, LEI resolution, CRUD).
- The only place the rest of the system touches it is the execution saga
  (credit deduction) and the counterparty REST controller — both easy to route
  through one seam.

## The seam

```
            ┌──────────────────────────── monolith (splitthemonolith) ───────────────────────────┐
            │  CounterpartyController        RFQExecutionSaga        SplitTheMonolithApplication   │
            │           │                          │                          │                    │
            │           └──────────────┬───────────┴──────────────┬──────────-┘                    │
            │                          ▼                          ▼                                 │
            │                 CounterpartyCreditService  (interface = the seam)                     │
            └──────────────────────────┬───────────────────────────────────────────────-──────────┘
                                        ▼
        ┌──────────────────── counterparty-credit-service module ───────────────────────┐
        │  InProcessCounterpartyCreditService  ──►  CounterpartyRepository  ──►  H2       │
        │  domain.Counterparty   exception.{InsufficientCredit, CounterpartyNotFound}     │
        └────────────────────────────────────────────────────────────────────────────────┘
```

`CounterpartyCreditService` is the contract. Every monolith caller depends only
on this interface, never on the implementation, repository, or entity internals.

- **Today (step 1):** the interface is satisfied by `InProcessCounterpartyCreditService`,
  an in-process Spring bean that shares the monolith's datasource and
  transaction. No network hop, no behaviour change.
- **Later (step N):** swap the single `@Service` implementation for a
  network-backed adapter (`RestCounterpartyCreditServiceClient` / gRPC) talking
  to a standalone deployment of this module. **No monolith caller changes.**

### Operations on the seam

| Method | Caller(s) | Notes |
|---|---|---|
| `findAll()` / `findById(id)` | controller, saga | read counterparty state |
| `findByLei(lei)` | (new) LEI resolution | resolve a counterparty by its Legal Entity Identifier |
| `create` / `update` / `delete` | controller | CRUD |
| `addCredit(id, amount)` | controller PATCH `ADD` | increase available credit (+ trade confirmation, owned by monolith) |
| `deductCredit(id, amount)` | controller PATCH `DEDUCT`, **saga** | reserve/consume credit during execution; throws `InsufficientCreditException` |

## Data ownership boundary

| Table / entity | Owner | Notes |
|---|---|---|
| `counterparties` (`Counterparty`) | **counterparty-credit-service** | credit limit, available credit, LEI. Only this context reads/writes it. |
| `bonds` (`Bond`) | monolith (inventory) | unchanged |
| `rfqs` (`Rfq`) | monolith (trading) | unchanged |

`rfqs.counterparty_id` is the **only** cross-boundary reference. Today it is a
real JPA `@ManyToOne` foreign key because both contexts still share one schema.

### Future DB-split seam

When `counterparties` moves to its own database:

1. Drop the `rfqs.counterparty_id` **foreign key constraint**; keep
   `counterparty_id` as a plain `long` soft-reference column on `Rfq` (replace
   the `@ManyToOne Counterparty` with a `long counterpartyId`).
2. The monolith no longer JPA-joins to the counterparty; it resolves display
   data via `CounterpartyCreditService` (`findById`) when needed.
3. `InProcessCounterpartyCreditService` is replaced by a remote adapter behind
   the same interface.

## Transactional integrity & eventual-consistency tradeoffs

- **Now:** `RFQExecutionSaga.executeRfq` is `@Transactional`. The in-process
  `deductCredit` runs with propagation `REQUIRED`, so the bond-notional update
  and the credit deduction commit/roll back **atomically** in one local
  transaction — identical behaviour to before extraction. Insufficient notional
  or insufficient credit rolls the whole RFQ back; no compensation needed.

- **After the DB split:** credit will live in a separate datastore, so a single
  local ACID transaction across bond + credit is no longer possible. The saga
  becomes a true distributed saga with **compensating actions**:

  1. `deductNotional` (bond, local commit)
  2. `deductCredit` (remote call to the credit service)
  3. if step 2 fails → **compensate** step 1 (`addNotional`) and reject the RFQ.

  This introduces a brief window of eventual consistency: between steps 1 and 2
  the bond shows reduced notional while credit is not yet reserved. Mitigations:
  reserve-then-confirm (two-phase) on the credit side, idempotency keys on
  `deductCredit`, and persisting saga state so a crashed saga can resume or
  compensate. These are explicitly **out of scope** for step 1 and only become
  necessary once the database is physically split.

## Recommended next service to extract

**Bond Inventory** (`bonds` + notional add/deduct). It is the second
independent aggregate touched by the execution saga, mirrors the credit context
almost exactly (single table, add/deduct semantics), and once both Credit and
Inventory sit behind service seams the `RFQExecutionSaga` reduces to a pure
orchestrator over two services — the natural point to introduce the
compensating-saga machinery described above.
