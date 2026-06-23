# Counterparty (Credit Management) Service

Independent microservice extracted from the Fixed-Income RFQ Trading Platform monolith. It owns the
**Counterparty / Credit Management** bounded context: counterparty records and their available credit.

Technologies used:
 - Java 11, Spring Boot 2.2.6, Spring Data JPA
 - H2 in-memory database
 - Apache HttpClient (RestTemplate)
 - Maven

## Domain Entity

 - **Counterparty**
   - name (3-100 chars)
   - lei (Legal Entity Identifier, 24 chars)
   - creditLimit (BigDecimal, precision 19 scale 2)
   - availableCredit (BigDecimal, precision 19 scale 2)

`availableCredit` is initialized from `creditLimit` on first persist if not provided.
`deductCredit()` throws `InsufficientCreditException` (HTTP 400) when the amount exceeds available credit.

## REST Endpoints

| Method | Path                   | Description                                          |
|--------|------------------------|------------------------------------------------------|
| GET    | `/counterparties`      | List all counterparties                              |
| POST   | `/counterparties`      | Create a counterparty (returns 201)                 |
| GET    | `/counterparties/{id}` | Get a counterparty by ID (404 if not found)         |
| PUT    | `/counterparties/{id}` | Full update (name, lei, creditLimit, availableCredit)|
| PATCH  | `/counterparties/{id}` | Add or deduct credit (JSON: `amount`, `operation`)  |
| DELETE | `/counterparties/{id}` | Delete a counterparty                               |

The API contract matches the monolith's `/counterparties` endpoints exactly.

### Credit ADD and trade confirmations

On a PATCH with `operation = ADD`, the service always sends a trade confirmation to the
**Trade Confirmation microservice** (extracted in WP A) by POSTing a `TradeConfirmationDto`
to `{confirmationms.url}confirmations/`. There is no feature flag — the confirmation service is an
external dependency. See `TradeConfirmationClient`.

## Configuration

| Property             | Default                     | Description                                  |
|----------------------|-----------------------------|----------------------------------------------|
| `server.port`        | `8072`                      | Port the service listens on                  |
| `confirmationms.url` | `http://localhost:8070/`    | Base URL of the Trade Confirmation service   |

## Seed Data

On startup the service loads one counterparty:
- **Acme Asset Management** (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)

## Running the Service

```bash
cd counterparty-service
./mvnw spring-boot:run
```

The service starts on port `8072`. Hit `/counterparties` to verify the REST endpoints.

## Testing

```bash
cd counterparty-service
./mvnw clean test
```
