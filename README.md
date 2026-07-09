# Fixed-Income RFQ Trading Platform

A SpringBoot monolith simulating a **fixed-income Request-for-Quote (RFQ) trading platform**.

Technologies used:
 - Java 11, Spring Boot, Spring Data JPA
 - H2 in-memory database
 - Maven

- [Fixed-Income RFQ Trading Platform](#fixed-income-rfq-trading-platform)
  - [Domain Entities](#domain-entities)
  - [REST Endpoints](#rest-endpoints)
  - [Seed Data](#seed-data)
  - [Real-time streaming](#real-time-streaming)
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

## Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Real-time streaming

The platform pushes live updates (bond prices, RFQ lifecycle, inventory and credit
changes) to clients in real time. Streaming is delivered over a STOMP WebSocket
transport with a Server-Sent Events (SSE) fallback, and is driven by a simulated
market-data feed.

### STOMP WebSocket transport

- **Connect endpoint**: `/ws` — supported both as a raw WebSocket and via
  [SockJS](https://github.com/sockjs) for browsers/proxies that cannot use native
  WebSockets.
- **Broker prefix**: `/topic`
- **Topics**:
  - `/topic/prices` — bond price ticks from the market-data feed
  - `/topic/rfqs` — RFQ lifecycle events (PENDING / QUOTED / EXECUTED / REJECTED)
  - `/topic/inventory` — bond available-notional changes
  - `/topic/credit` — counterparty available-credit changes

### SSE fallback

- **Endpoint**: `GET /stream/sse` — a Server-Sent Events stream for clients that
  prefer (or are restricted to) plain HTTP streaming instead of WebSockets.
- A static demo page that consumes the streams is served at
  [`http://localhost:8080/`](http://localhost:8080/).

### Simulated market-data feed

A background feed periodically updates bond prices and publishes price ticks to
`/topic/prices` (and the SSE stream), simulating a live market without an external
data provider.

### Configuration toggles

Configured in `monolith/src/main/resources/application.properties`, following the
existing `use.confirmation.service` feature-toggle style:

| Property | Purpose | Default |
|----------|---------|---------|
| `streaming.marketdata.enabled` | Master enable/disable for the simulated market-data feed | `true` |
| `streaming.marketdata.interval-ms` | Interval (in milliseconds) of the simulated price feed | `2000` |
| `streaming.websocket.enabled` | Enable/disable the WebSocket/STOMP transport | `true` |
| `streaming.sse.enabled` | Enable/disable the SSE fallback endpoint | `true` |

### Architecture / roadmap

Today, streaming is powered entirely in-process: domain components publish events to
a Spring `ApplicationEventPublisher` event bus, and a listener bridges those events to
the STOMP broker (and the SSE stream), fanning them out to connected clients. This is
simple and requires no external infrastructure, but it is confined to a single
application instance — events raised on one node are not visible to clients connected
to another.

The future path is to swap the in-process event bus for **Apache Kafka**. Domain
events would be published to Kafka topics (e.g. `prices`, `rfqs`, `inventory`,
`credit`), and a dedicated consumer would subscribe to those topics and fan messages
out to the WebSocket/SSE transports. This decouples event production from delivery and
lets the platform scale horizontally: any number of application instances can produce
and consume the same event streams, so streaming works consistently across a
multi-instance deployment.

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
