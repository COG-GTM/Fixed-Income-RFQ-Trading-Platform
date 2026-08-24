# Fixed-Income RFQ Trading Platform

A SpringBoot monolith simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

Technologies used:
 - Java 21, Spring Boot 3.5, Spring Data JPA (Hibernate 6)
 - H2 in-memory database (swappable via environment variables)
 - Maven, Docker, GitHub Actions

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Seed Data](#seed-data)
  - [Configuration](#configuration)
  - [Running the Application](#running-the-application)
  - [Running in Docker](#running-in-docker)
  - [Testing](#testing)
  - [Continuous Integration](#continuous-integration)

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
| GET    | `/actuator/health`     | Liveness/readiness health probe                  |

## Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Configuration

All configuration is externalized (12-factor): `application.properties` contains only `${ENV_VAR:default}`
placeholders, so nothing needs to be edited or rebuilt to run in another environment. No hosts, credentials
or toggles are hardcoded in Java code.

| Environment variable       | Default                                  | Description                                                     |
|----------------------------|------------------------------------------|-----------------------------------------------------------------|
| `APP_NAME`                 | `fixed-income-rfq`                       | Spring application name (log/metric tagging)                     |
| `SERVER_PORT`              | `8080`                                   | HTTP listen port                                                 |
| `DATASOURCE_URL`           | `jdbc:h2:mem:rfqdb;DB_CLOSE_DELAY=-1`    | JDBC URL; point at Postgres/Oracle/etc. to leave H2 behind       |
| `DATASOURCE_DRIVER`        | `org.h2.Driver`                          | JDBC driver class                                                |
| `DATASOURCE_USERNAME`      | `sa`                                     | Database username                                                |
| `DATASOURCE_PASSWORD`      | *(empty)*                                | Database password — inject from a secret store, never commit     |
| `JPA_DDL_AUTO`             | `update`                                 | Hibernate schema strategy (`none` for managed schemas)           |
| `JPA_OPEN_IN_VIEW`         | `false`                                  | Spring Data open-in-view                                         |
| `JPA_SHOW_SQL`             | `false`                                  | Log generated SQL                                                |
| `H2_CONSOLE_ENABLED`       | `false`                                  | Expose the H2 web console (local debugging only)                 |
| `USE_CONFIRMATION_SERVICE` | `false`                                  | Feature toggle: route confirmations to ConfirmationMS instead of the in-process service |
| `CONFIRMATION_SERVICE_URL` | `http://localhost:8070/`                 | Base URI of ConfirmationMS, used when the toggle is on           |
| `MANAGEMENT_ENDPOINTS`     | `health,info`                            | Actuator endpoints exposed over HTTP                             |
| `LOG_LEVEL_ROOT`           | `INFO`                                   | Root log level                                                   |
| `LOG_LEVEL_APP`            | `INFO`                                   | Log level for `com.javieraviles.splitthemonolith`                |

## Running the Application

Requires JDK 21.

```bash
cd monolith
./mvnw spring-boot:run
```

The application starts on port `8080`. Hit `/counterparties`, `/bonds`, and `/rfqs` to verify the REST endpoints.

## Running in Docker

The multi-stage `Dockerfile` builds the jar with Maven/JDK 21 and runs the extracted layered Spring Boot
application on a JRE 21 base image as the non-root `rfq` user, with a `/actuator/health` healthcheck.

```bash
docker build -t fixed-income-rfq:local .
docker run --rm -p 8080:8080 -e USE_CONFIRMATION_SERVICE=false fixed-income-rfq:local
```

Or with compose (every variable above can be overridden in the environment or an `.env` file):

```bash
docker compose up --build
curl http://localhost:8080/actuator/health
```

## Testing

```bash
cd monolith
./mvnw clean test
```

See `IntegrationTest.java` for the HTTP-level use-cases covering RFQ execution, insufficient notional/credit,
and missing counterparty/bond scenarios, and `RFQExecutionSagaTest.java` for the saga-level tests asserting
credit/notional adjustments, boundary conditions and transactional atomicity on failure.

## Continuous Integration

`.github/workflows/ci.yml` runs on every push and pull request: it builds and tests on JDK 21, uploads the
surefire reports, then builds the container image and smoke-tests the running container against
`/actuator/health`, `/counterparties`, `/bonds` and `/rfqs`.
