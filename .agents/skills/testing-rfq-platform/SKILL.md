---
name: testing-rfq-platform
description: Test the fixed-income RFQ trading platform REST API end-to-end. Use when verifying Counterparty/Bond/RFQ endpoint changes or saga transactional logic.
---

# Testing the RFQ Trading Platform

## Quick Start

```bash
cd monolith && ./mvnw spring-boot:run
# Wait ~10s for startup, then test:
curl -s http://localhost:8080/counterparties | python3 -m json.tool
```

Port: **8080** (configured in `application.properties`).
Database: **H2 in-memory** — data resets on every restart.

## REST Endpoints

| Method | Endpoint | Purpose |
|--------|----------|--------|
| GET | /counterparties | List all counterparties |
| POST | /counterparties | Create counterparty (JSON: name, lei, creditLimit) |
| GET | /counterparties/{id} | Get one counterparty |
| PATCH | /counterparties/{id} | Top up/deduct credit (JSON: amount, operation=ADD/DEDUCT) |
| GET | /bonds | List all bonds |
| POST | /bonds | Create bond (JSON: isin, issuer, couponRate, maturityDate, availableNotional) |
| GET | /bonds/{id} | Get one bond |
| PATCH | /bonds/{id} | Add/deduct notional (JSON: amount, operation=ADD/DEDUCT) |
| GET | /rfqs | List all RFQs |
| POST | /rfqs | Execute RFQ via saga (JSON: counterpartyId, bondId, notionalAmount, side, executionPrice) |

## Seed Data (loaded on startup)

- **Counterparty**: id=1, "Acme Asset Management", LEI="549300EXAMPLE12345678", creditLimit=50M
- **Bond**: id=2, ISIN="US912828YK15", "US Treasury", coupon=2.75%, maturity=2030-11-15, notional=100M
- **RFQ**: id=3, executed BUY, notional=5M, price=4,987,500

Note: Seed RFQ is inserted directly via `rfqRepository.save()`, NOT through the saga, so seed counterparty credit and bond notional are NOT deducted.

## Auto-Generated IDs

H2 uses `GenerationType.AUTO` with a shared sequence across all entities. IDs increment globally: Counterparty=1, Bond=2, RFQ=3 from seed data. New entities continue from there.

## Key Testing Scenarios

### 1. RFQ Execution (Happy Path)
```bash
# Create counterparty and bond, then execute RFQ
curl -X POST localhost:8080/counterparties -H 'Content-Type: application/json' \
  -d '{"name":"Test Fund","lei":"549300TESTFND00001","creditLimit":10000000}'
curl -X POST localhost:8080/bonds -H 'Content-Type: application/json' \
  -d '{"isin":"US912828TS01","issuer":"Test","couponRate":4.5,"maturityDate":"2035-06-15","availableNotional":20000000}'
# Use the returned IDs in the RFQ:
curl -X POST localhost:8080/rfqs -H 'Content-Type: application/json' \
  -d '{"counterpartyId":4,"bondId":5,"notionalAmount":2000000,"side":"BUY","executionPrice":1995000}'
# VERIFY: GET the counterparty and bond to confirm deduction
```

### 2. Error Cases
- **Insufficient notional**: POST /rfqs with notionalAmount > bond's availableNotional → HTTP 400
- **Insufficient credit**: POST /rfqs with executionPrice > counterparty's availableCredit → HTTP 400
- **Not found**: POST /rfqs with nonexistent counterpartyId or bondId → HTTP 404

### 3. Transactional Rollback
Critical: When credit check fails, verify the bond's notional was NOT deducted (saga runs `bond.deductNotional()` before `counterparty.deductCredit()`, so rollback must undo both).

## Common Pitfalls

- **ISIN length**: The `isin` column is `VARCHAR(12)`. Standard ISINs are exactly 12 characters. Test ISINs longer than 12 chars will cause a 500 error.
- **LEI length**: The `lei` column is `VARCHAR(24)`. LEIs are typically 20 characters.
- **availableCredit on POST**: Uses `@PrePersist` to default `availableCredit = creditLimit` when not provided in JSON. If this callback is missing or broken, POST will return `availableCredit: null`.
- **BigDecimal serialization**: All monetary fields serialize as numbers with decimal points (e.g., `50000000.0`). After PATCH operations, they may show trailing zeros (e.g., `50000000.00`).

## Devin Secrets Needed

None — this is a local Spring Boot app with H2 in-memory database. No external services or credentials required.

## Unit Tests

```bash
cd monolith && ./mvnw clean test
# 7 integration tests covering: seed data, successful RFQ, insufficient notional,
# insufficient credit, counterparty not found, bond not found
```
