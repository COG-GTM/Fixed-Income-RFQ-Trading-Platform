# Trade Confirmation Service

A standalone Spring Boot microservice extracted from the Fixed-Income RFQ monolith
following the **Strangler Fig** pattern. It receives trade confirmations that were
previously handled in-process by the monolith's `TradeConfirmationService`.

## Technologies

- Java 11
- Spring Boot 2.2.6 (`spring-boot-starter-web`)
- H2 (available for optional persistence)
- Maven

## API

### `POST /confirmations/`

Records a trade confirmation. Currently it logs the confirmation (mirroring the
monolith's `TradeConfirmationService.sendTradeConfirmation()`).

Request body:

```json
{
  "counterpartyName": "Acme Asset Management",
  "creditAmount": 1000000.00
}
```

| Field             | Type       | Description                     |
| ----------------- | ---------- | ------------------------------- |
| `counterpartyName`| String     | Name of the counterparty        |
| `creditAmount`    | BigDecimal | Credit amount being confirmed   |

Response: `201 Created` (empty body).

Example:

```bash
curl -i -X POST http://localhost:8070/confirmations/ \
  -H "Content-Type: application/json" \
  -d '{"counterpartyName":"Acme Asset Management","creditAmount":1000000.00}'
```

## Running

The service listens on port **8070**, matching the monolith's
`confirmationms.url=http://localhost:8070/`.

```bash
cd confirmation-service
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/confirmation-service.jar
```

## Integration with the monolith

The monolith toggles between the in-process service and this microservice via the
`use.confirmation.service` feature flag in
`monolith/src/main/resources/application.properties`. When `true` (the default),
the monolith's `CounterpartyController` routes confirmations here through
`TradeConfirmationMicroserviceClient`.
