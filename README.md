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

| Method | Path                   | Description                                      | Allowed roles |
|--------|------------------------|--------------------------------------------------|---------------|
| GET    | `/counterparties`      | List all counterparties                          | OPERATOR (all), TRADER (own only) |
| POST   | `/counterparties`      | Create a counterparty                            | OPERATOR |
| GET    | `/counterparties/{id}` | Get a counterparty by ID                         | OPERATOR, TRADER (own only) |
| PUT    | `/counterparties/{id}` | Update a counterparty                            | OPERATOR |
| PATCH  | `/counterparties/{id}` | Add or deduct credit (JSON: `amount`, `operation`) | OPERATOR |
| DELETE | `/counterparties/{id}` | Delete a counterparty                            | OPERATOR |
| GET    | `/bonds`               | List all bonds                                   | OPERATOR, TRADER |
| POST   | `/bonds`               | Create a bond                                    | OPERATOR |
| GET    | `/bonds/{id}`          | Get a bond by ID                                 | OPERATOR, TRADER |
| PUT    | `/bonds/{id}`          | Update a bond                                    | OPERATOR |
| PATCH  | `/bonds/{id}`          | Add or deduct notional (JSON: `amount`, `operation`) | OPERATOR |
| DELETE | `/bonds/{id}`          | Delete a bond                                    | OPERATOR |
| GET    | `/rfqs`                | List all RFQs                                    | OPERATOR (all), TRADER (own only) |
| POST   | `/rfqs`                | Execute an RFQ                                   | OPERATOR, TRADER (own counterparty only) |
| GET    | `/rfqs/{id}`           | Get an RFQ by ID                                 | OPERATOR, TRADER (own only) |
| DELETE | `/rfqs/{id}`           | Delete an RFQ                                    | OPERATOR |

## Authentication and Authorization

Every endpoint requires HTTP Basic credentials; unauthenticated requests get `401`, and requests outside the caller's role or counterparty get `403`.

- **OPERATOR** manages counterparties, bonds and credit/notional, and may execute RFQs for any counterparty.
- **TRADER** is bound to one counterparty by LEI (`counterparty-lei`). On `POST /rfqs` the counterparty is taken from the logged-in user: `counterpartyId` may be omitted, and any other counterparty's id is rejected with `403`.

Users are configured under `rfq.security.users` in `application.properties`. Passwords are read from the environment; a user without a password cannot log in. Raw values are bcrypt-encoded at startup, or an encoded value such as `{bcrypt}$2a$10$...` can be supplied.

| User          | Role     | Counterparty                 | Password env var           |
|---------------|----------|------------------------------|----------------------------|
| `operator`    | OPERATOR | any                          | `RFQ_OPERATOR_PASSWORD`    |
| `acme-trader` | TRADER   | Acme Asset Management (LEI 549300EXAMPLE12345678) | `RFQ_ACME_TRADER_PASSWORD` |

`POST /rfqs` also rejects (`400`) non-positive amounts and an `executionPrice` outside `rfq.execution.min-price-pct`..`rfq.execution.max-price-pct` percent of the notional (default 50..150).

## Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Running the Application

```bash
cd monolith
export RFQ_OPERATOR_PASSWORD=<choose-one> RFQ_ACME_TRADER_PASSWORD=<choose-one>
./mvnw spring-boot:run
```

The application starts on port `8080`. Hit `/counterparties`, `/bonds`, and `/rfqs` with credentials to verify the REST endpoints, e.g. `curl -u operator:$RFQ_OPERATOR_PASSWORD localhost:8080/counterparties`.

## Testing

```bash
cd monolith
./mvnw clean test
```

See `IntegrationTest.java` for the full set of use-cases covering RFQ execution, insufficient notional/credit, and missing counterparty/bond scenarios, and `SecurityIntegrationTest.java` for authentication, role and counterparty-ownership checks. Test users are defined in `src/test/resources/application-test.properties`.
