# Fixed-Income RFQ Trading Platform

A SpringBoot monolith simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Strangler Extraction Seam: Trade Confirmation](#strangler-extraction-seam-trade-confirmation)
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

A trade confirmation is sent to a counterparty whenever credit is added. That capability sits behind the `TradeConfirmationPort` interface — see [Strangler Extraction Seam: Trade Confirmation](#strangler-extraction-seam-trade-confirmation).

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

## Strangler Extraction Seam: Trade Confirmation

Trade confirmation is the first capability being strangled out of this monolith. All confirmation logic sits behind one outbound port, so the monolith has exactly one coupling point to the capability.

```
CounterpartyController ──> TradeConfirmationPort (interface)
                                    │
                           TradeConfirmationDispatcher   @Primary, owns the toggle
                                    ├── use.confirmation.service=false ──> InProcessTradeConfirmationAdapter
                                    └── use.confirmation.service=true  ──> RemoteTradeConfirmationAdapter ──HTTP──> confirmation microservice
```

| Component | Role |
|-----------|------|
| `confirmation/TradeConfirmationPort` | The seam. The only confirmation type the rest of the monolith may reference |
| `confirmation/TradeConfirmationDispatcher` | Routes on `use.confirmation.service`; the toggle exists nowhere else |
| `confirmation/InProcessTradeConfirmationAdapter` | Legacy in-process implementation (logs the confirmation) |
| `confirmation/RemoteTradeConfirmationAdapter` | Calls the extracted service at `${confirmationms.url}confirmations/` |

Rules for keeping the seam clean: call sites depend on `TradeConfirmationPort` only, never on an adapter or on the toggle; `TradeConfirmationDto` is the contract carried across the seam — treat changes to it as changes to a published API.

### Next steps

1. **Widen the port to the whole capability.** Confirmations are currently emitted only from the credit-`ADD` path of `PATCH /counterparties/{id}`. Route RFQ execution confirmations through the same port so the seam covers the real business event rather than one controller branch.
2. **Enrich the contract.** `TradeConfirmationDto` carries only counterparty name and amount. An extracted service needs stable identifiers (counterparty LEI, bond ISIN, RFQ id, trade timestamp) — add them behind the port while both adapters still exist.
3. **Make the remote path safe to enable.** Today the remote call is synchronous and inside the request path, so a confirmation-service outage fails the credit update (pinned by `RemoteTradeConfirmationCharacterizationTest`). Before flipping the toggle in production, decide on timeouts, retries, and whether confirmation should become an after-commit/asynchronous concern — then update the characterization tests deliberately, as an intentional behavior change.
4. **Run both adapters side by side.** Add a dual-write/comparison implementation of the port that calls in-process and remote and reports divergences; this validates the extracted service against live traffic with no user impact.
5. **Flip and delete.** Once the remote adapter is trusted, default `use.confirmation.service=true`, then delete the dispatcher, the in-process adapter, and the toggle. The port stays as the anti-corruption layer.
6. **Repeat for the next capability.** `RFQExecutionSaga` is the next candidate: extract credit-limit enforcement behind a port the same way, keeping `RfqExecutionSagaCharacterizationTest` green at every step.

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

### Characterization suite

`src/test/java/.../characterization/` holds a golden-master suite that pins the *current* end-to-end behavior — quirks included — so migration steps can be proven behavior preserving. It exercises the application through the HTTP boundary only, so it survives internal refactorings:

| Test | Pins |
|------|------|
| `RfqExecutionSagaCharacterizationTest` | `POST /rfqs` writes `EXECUTED` in one step (a client-supplied `PENDING`/`QUOTED` status is ignored and `REJECTED` is never persisted); notional is deducted from the bond and the *execution price* from the counterparty credit; `SELL` behaves exactly like `BUY`; credit/notional limits are inclusive; a failure rolls back the whole saga and returns `400`; unknown ids return `404` |
| `InProcessTradeConfirmationCharacterizationTest` | `use.confirmation.service=false`: confirmations are logged in-process and no HTTP call is made; `DEDUCT`, insufficient credit, and unknown operations send no confirmation |
| `RemoteTradeConfirmationCharacterizationTest` | `use.confirmation.service=true`: confirmations are `POST`ed to `${confirmationms.url}confirmations/` as JSON, and a failing call propagates so the credit update is not persisted |

Both toggle branches are covered in one `./mvnw test` run: each test class boots its own Spring context with its own toggle value.
