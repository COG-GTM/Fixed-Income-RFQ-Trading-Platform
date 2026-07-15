# Phase 0 — Database Seams

This note documents the per-domain database seams introduced as part of **Phase 0
(Foundations)** of the [microservices decomposition strategy](../MICROSERVICES_DECOMPOSITION_STRATEGY.md).
The goal is to carve the single H2 schema into per-domain **logical schemas** so that
future services can own their own data, and to surface the cross-domain coupling that
the current data model hides.

## What changed

All three entities now map to their own logical H2 schema instead of the single default
schema:

| Entity         | Table            | Schema          |
| -------------- | ---------------- | --------------- |
| `Counterparty` | `counterparties` | `counterparty`  |
| `Bond`         | `bonds`          | `bond`          |
| `Rfq`          | `rfqs`           | `rfq`           |

The mapping is done with JPA `@Table(schema = "...")` on each entity. H2 does not create
schemas automatically from the JPA mapping, so the schemas are created at startup via the
JDBC connection `INIT` clause in `application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:rfqdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;\
INIT=CREATE SCHEMA IF NOT EXISTS counterparty\;CREATE SCHEMA IF NOT EXISTS bond\;CREATE SCHEMA IF NOT EXISTS rfq
```

This establishes the *seams* — the boundaries along which the monolith will eventually be
split — without changing any business logic in the saga, entity domain methods
(`deductNotional` / `deductCredit`), or the controllers.

## The cross-schema FK problem this exposes

`Rfq` physically joins to both other domains via eager foreign keys:

```java
// entity/Rfq.java
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "counterparty_id")
private Counterparty counterparty;   // -> counterparty.counterparties(id)

@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "bond_id")
private Bond bond;                    // -> bond.bonds(id)
```

Now that each entity lives in its own schema, these foreign keys **cross schema
boundaries**: `rfq.rfqs.counterparty_id -> counterparty.counterparties.id` and
`rfq.rfqs.bond_id -> bond.bonds.id`. Within a single H2 database, cross-schema foreign
keys are still valid, so the app keeps working today. But once each schema is owned by a
separate service (separate database, separate deploy), a database-level FK across a
service boundary is no longer possible.

The coupling is not only at the schema level. `RfqController.toDto` dereferences the
related aggregates directly:

```java
// controller/RfqController.java
private RfqDto toDto(final Rfq rfq) {
    return new RfqDto(rfq.getId(),
        rfq.getCounterparty().getId(),   // cross-domain object navigation
        rfq.getBond().getId(),           // cross-domain object navigation
        ...);
}
```

Because the associations are `FetchType.EAGER`, loading any `Rfq` also loads its
`Counterparty` and `Bond` through a physical join — the RFQ domain cannot be read
without touching the other two domains' tables.

## What must change in later phases

This task **only establishes the schema seams and documents the coupling** — the FKs are
intentionally left in place so the build stays green and the app keeps serving
`/counterparties`, `/bonds`, and `/rfqs`. Removing the coupling is deferred:

1. **Replace object references with stored IDs.** Change `Rfq.counterparty` /
   `Rfq.bond` (`@ManyToOne` object references) into plain `counterpartyId` /
   `bondId` columns (`long`), dropping the `@JoinColumn` foreign keys. `toDto` then
   reads `rfq.getCounterpartyId()` / `rfq.getBondId()` directly instead of navigating
   into the other aggregates.
2. **Stop the eager cross-domain fetch.** With IDs instead of associations there is no
   join; any data the RFQ view needs from the other domains is fetched explicitly
   (e.g. via a repository/service call, and eventually a remote API call once the
   domains are separate services).
3. **Drop the physical FK constraints** between schemas so each schema can move to its
   own database. Referential integrity across domains becomes an application-level /
   eventual-consistency concern (validated in the `RFQExecutionSaga`), not a
   database-enforced one.

### Limitation

The three schemas currently live inside the **same** in-memory H2 database, so the eager
cross-schema FK still resolves. This is deliberate: it keeps the app functional while the
seams are in place. True per-service isolation (separate databases with no cross-database
FK) requires steps 1–3 above and is out of scope for Phase 0.
