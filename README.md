# Fixed-Income RFQ Trading Platform

A SpringBoot monolith simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Authentication and Authorization](#authentication-and-authorization)
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

## Authentication and Authorization

Every endpoint requires HTTP Basic authentication (Spring Security); unauthenticated requests get `401`.
Two roles exist:

- **OPERATOR** (platform staff): full access to every endpoint, including creating/updating/deleting counterparties
  and bonds, adjusting credit/notional (PUT/PATCH), executing RFQs on behalf of any counterparty and deleting RFQs.
- **COUNTERPARTY** (a trader bound to one counterparty id): may read bonds, read its own counterparty
  (`GET /counterparties/{ownId}`), list/read its own RFQs and execute RFQs (`POST /rfqs`). The counterparty is taken
  from the authenticated user; a body `counterpartyId` that differs from it is rejected with `403`. All other
  endpoints return `403`.

`POST /rfqs` also rejects (`400`) non-positive notionals and execution prices outside
`rfq.pricing.min-price-ratio`..`rfq.pricing.max-price-ratio` of the notional (default 0.50..1.50).

No users are committed to the repo. Provision them through configuration, e.g. environment variables
(passwords use Spring Security's delegating format; prefer `{bcrypt}` hashes):

```bash
export RFQ_SECURITY_USERS_0_USERNAME=ops
export RFQ_SECURITY_USERS_0_PASSWORD='{bcrypt}<hash>'
export RFQ_SECURITY_USERS_0_ROLE=OPERATOR
export RFQ_SECURITY_USERS_1_USERNAME=acme
export RFQ_SECURITY_USERS_1_PASSWORD='{bcrypt}<hash>'
export RFQ_SECURITY_USERS_1_ROLE=COUNTERPARTY
export RFQ_SECURITY_USERS_1_COUNTERPARTYID=1
```

If no users are configured the application still starts, but every request is rejected with `401`.

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

The application starts on port `8080`. After provisioning users (see above), hit `/counterparties`, `/bonds`, and `/rfqs`
with credentials, e.g. `curl -u ops:<password> localhost:8080/bonds`.

## Testing

```bash
cd monolith
./mvnw clean test
```

See `IntegrationTest.java` for the full set of use-cases covering RFQ execution, insufficient notional/credit, and missing counterparty/bond scenarios.
