# Monolith → Microservice Migration Checklist

Repeatable checklist for migrating a module from **direct method invocation (v1)** to a **REST client interface (v2)** using the Strangler Fig pattern.

The TradeConfirmationService migration (completed) serves as the reference implementation.

---

## Phase 1 — Analysis

- [ ] Identify the monolith service class to migrate (e.g. `XxxService`).
- [ ] Grep all call sites across controllers, sagas, and other services.
- [ ] Document the method signatures and data types exchanged.
- [ ] Note any transactional boundaries the service participates in.
- [ ] Check for existing feature flags (e.g. `use.xxx.service`).

## Phase 2 — Interface Extraction

- [ ] Create a `client/` package (or use the existing one).
- [ ] Define `XxxClient` interface with the same method contracts.
- [ ] Implement `RestXxxClient` — REST call to the target microservice.
- [ ] Implement `FallbackXxxClient` — local logging / degraded behaviour.
- [ ] Implement `ResilientXxxClient` (`@Primary`) that:
  - Respects the feature toggle (`use.xxx.service`).
  - Tries the REST client first when the toggle is **on**.
  - Falls back to the local implementation on any exception.
  - Goes straight to fallback when the toggle is **off**.

## Phase 3 — Wiring

- [ ] Replace all injected references to the old service with the new interface:
  - Controllers
  - Sagas / orchestrators
  - Other services
- [ ] Remove the branching `if (useXxxService)` logic from callers — the `ResilientXxxClient` now owns that decision.
- [ ] Mark the old service class and any old REST client as `@Deprecated`.

## Phase 4 — Configuration

- [ ] Ensure `application.properties` contains:
  - `use.xxx.service=false` (safe default — local fallback).
  - `xxxms.url=http://localhost:<port>/` (microservice base URL).
- [ ] Verify the `RestTemplate` bean (or `WebClient`) is available.

## Phase 5 — Testing

- [ ] **Unit / integration tests** for the resilient client:
  - Toggle **on** + REST available → REST path used.
  - Toggle **on** + REST unavailable → fallback path used.
  - Toggle **off** → fallback path used, REST never called.
- [ ] Re-run existing integration tests to confirm no regressions.
- [ ] If the module participates in a saga, verify the confirmation is sent after the transaction commits.

## Phase 6 — Rollout

- [ ] Deploy with `use.xxx.service=false` (v1 behaviour).
- [ ] Deploy the target microservice.
- [ ] Flip toggle to `true` to activate REST path.
- [ ] Monitor logs for `FALLBACK` warnings.
- [ ] Once stable, remove the deprecated v1 classes and the toggle.

---

## Assumptions (TradeConfirmationService reference migration)

| # | Assumption | Rationale |
|---|-----------|-----------|
| 1 | Trade confirmation is a **fire-and-forget** side-effect. | A REST failure must not roll back the trade transaction. The fallback logs the event for later retry / audit. |
| 2 | The feature toggle `use.confirmation.service` is read at runtime. | Allows operators to flip between v1 and v2 without redeployment (supports Spring Cloud Config / environment variable override). |
| 3 | The `ResilientTradeConfirmationClient` is `@Primary`. | Spring injects it wherever `TradeConfirmationClient` is requested, so callers do not need to know about the toggle or fallback logic. |
| 4 | `RFQExecutionSaga.executeRfq` sends a confirmation after persisting the RFQ. | Confirmation was previously missing from the saga; adding it aligns with the counterparty credit-update flow in `CounterpartyController`. |
| 5 | Old classes (`TradeConfirmationService`, `TradeConfirmationMicroserviceClient`) are retained but deprecated. | Enables side-by-side comparison and a safe rollback path during the transition period. |
| 6 | The confirmation microservice endpoint is `POST {base}/confirmations/`. | Matches the existing `TradeConfirmationMicroserviceClient` contract. |

---

## Remaining Modules to Migrate

| Module | Current state | Notes |
|--------|--------------|-------|
| **TradeConfirmationService** | **Done** | Reference implementation in this PR. |
| BondRepository / Bond logic | v1 (monolith) | Candidate for inventory microservice. |
| CounterpartyRepository / Credit logic | v1 (monolith) | Candidate for counterparty-credit microservice. |
| RFQExecutionSaga orchestration | v1 (monolith) | May move to an event-driven choreography once services are extracted. |
