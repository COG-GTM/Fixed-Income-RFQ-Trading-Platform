# Microservices Decomposition Strategy — Fixed-Income RFQ Trading Platform

> **Status:** Planning document only. No services are extracted by this document.
> Each phase below is scoped to be executable as a **single follow-up Devin session**
> with explicit success criteria.

This document analyzes the `split-the-monolith` Spring Boot monolith (a fixed-income
Request-for-Quote trading platform: Java 11, Spring Data JPA, H2, Maven, under
`monolith/`) and proposes a **strangler-fig** decomposition into microservices.

All coupling claims are grounded in `file:line` evidence relative to the repo root.

---

## 1. Codebase Structure & Domain Map

The application is a single Maven module (`monolith/pom.xml`) with one Spring Boot
entrypoint (`monolith/src/main/java/com/javieraviles/splitthemonolith/SplitTheMonolithApplication.java`).
All classes share one package tree and one H2 in-memory database (no datasource is
configured in `monolith/src/main/resources/application.properties`, so Spring Boot
auto-configures a single shared H2 schema for all entities).

### 1.1 Identified business domains

| # | Domain | Responsibility | Owning classes |
|---|--------|----------------|----------------|
| A | **Counterparty Management** | Trading entities (LEI), credit limits & available credit | `entity/Counterparty.java`, `controller/CounterpartyController.java`, `repository/CounterpartyRepository.java`, `exception/InsufficientCreditException.java` |
| B | **Bond Inventory** | Fixed-income instruments (ISIN), available notional | `entity/Bond.java`, `controller/BondController.java`, `repository/BondRepository.java`, `exception/InsufficientNotionalException.java` |
| C | **RFQ Trading / Execution** | Request-for-Quote lifecycle, atomic trade execution | `entity/Rfq.java`, `entity/RfqStatus.java`, `entity/Side.java`, `controller/RfqController.java`, `repository/RfqRepository.java`, `saga/RFQExecutionSaga.java`, `dto/RfqDto.java` |
| D | **Trade Confirmation** | Notify counterparty when credit is added | `service/TradeConfirmationService.java`, `restclient/TradeConfirmationMicroserviceClient.java`, `dto/TradeConfirmationDto.java` |

Shared/cross-cutting: `dto/OperationEnum.java` (used by both Bond and Counterparty
PATCH endpoints), `exception/ResourceNotFoundException.java` (used everywhere).

### 1.2 Domain interaction diagram (as-is)

```
        HTTP POST /rfqs
             |
             v
      RfqController  --->  RFQExecutionSaga  @Transactional (single DB tx)
                                |  \
                                |   \--- bond.deductNotional()        (Domain B)
                                |   \--- counterparty.deductCredit()  (Domain A)
                                |   \--- rfqRepository.save(Rfq)      (Domain C)
                                v
                          shared H2 schema
                    (rfqs FK-> counterparties, bonds)

   HTTP PATCH /counterparties/{id} (ADD credit)
             |
             v
   CounterpartyController --(feature flag use.confirmation.service)-->
        false: TradeConfirmationService.sendTradeConfirmation()   (in-process, Domain D)
        true : TradeConfirmationMicroserviceClient.sendConfirmation() (HTTP, Domain D)
```

---

## 2. Coupling Analysis (the seams)

### 2.1 Shared domain model via JPA associations (Domain C → A, B)

`Rfq` holds hard `@ManyToOne` object references (not IDs) to both other aggregates:

- `Rfq.counterparty` — `@ManyToOne(fetch = FetchType.EAGER)` with
  `@JoinColumn(name = "counterparty_id")` at `monolith/src/main/java/com/javieraviles/splitthemonolith/entity/Rfq.java:27-30`.
- `Rfq.bond` — `@ManyToOne(fetch = FetchType.EAGER)` with
  `@JoinColumn(name = "bond_id")` at `entity/Rfq.java:32-35`.

These create **foreign-key constraints** (`rfqs.counterparty_id → counterparties.id`,
`rfqs.bond_id → bonds.id`) inside a single shared schema. EAGER fetch means every RFQ
read joins across all three tables.

`RfqController.toDto()` traverses these associations to read foreign identities:
`rfq.getCounterparty().getId()` and `rfq.getBond().getId()` at
`controller/RfqController.java:54`.

### 2.2 Cross-domain distributed transaction (the primary seam)

`RFQExecutionSaga` is the single strongest coupling point. It:

- autowires all three repositories (`CounterpartyRepository`, `BondRepository`,
  `RfqRepository`) at `saga/RFQExecutionSaga.java:20-27`;
- mutates Bond state — `bond.deductNotional(...)` at `saga/RFQExecutionSaga.java:37`;
- mutates Counterparty state — `counterparty.deductCredit(...)` at `saga/RFQExecutionSaga.java:43`;
- persists the Rfq — `rfqRepository.save(...)` at `saga/RFQExecutionSaga.java:45-46`;
- wraps all of the above in one local ACID transaction via `@Transactional` at
  `saga/RFQExecutionSaga.java:29` (the comment at lines 38-42 explicitly relies on
  the single-transaction rollback semantics: *"No need for saga compensation..."*).

Despite the name "Saga", this is a **local database transaction**, not a distributed
saga. Splitting Bond and Counterparty into separate services turns this into a genuine
distributed workflow that needs a real saga (reservation + compensation) or an
outbox/event approach. This is the crux of the decomposition and is deliberately
sequenced **last** (see Phase 4).

### 2.3 Business invariants living inside entities (Domain A, B)

Domain rules are enforced inside the entity mutators, which the saga depends on:

- `Bond.deductNotional` throws `InsufficientNotionalException` when notional is
  insufficient — `entity/Bond.java:52-57`.
- `Counterparty.deductCredit` throws `InsufficientCreditException` when credit is
  insufficient — `entity/Counterparty.java:58-63`.
- Both exceptions map to HTTP 400 via `@ResponseStatus(HttpStatus.BAD_REQUEST, ...)`
  at `exception/InsufficientNotionalException.java:6` and
  `exception/InsufficientCreditException.java:6`.

When Bond/Counterparty become services, these invariants must be enforced **behind
their own APIs** (e.g. a "reserve notional" / "reserve credit" endpoint), not by an
external caller mutating shared objects.

### 2.4 Trade Confirmation seam — already strangler-ready (Domain D)

The Trade Confirmation capability already has a **feature-flagged seam** — the pattern
we want everywhere:

- Feature flag `use.confirmation.service` injected at
  `controller/CounterpartyController.java:33-34`, defaulted to `false` at
  `monolith/src/main/resources/application.properties:3`.
- The branch that toggles in-process vs. remote implementation is at
  `controller/CounterpartyController.java:83-87`:
  - flag `false` → in-process `TradeConfirmationService.sendTradeConfirmation(...)`
    (`service/TradeConfirmationService.java:14-17`, just logs);
  - flag `true` → HTTP call `TradeConfirmationMicroserviceClient.sendConfirmation(...)`
    (`restclient/TradeConfirmationMicroserviceClient.java:23-26`).
- The remote client already POSTs `TradeConfirmationDto` to
  `${confirmationms.url}confirmations/` (`restclient/TradeConfirmationMicroserviceClient.java:25`,
  URL configured at `application.properties:4`).
- `RestTemplate` for the client is already wired as a bean in
  `SplitTheMonolithApplication.java:54-64`.

Domain D touches **no database** and shares only one DTO (`dto/TradeConfirmationDto.java`),
constructed at `controller/CounterpartyController.java:82`. This makes it the
lowest-coupling, highest-signal first extraction.

### 2.5 Bootstrap coupling (seed data)

`SplitTheMonolithApplication.run(...)` seeds one Counterparty, one Bond, and one
executed Rfq that references both — `SplitTheMonolithApplication.java:42-52`. Any
data-ownership split must relocate/replace this cross-domain seeding.

### 2.6 Shared minor types

- `OperationEnum` (`dto/OperationEnum.java`) is consumed by both
  `BondController.java:67` and `CounterpartyController.java:79`.
- `ResourceNotFoundException` is thrown across all controllers and the saga.

These are trivial to duplicate per-service (they carry no behavior) and are not a
blocking seam.

### 2.7 Coupling summary table

| From → To | Type | Strength | Evidence |
|-----------|------|----------|----------|
| RFQ → Counterparty | JPA `@ManyToOne` + FK (`counterparty_id`) | **High** | `entity/Rfq.java:27-30` |
| RFQ → Bond | JPA `@ManyToOne` + FK (`bond_id`) | **High** | `entity/Rfq.java:32-35` |
| RFQ → Counterparty + Bond | distributed write in one local tx | **High** | `saga/RFQExecutionSaga.java:29,37,43,45` |
| RFQ → Counterparty/Bond | association traversal for IDs | Medium | `controller/RfqController.java:54` |
| RFQ/A/B | single shared H2 schema (no per-domain datasource) | **High** | `application.properties:1-4` (no datasource defined) |
| Counterparty → Trade Confirmation | feature-flagged call (in-proc / HTTP) | **Low** (seam exists) | `controller/CounterpartyController.java:83-87` |
| Trade Confirmation → (shared) | shares only `TradeConfirmationDto` | **Low** | `controller/CounterpartyController.java:82`, `dto/TradeConfirmationDto.java` |
| All controllers/saga | shared `ResourceNotFoundException`, `OperationEnum` | Trivial | `BondController.java:67`, `CounterpartyController.java:79` |
| Bootstrap | seeds all 3 domains together | Medium | `SplitTheMonolithApplication.java:42-52` |

### 2.8 Prioritized module (extraction-candidate) list

Ordered by *(low coupling × high demonstrative value)* first:

1. **Trade Confirmation Service** — extract first. Seam + HTTP client + flag already
   exist; no DB; only one DTO shared. (Phase 1)
2. **Bond Inventory Service** — self-contained aggregate + repository; the only inbound
   coupling is the RFQ execution write and the RFQ→Bond FK. (Phase 2)
3. **Counterparty (Credit) Service** — symmetric to Bond; also the source of the Trade
   Confirmation trigger. (Phase 3)
4. **RFQ / Trade Execution Service** — extract last. Owns the cross-service workflow;
   requires converting the in-process `@Transactional` into a real saga + anti-corruption
   layer against Bond & Counterparty. (Phase 4)

---

## 3. Decomposition Strategy (strangler-fig, phased)

Guiding principles:

- **Strangler-fig:** route capabilities to new services behind toggles/facades while
  the monolith keeps serving everything until each seam is proven, then remove the old
  path.
- **Database-per-service** is the target; get there via *shared-DB → separate schema →
  separate database*, never a big-bang data migration.
- **Anti-corruption layer (ACL):** the monolith talks to each new service through a
  thin client interface (mirroring the existing `TradeConfirmationMicroserviceClient`
  pattern) so extraction is a config flip, not a rewrite.
- **Reliability for cross-service writes:** use the **transactional outbox** pattern +
  a saga with explicit reserve/commit/compensate steps once RFQ execution spans services.
- Each phase is independently shippable, reversible via its flag, and small enough for
  one follow-up Devin session.

---

### Phase 0 — Enabling work (no extraction)

**Goal:** make the monolith safe to decompose. Establish tests, CI, and internal module
boundaries so later phases have a green baseline and clear seams.

**Scope / tasks:**
1. **CI pipeline.** Add a GitHub Actions workflow (none exists today — no `.github/`)
   that runs `cd monolith && ./mvnw clean test` on push/PR against Java 11. This gives
   every later phase an automated gate.
2. **Characterization tests.** The current suite (`monolith/src/test/.../IntegrationTest.java`)
   already covers RFQ execution, insufficient notional/credit, and missing entity paths.
   Add tests that pin the **Trade Confirmation seam behavior** for both flag states
   (`use.confirmation.service` true/false) so Phase 1 can refactor safely.
3. **Introduce service-layer interfaces (in-process ACL).** Define package-level
   interfaces the controllers depend on rather than concrete classes/repositories —
   e.g. a `ConfirmationPort` implemented by `TradeConfirmationService` today. This makes
   swapping in a remote implementation a wiring change.
4. **Package-by-domain boundaries.** Reorganize/annotate packages so each domain
   (`counterparty`, `bond`, `rfq`, `confirmation`) is a clear internal module. Optionally
   add ArchUnit rules asserting no cross-domain access except through defined ports.
5. **Externalize configuration.** Confirm all seam config (`use.confirmation.service`,
   `confirmationms.url`) is env-overridable; document required env vars.

**Success criteria:**
- `./mvnw clean test` green in CI on every PR.
- New tests assert both branches of `CounterpartyController.java:83-87`.
- Controllers depend on domain interfaces, not repositories/concrete services, for any
  cross-domain call.
- No behavioral change to any REST endpoint (verified by existing integration tests).

---

### Phase 1 — Extract **Trade Confirmation Service** (first extraction)

**Why first:** lowest coupling, seam already built (Section 2.4). No shared database.
Failure is non-critical (confirmations are notifications, not the trade itself).

**Scope:**
- Stand up a standalone `confirmation-service` exposing the contract the existing client
  already assumes.
- The monolith keeps calling it through `TradeConfirmationMicroserviceClient`
  (`restclient/TradeConfirmationMicroserviceClient.java`), toggled by
  `use.confirmation.service`. Extraction becomes flipping the flag to `true` and pointing
  `confirmationms.url` at the deployed service.
- Wrap the outbound call in the ACL/port from Phase 0 so the monolith is insulated from
  the remote schema; add timeout + fallback (log locally on failure) so a confirmation
  outage cannot break the credit-add path (`CounterpartyController.java:80-91`).

**API contract exposed by `confirmation-service`:**

```
POST /confirmations/
Content-Type: application/json
Request body (matches dto/TradeConfirmationDto.java):
  { "counterpartyName": "string", "creditAmount": number(>0) }
Response: 200/201 on accepted; 4xx on validation failure.
```

(This deliberately matches `TradeConfirmationMicroserviceClient.java:25` and
`dto/TradeConfirmationDto.java` so no monolith code changes shape.)

**Data ownership:** none required — Domain D is stateless (it only logs today,
`service/TradeConfirmationService.java:14-17`). If confirmation history is later needed,
it is owned solely by the new service.

**Keeping the monolith working:**
- Default `use.confirmation.service=false` until the service is deployed and verified.
- Flip to `true` per-environment; the in-process `TradeConfirmationService` remains as
  the fallback path and for local/dev.

**Success criteria:**
- With flag `true`, adding credit produces a confirmation via the remote service; with
  flag `false`, behavior is unchanged.
- Confirmation-service downtime does **not** fail `PATCH /counterparties/{id}` credit
  adds (graceful degradation verified by test).
- Monolith Java changes are limited to the ACL/fallback; the REST contract of the
  monolith is unchanged.

---

### Phase 2 — Extract **Bond Inventory Service**

**Why second:** self-contained aggregate (`entity/Bond.java`, `BondController`,
`BondRepository`). Its only inbound coupling is the RFQ execution write
(`saga/RFQExecutionSaga.java:37`) and the `rfqs.bond_id` FK (`entity/Rfq.java:32-35`).

**Scope:**
- Stand up `bond-service` owning bond CRUD + inventory. Move the notional invariant
  (`Bond.deductNotional`, `entity/Bond.java:52-57`) behind its API.
- In the monolith, replace direct `BondRepository`/entity usage in RFQ execution with a
  `BondClient` ACL. Route `/bonds*` traffic to the new service via a facade/gateway;
  keep the monolith's `BondController` as a temporary pass-through until traffic is cut
  over.
- **Data:** first give bonds their own schema in the shared DB, then a separate DB.
  Introduce a **reserve/commit** endpoint so RFQ execution no longer mutates a shared
  object directly (prepares Phase 4).

**API contract exposed by `bond-service`:**

```
GET    /bonds                list bonds
POST   /bonds                create bond
GET    /bonds/{id}           get bond
PUT    /bonds/{id}           update bond
PATCH  /bonds/{id}           add/deduct notional { amount, operation: ADD|DEDUCT }
DELETE /bonds/{id}           delete bond
# new, for cross-service execution:
POST   /bonds/{id}/notional-reservations   { amount }  -> 200 reservationId | 409 insufficient
POST   /bonds/{id}/notional-reservations/{rid}/commit
DELETE /bonds/{id}/notional-reservations/{rid}          (compensation / release)
```

(The first six mirror `controller/BondController.java` exactly; the reservation
endpoints replace the in-process `bond.deductNotional` call.)

**Data ownership:** `bonds` table moves to `bond-service`. The `rfqs.bond_id` FK is
**dropped** and replaced by storing the bond id as a plain value on the RFQ; referential
integrity becomes an application concern (validated via the ACL).

**Keeping the monolith working:**
- Cut over reads first (`GET /bonds*`) behind a flag; then writes; then remove the
  monolith's bond code.
- Until Phase 4, RFQ execution can call the reservation API but still finalize within the
  monolith, or continue using the local path behind a flag.

**Success criteria:**
- All bond endpoints served by `bond-service` with identical request/response shapes
  (contract tests pass).
- `bonds` no longer read/written by the monolith except through `BondClient`.
- Notional invariant enforced only inside `bond-service`.

---

### Phase 3 — Extract **Counterparty (Credit) Service**

**Why third:** symmetric to Bond (`entity/Counterparty.java`, `CounterpartyController`,
`CounterpartyRepository`). Also the origin of the Trade Confirmation trigger
(`CounterpartyController.java:80-91`), so extracting it consolidates Domain A + the
Phase 1 integration.

**Scope:**
- Stand up `counterparty-service` owning counterparty CRUD + credit. Move the credit
  invariant (`Counterparty.deductCredit`, `entity/Counterparty.java:58-63`) behind its
  API, and move the "on credit add → send confirmation" behavior so the credit service
  calls the Phase 1 `confirmation-service` directly.
- Replace monolith `CounterpartyRepository`/entity usage in RFQ execution with a
  `CounterpartyClient` ACL; add reserve/commit/compensate for credit.

**API contract exposed by `counterparty-service`:**

```
GET    /counterparties               list
POST   /counterparties               create
GET    /counterparties/{id}          get
PUT    /counterparties/{id}          update
PATCH  /counterparties/{id}          add/deduct credit { amount, operation: ADD|DEDUCT }
                                     (ADD triggers confirmation-service internally)
DELETE /counterparties/{id}          delete
# new, for cross-service execution:
POST   /counterparties/{id}/credit-reservations   { amount } -> 200 rid | 409 insufficient
POST   /counterparties/{id}/credit-reservations/{rid}/commit
DELETE /counterparties/{id}/credit-reservations/{rid}         (compensation / release)
```

**Data ownership:** `counterparties` table moves to `counterparty-service`; `rfqs.counterparty_id`
FK dropped and stored as a plain value on the RFQ. The confirmation trigger
(`CounterpartyController.java:82-87`) becomes an internal call from this service to
`confirmation-service`, removing that seam from the monolith.

**Keeping the monolith working:** same read→write→remove cutover, flag-gated, as Phase 2.

**Success criteria:**
- All counterparty endpoints served by `counterparty-service` with identical shapes.
- Credit-add confirmations flow `counterparty-service → confirmation-service`.
- Monolith no longer references `Counterparty`/`CounterpartyRepository` except via
  `CounterpartyClient`.

---

### Phase 4 — Extract **RFQ / Trade Execution Service** & convert the local transaction to a saga

**Why last:** RFQ execution is the cross-service workflow. Once Bond and Counterparty
own their data, the current single `@Transactional` block
(`saga/RFQExecutionSaga.java:29-47`) can no longer be one ACID transaction and must
become a genuine distributed saga.

**Scope:**
- Stand up `rfq-service` owning `rfqs` (id + `counterparty_id`/`bond_id` as plain values,
  no FK). Implement `RFQExecutionSaga` as an **orchestrated saga**:
  1. reserve notional (`bond-service`), 2. reserve credit (`counterparty-service`),
  3. persist RFQ as `EXECUTED`, 4. commit both reservations. On any failure,
  **compensate** already-made reservations (release them). This replaces the
  rollback-based comment reasoning at `saga/RFQExecutionSaga.java:38-42`.
- Use a **transactional outbox** in `rfq-service` for reliable event emission
  (`RfqExecuted`, reservation commands) so no dual-write inconsistency occurs.
- Keep the monolith's `RfqController` (`controller/RfqController.java`) as a facade that
  forwards to `rfq-service` until traffic is fully cut over, then delete the monolith's
  RFQ code and the now-empty monolith.

**API contract exposed by `rfq-service`:**

```
GET    /rfqs         list RFQs (RfqDto shape from dto/RfqDto.java)
POST   /rfqs         execute an RFQ (runs the saga) -> 201 EXECUTED | 400 insufficient | 404 missing
GET    /rfqs/{id}    get one
DELETE /rfqs/{id}    delete
```

(Mirrors `controller/RfqController.java` + `dto/RfqDto.java`. `POST /rfqs` internally
orchestrates the reserve/commit/compensate calls from Phases 2–3.)

**Data ownership:** `rfqs` table owned by `rfq-service`; it stores counterparty/bond
identifiers only (no cross-service FK). Bond notional and counterparty credit remain
owned by their respective services and are only changed via their reservation APIs.

**Keeping the monolith working during transition:**
- Introduce the saga behind a flag while the local `@Transactional` path still exists;
  run both in shadow/parallel to compare results before cutover.
- Move the seed data (`SplitTheMonolithApplication.java:42-52`) into per-service
  bootstrap/migrations.

**Success criteria:**
- `POST /rfqs` produces the same outcomes as today (EXECUTED / 400 insufficient /
  404 missing) — verified against the existing scenarios in `IntegrationTest.java`.
- A failure after the first reservation triggers compensation (no orphaned reservation),
  verified by a fault-injection test.
- No shared database remains; each service owns its schema; the monolith module is
  retired.

---

## 4. Cross-Phase Concerns

- **Anti-corruption layers:** every monolith→service call goes through a client
  interface modeled on the existing `TradeConfirmationMicroserviceClient`
  (`restclient/TradeConfirmationMicroserviceClient.java`). New service internals never
  leak into the monolith.
- **Feature flags:** each extraction is gated by a boolean like the existing
  `use.confirmation.service` (`application.properties:3`) so every phase is reversible.
- **Data split ladder:** shared H2 schema → per-domain schema → per-service database.
  FKs (`rfqs.counterparty_id`, `rfqs.bond_id`) are removed only when the referenced
  domain is extracted.
- **Consistency:** cross-service writes (Phase 4) use saga + outbox; notifications
  (Phase 1) are fire-and-forget with graceful degradation.
- **Observability:** add correlation IDs across service calls before Phase 4 so
  distributed saga steps are traceable.

## 5. Sequencing Summary

| Phase | Extract | Coupling removed | Reversible via | One-session? |
|-------|---------|------------------|----------------|--------------|
| 0 | (none) | — establishes CI, tests, ports, boundaries | n/a | ✅ |
| 1 | Trade Confirmation | `CounterpartyController` → in-proc confirmation | `use.confirmation.service` | ✅ |
| 2 | Bond Inventory | `rfqs.bond_id` FK, `bond.deductNotional` in saga | `bond-service` flag | ✅ |
| 3 | Counterparty/Credit | `rfqs.counterparty_id` FK, `counterparty.deductCredit` in saga, confirmation trigger | `counterparty-service` flag | ✅ |
| 4 | RFQ / Execution | local `@Transactional` → distributed saga; shared DB retired | saga flag / shadow run | ✅ |
