# API Spec — Fixed-Income RFQ Trading Platform

Version-controlled API contracts for the platform, authored as part of
**Phase 0 (Foundations)** of the strangler-fig microservices decomposition.

## Contents

- `openapi.yaml` — OpenAPI 3.x contract describing the **current** REST API of
  the monolith, organized by business capability (tags): **Counterparty**,
  **Bond**, **RFQ**. It also documents the **Trade Confirmation** contract
  (`TradeConfirmationDto`). Every endpoint in the README's REST Endpoints table
  is documented.
- `redocly.yaml` — Redocly lint ruleset used to validate the spec.
- `validate.sh` — lightweight validation/lint entrypoint.

## Keeping the spec in sync with the code

The running application serves live, annotation-driven docs via
[springdoc-openapi](https://springdoc.org/):

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Controllers and DTOs carry `@Operation` / `@Schema` annotations so the generated
docs stay aligned with `openapi.yaml`. When an endpoint or model changes, update
both the annotations and this spec.

## Validating the spec

Via the script (requires Node.js / `npx`):

```bash
./api-spec/validate.sh
```

Or through Maven (so CI can enforce it):

```bash
cd monolith && ./mvnw -Pvalidate-openapi verify
```
