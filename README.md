# SecBank Card Request API

A REST API standing in for SecBank's Core Banking system, built for a technical case study: a FinTech partner uses this service to submit a card request and check its status. Two endpoints, on purpose, everything else was deliberately kept out of scope.

## What this does (and doesn't do)

**In scope:**
- Create a card request for an existing, seeded customer
- Check the status of a card request by its reference
- Idempotent creation via an `Idempotency-Key` header
- A background job that moves requests through `PENDING → APPROVED/REJECTED → ISSUED` on its own, no client trigger

**Deliberately out of scope:** checkout flows, transaction posting, and card issuance/dispatch logic. This service answers "can I request a card, and what's happening with it," nothing else.

## Why it's built the way it is

A few decisions here aren't obvious from the code alone, so they're worth stating up front:

- **References are opaque, not the database ID.** A sequential ID in a URL lets anyone enumerate other partners' card requests. References are generated from `SecureRandom` bytes over a 62-character alphabet, checked against the database for collisions before use.
- **Customers have to already exist.** This mirrors how BaaS actually works, the bank is the system of record for identity, so an unknown `customerId` is rejected (`422`) rather than silently created from whatever a partner's payload says.
- **`Idempotency-Key` is a real contract, not just a header that's accepted.** Same key with the same body replays the original response. Same key with a different body is a `409`, treated as a client-side bug, not silently reprocessed or silently replayed.
- **Rejection is a seeded business rule, not randomness.** A `HIGH_RISK` customer's requests get rejected, deterministically, so the same input produces the same outcome every time, useful for a demo, and honest about how a real underwriting rule would work.
- **Customers are referenced by ID (a plain string field), not a JPA relationship.** Keeps `CardRequest` decoupled from `Customer`'s persistence lifecycle, and sidesteps lazy-loading/session-boundary issues entirely.

## Stack

Java 21, Spring Boot 4.1.1, Spring Data JPA, embedded H2, springdoc-openapi for Swagger UI.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/v1/card-requests` | Create a card request. Returns `201`, a `Location` header, and the new reference. |
| `GET` | `/api/v1/card-requests/{reference}` | Return the current status of a card request, or `404` if the reference doesn't exist. |

Full request/response shapes and every documented error response are in Swagger UI once the app is running.

## Running it

```bash
./mvnw spring-boot:run
```

The app boots with two seeded customers so you have something to test against immediately:

| customerId | riskTier | what you'll see |
|---|---|---|
| `CUST-1001` | `STANDARD` | `PENDING → APPROVED → ISSUED` over two scheduler ticks |
| `CUST-1002` | `HIGH_RISK` | `PENDING → REJECTED` immediately |

Once it's running:
- API: `http://localhost:8080/api/v1/card-requests`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:secbank`, user `sa`, no password)

The database is in-memory and rebuilt on every restart, so nothing persists between runs by design, this is a demo standing in for Core Banking, not Core Banking itself.

## Testing

```bash
./mvnw test
```

Three layers, each testing something the others can't:

- **`CardRequestServiceTest`** - unit tests with mocked repositories, 
  covering creation, customer validation, reference collision retries, idempotency (replay and conflict), and status lookup.
- **`CardRequestStatusAdvancementIntegrationTest`** - real Spring 
  context, real H2, real transactions. This one exists specifically because a bug found during manual testing (a request skipping straight from `PENDING` to `ISSUED` in a single scheduler tick) only reproduces with Hibernate's actual auto-flush behavior; a mocked repository can't catch it. This test pins that fix.
- **`CardRequestControllerTest`** - full HTTP-layer tests through 
  MockMvc: status codes, the `Location` header, validation failures, and the idempotency conflict path.

## A known, disclosed limitation

If the exact same `Idempotency-Key` arrives twice at the exact same instant (a true concurrent race, not a sequential retry), both requests could miss each other's not-yet-committed record. The database's primary key on `idempotency_key` prevents a duplicate row from ever being persisted, but the second request in that narrow window would see a raw constraint failure rather than a clean replay. Real clients retry sequentially after a timeout, which this handles correctly, hardening the true-concurrency edge case would mean pessimistic locking or a catch-and-refetch step, deliberately left out here as more complexity than this scope needs.
