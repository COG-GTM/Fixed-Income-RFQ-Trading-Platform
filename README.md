# split-the-monolith — Fixed-Income RFQ Trading Platform

A practice project for learning how to **split a monolith into microservices** using the **Strangler Fig** and **Branch By Abstraction** patterns. The domain models a **fixed-income Request-for-Quote (RFQ) trading platform** inspired by MarketAxess.

Technologies used:
 - SpringBoot (microservices themselves).
 - After split → Docker, K8s, and NGINX as ingress proxy to redirect traffic to each ms.

Everything will be in the master branch, having a specific `tag` for each Phase once finished.

- [split-the-monolith — Fixed-Income RFQ Trading Platform](#split-the-monolith--fixed-income-rfq-trading-platform)
  - [Phase 1 - THE MONOLITH](#phase-1---the-monolith)
  - [Phase 2 - Applying STRANGLER FIG](#phase-2---applying-strangler-fig)
    - [The new Microservice, RFQMs](#the-new-microservice-rfqms)
    - [The proxy, NGINX as K8s Ingress](#the-proxy-nginx-as-k8s-ingress)
  - [Phase 3 - Applying BRANCH BY ABSTRACTION](#phase-3---applying-branch-by-abstraction)

## Phase 1 - THE MONOLITH
This is a SpringBoot project simulating a fixed-income RFQ trading desk. Using H2 as an in-memory database for simplicity, the main class populates seed data on startup: one counterparty, one bond, and one executed RFQ.

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

REST API to create any of the above in `JSON` format. Have a look at `IntegrationTest.java` to see the use-cases.

Counterparty and Bond must be in place before executing an RFQ. If the bond has insufficient available notional or the counterparty has insufficient available credit, an exception will be thrown. The core logic is in `RFQExecutionSaga.java`, which attempts to execute an RFQ in a single transaction.

Notice a PATCH method endpoint for both `Counterparty` and `Bond` in their controllers to update credit / notional inventory.

Additionally, a trade confirmation will be sent to a counterparty whenever credit is added. Such confirmations are carried out via `TradeConfirmationService.java`. The implementation simply logs a message. BUT, it is still important that the service exists as the **Branch By Abstraction** pattern will be applied over the trade confirmation feature.

### REST Endpoints

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

### Seed Data

On startup the application loads:
- **Counterparty**: Acme Asset Management (LEI: 549300EXAMPLE12345678, credit limit: $50,000,000)
- **Bond**: US Treasury 2.75% 11/15/2030 (ISIN: US912828YK15, available notional: $100,000,000)
- **RFQ**: BUY $5,000,000 notional at $4,987,500 — status EXECUTED

## Phase 2 - Applying STRANGLER FIG
The goal is to extract `RFQ` management out of the monolith into a separate **RFQ Microservice (RFQMs)**.

Based on [Martin Fowler Strangler Fig post](https://martinfowler.com/bliki/StranglerFigApplication.html), we will "gradually create a new system around the edges of the old".

![Strangler Fig Pattern](https://raw.githubusercontent.com/javieraviles/split-the-monolith/master/images/strangler-fig.jpg)

A proxy will still forward `/counterparties` and `/bonds` API requests to the monolith, but `/rfqs` should go to the new microservice.

### The new Microservice, RFQMs
A separate SpringBoot project, containing DTOs for counterparty and bond, and one entity `Rfq.java`.

Only one REST controller for RFQ will be created here, and again the core logic is in `RFQExecutionSaga.java` which will attempt to execute an RFQ. We can't use a single transaction as we do in the monolith, so will use a rest client which will attempt to get credit and notional from the monolith. If something goes wrong we will need to perform a compensation.

The idea is, even though the implementation is different, we will get the same exceptions for the same use cases, so the external API remains exactly the same. "Counterparty and Bond have to be in place before executing an RFQ. If insufficient notional in the bond or credit in the counterparty, an exception will be thrown." Again have a look at `IntegrationTest.java` to see the use-cases.

### The proxy, NGINX as K8s Ingress
The NGINX Ingress will route traffic to the monolith or the RFQ microservice depending on the path:

```
spec:
  rules:
  - host: split-the-monolith.com
    http:
      paths:
      - path: /counterparties
        backend:
          serviceName: monolith
          servicePort: 8080
      - path: /bonds
        backend:
          serviceName: monolith
          servicePort: 8080
      - path: /rfqs
        backend:
          serviceName: rfqms
          servicePort: 8090
```

## Phase 3 - Applying BRANCH BY ABSTRACTION
Remember the `TradeConfirmationService` in the monolith? That could very well be another microservice, just in charge of sending trade confirmations, so the monolith does not need to have such responsibility anymore. We can spin up the new microservice and gradually switch confirmation generation from monolith to this new service, using a [feature toggle](https://martinfowler.com/articles/feature-toggles.html).

Based on [Martin Fowler Branch by Abstraction post](https://martinfowler.com/bliki/BranchByAbstraction.html), "While we are building the new feature we can use FeatureToggles to run the new supplier in test environments and compare its behavior to the flawed supplier".

![Branch by Abstraction Pattern](https://raw.githubusercontent.com/javieraviles/split-the-monolith/master/images/branch-by-abstraction.png)

After creating another SpringBoot project (a simple `ConfirmationController` to receive a POST for confirmation creation, calling a service that sends the trade confirmation), we will introduce a `feature toggle` in the monolith. When credit is added to a counterparty, depending on the value of the feature toggle, the confirmation will get sent through the monolith implementation (still there) or through the new service (the monolith will trigger a POST to the confirmation microservice URL).

For us the feature toggle is an application.property called `use.confirmation.service`, containing a boolean:

```java
@Value(value = "${use.confirmation.service}")
private boolean useConfirmationService;

...

if (useConfirmationService) {
  // rest call to new microservice
  confirmationMsClient.sendConfirmation(confirmation);
} else {
  // monolith sends the confirmation itself
  tradeConfirmationService.sendTradeConfirmation(confirmation);
}
```

This way, the k8s deployment yaml for the monolith will contain an environment variable that will override the feature toggle value per environment:

```yaml
- env:
  - name: CONFIRMATIONMS_URL
    value: http://confirmationms:8070
  - name: USE_CONFIRMATION_SERVICE
    value: "true"
  image: javieraviles/monolith
  name: monolith
  imagePullPolicy: Always
  ports:
    - containerPort: 8080
```

So operations will have the option to configure whether to use this external trade confirmation service or not. The idea is to reach a point where no more clients are using the original monolith confirmation feature so it can be removed.
