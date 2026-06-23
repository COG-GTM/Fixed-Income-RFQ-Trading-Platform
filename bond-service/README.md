# Bond Inventory Service

A standalone Spring Boot microservice extracted from the Fixed-Income RFQ monolith
following the **Strangler Fig** pattern. It owns the bond bounded context (bond
inventory and available notional), replicating the monolith's bond REST API and
persistence.

## Technologies

- Java 11
- Spring Boot 2.2.6 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`)
- H2 in-memory database
- Maven

## Domain

A **Bond** has:

| Field              | Type       | Notes                                  |
| ------------------ | ---------- | -------------------------------------- |
| `id`               | long       | Generated identifier                   |
| `isin`             | String     | Unique, 12 chars (e.g. `US912828YK15`) |
| `issuer`           | String     |                                        |
| `couponRate`       | BigDecimal | precision 7, scale 4                   |
| `maturityDate`     | LocalDate  |                                        |
| `availableNotional`| BigDecimal | precision 19, scale 2                  |

`deductNotional()` throws `InsufficientNotionalException` (HTTP 400) when the
requested amount exceeds `availableNotional`.

## API

| Method | Path           | Description                                   | Success |
| ------ | -------------- | --------------------------------------------- | ------- |
| GET    | `/bonds`       | List all bonds                                | 200     |
| POST   | `/bonds`       | Create a bond                                 | 201     |
| GET    | `/bonds/{id}`  | Get one bond (404 if not found)               | 200     |
| PUT    | `/bonds/{id}`  | Full update                                   | 200     |
| PATCH  | `/bonds/{id}`  | Adjust notional (`amount` + `operation`)      | 200     |
| DELETE | `/bonds/{id}`  | Delete a bond                                 | 200     |

The `PATCH` body adjusts the available notional:

```json
{ "amount": "1000000.00", "operation": "DEDUCT" }
```

`operation` is `ADD` or `DEDUCT`. A `DEDUCT` larger than the available notional
returns `400 Bad Request` ("Insufficient notional"). An unknown operation returns
`400 Bad Request` ("wrong operation").

Example:

```bash
curl -i -X PATCH http://localhost:8071/bonds/1 \
  -H "Content-Type: application/json" \
  -d '{"amount":"1000000.00","operation":"DEDUCT"}'
```

## Running

The service listens on port **8071**, matching the monolith's
`bondms.url=http://localhost:8071/`. It seeds one bond (`US912828YK15`) on startup.

```bash
cd bond-service
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/bond-service.jar
```

## Integration with the monolith

The monolith routes bond notional deductions here when the `use.bond.service`
feature flag in `monolith/src/main/resources/application.properties` is `true`.
In that case `RFQExecutionSaga` calls this service via `BondMicroserviceClient`
(`PATCH /bonds/{id}` with `operation=DEDUCT`) instead of using the local
`bondRepository`. When `false` (the default), the monolith keeps its existing
in-process behavior.
