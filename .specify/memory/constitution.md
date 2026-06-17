<!--
  SYNC IMPACT REPORT
  Version change: 1.0.0 → 1.2.0 (MINOR: log pattern + Records + MapStruct formalized)
  Added principles: IX. Communication Architecture
  Modified principles: VI (notification-service removed from direct HTTP calls)
  Added sections: Order State Machine
  Modified sections: Stack & Technology Decisions (Kafka, Records added)
  Templates reviewed:
    - .specify/templates/plan-template.md ✅ no changes required
    - .specify/templates/spec-template.md ✅ no changes required
    - .specify/templates/tasks-template.md ✅ no changes required
  Deferred items: none
-->

# E-commerce Order Platform Constitution

## Core Principles

### I. Clean Architecture (NON-NEGOTIABLE)
The `order-service` MUST be structured in three strictly separated layers:
- **Domain**: Entities, Value Objects, Aggregates, Domain Events, Repository Ports — zero external dependencies.
- **Application**: Use Cases and Application Services — depends only on the Domain layer.
- **Infrastructure**: Adapters (HTTP controllers, JPA repositories, HTTP clients) — depends on Application and Domain.

No dependency MUST flow inward toward Infrastructure from the Domain layer.
Cross-layer imports are a hard violation and MUST be caught in code review.

**Automated enforcement**: Architecture rules MUST be validated by **ArchUnit** (`archunit-junit5`) tests running on every CI build. The `ArchitectureTest` class MUST assert at minimum:
- `domain` has zero imports from `infrastructure`, `application`, Spring, or JPA.
- `application` has zero imports from `infrastructure`.
- `@RestController` classes reside only in `infrastructure.adapter.http`.
- Port interfaces reside only in `domain.port`.
- Use case classes reside only in `application.usecase`.
- No cyclic dependencies between packages.

### II. Domain-Driven Design
The domain model MUST be rich and expressive:
- Entities and Value Objects MUST encapsulate business rules — raw public setters are prohibited.
- The `Order` aggregate MUST be the single consistency boundary for all order state changes.
- Domain Events (e.g., `OrderConfirmed`, `PaymentApproved`, `OrderCancelled`) MUST be raised by the
  aggregate on meaningful state transitions.
- Primitive obsession is prohibited: use Value Objects for identifiers, monetary amounts, and status.

### III. Test-First TDD (NON-NEGOTIABLE)
Testing discipline MUST follow Red-Green-Refactor strictly:
- Unit tests MUST be written before implementation for all domain logic.
- Domain layer MUST achieve a minimum of **90% line coverage**.
- Mutation Testing with **Pitest** MUST achieve a minimum **MSI of 90%** on the domain module.
- Integration tests MUST use **Testcontainers** with a real PostgreSQL instance — no in-memory databases.
- WireMock integration tests MUST reuse the mappings in `wiremock/mappings/` via Testcontainers.

### IV. Idempotency
All mutation endpoints (`POST`, `DELETE`) MUST support the `Idempotency-Key` header:
- Re-submitting the same `Idempotency-Key` MUST return the original response without side effects.
- `OrderConfirmation` and `PaymentInitiation` MUST be idempotent at the use case level.
- Payment webhook callbacks MUST be idempotent: processing the same event multiple times MUST NOT
  generate duplicate state changes or transitions.

### V. Observability
The service MUST be fully observable from day one:

- **Logs**: Structured JSON via **logstash-logback-encoder**. Every log entry MUST contain the
  following mandatory fields propagated through MDC:

  ```json
  {
    "@timestamp": "2026-06-08T19:00:00.000Z",
    "level": "INFO",
    "logger_name": "com.plataforma.order.OrderService",
    "message": "Order confirmed",
    "correlationId": "uuid",
    "traceId":       "otel-trace-id",
    "spanId":        "otel-span-id",
    "service":       "order-service"
  }
  ```

  - `correlationId` MUST be extracted from the `X-Correlation-ID` request header (or generated if
    absent) and propagated to all outbound HTTP calls and Kafka message headers.
  - `traceId` and `spanId` MUST be populated automatically by the OpenTelemetry bridge.
  - Domain-relevant fields (e.g., `orderId`, `customerId`) SHOULD be included as MDC context when
    available.

- **Metrics**: Exposed via Micrometer + Prometheus at `/actuator/prometheus`. Business metrics
  (orders created, payments processed, payment rejections) MUST be instrumented.
- **Tracing**: Distributed tracing via OpenTelemetry, exporting spans to Jaeger (or console in dev).
  All inbound/outbound HTTP calls and Kafka produce/consume operations MUST produce spans.
- Docker Compose MUST include Prometheus + Grafana for local visualization.

### VI. External Services via WireMock Only
No stub, mock bean, or fake implementation of external services MUST exist in production code:
- **Customer Service**, **Catalog Service**, and **Payment Gateway** MUST be consumed as real HTTP
  endpoints pointing to WireMock.
- **Notification Service** MUST NOT be called directly by `order-service`. It is a downstream
  consumer of Kafka events — its WireMock mapping exists solely to document the contract.
- All simulated responses MUST be defined exclusively as JSON files in `wiremock/mappings/`.
- In tests, WireMock MUST be initialized via **Testcontainers** loading those same mapping files —
  never duplicated inline.

### VII. Security
All endpoints MUST be protected:
- Authentication via **JWT Bearer Token (OAuth2)**. The auth server MAY be Keycloak via Docker or a
  local stub — but the production security filter MUST be real.
- Authorization MUST use scope-based access control (e.g., `orders:write`, `payments:read`).
- OWASP Top 10 controls MUST be applied: input validation, rate limiting, security headers
  (HSTS, X-Content-Type-Options, X-Frame-Options).
- Error responses MUST follow **RFC 7807 (Problem Details)** — stack traces MUST NOT be exposed.

### IX. Communication Architecture
The `order-service` adopts a hybrid communication model:

**Inbound (synchronous REST)** — client-facing API only:
- All endpoints under `/api/v1/orders` and `/api/v1/payments` are synchronous REST.
- The client receives an immediate response for every operation.

**Outbound to external dependencies (synchronous HTTP via WireMock)**:
- `customer-service` — validate customer on order creation.
- `catalog-service` — verify product availability and fetch current price on confirmation.
- `payment-gateway` — initiate payment charge; receives webhook callback asynchronously.

**Outbound domain events (asynchronous Kafka)**:
- `order-service` MUST publish events to Kafka for every significant state transition.
- Consumers (notification, fulfillment, inventory) are NOT implemented — they are downstream.
- `order-service` MUST NOT know who consumes its events (no direct coupling to consumers).

| Event | Kafka Topic | Trigger |
|---|---|---|
| `OrderConfirmed` | `orders.confirmed` | Order successfully confirmed |
| `PaymentApproved` | `orders.payment-approved` | Payment callback approved |
| `PaymentRejected` | `orders.payment-rejected` | Payment callback rejected (each attempt) |
| `OrderCancelled` | `orders.cancelled` | Order cancelled (manual or after 3 rejections) |

### VIII. Concurrency & Consistency
The `Order` aggregate MUST be protected against concurrent modifications:
- **Optimistic Locking** via JPA `@Version` MUST be applied to the `Order` entity.
- Confirmation and payment initiation endpoints MUST handle `OptimisticLockException` gracefully,
  returning HTTP 409 Conflict with a RFC 7807 problem detail body.
- Pessimistic locking or distributed locks MUST NOT be used unless an ADR justifies why optimistic
  locking is insufficient for a specific operation.

## Stack & Technology Decisions

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x — Spring MVC, Spring Data JPA, Spring Security, Spring WebClient
- **Database**: PostgreSQL with **Flyway** versioned migrations
- **Messaging**: **Apache Kafka** via Spring Kafka — topics defined in `orders.*` namespace
- **Resilience**: Resilience4j Circuit Breaker on Payment Gateway calls (handles 503 instability)
- **API Documentation**: OpenAPI 3.1 via SpringDoc — Swagger UI at `/swagger-ui.html`
- **API Versioning**: URI path prefix `/api/v1/`
- **DTOs / Transfer objects**: Java **Records** MUST be used for ALL data transfer objects across
  every layer boundary — HTTP request/response, Kafka event payloads, and inter-layer commands.
  Classes with getters/setters MUST NOT be used for this purpose.
- **Layer mapping**: **MapStruct** (`@Mapper(componentModel = "spring")`) MUST be used for all
  conversions between Records and domain objects. Manual mapping code is prohibited.
  Mapping direction: `Record (HTTP in) → Domain command → Domain entity → Record (HTTP out)`.
  Kafka event records MUST also be produced via MapStruct mappers.
- **Containerization**: Docker + Docker Compose (app, PostgreSQL, Kafka, WireMock, Prometheus, Grafana, Jaeger)
- **CI/CD**: GitHub Actions — build → unit tests → integration tests → Trivy vulnerability scan

## Order State Machine

```
                    ┌─────────────────────────────────────┐
                    │                                     │
   POST /orders     ▼           POST /confirm             │
  ─────────────► OPEN ──────────────────────► CONFIRMED   │
                  │  ▲                          │    │     │
  DELETE /orders  │  │ (rejected, retry < 3)   │    │     │
  ─────────────►  │  │                          │    │     │
                  │  └──────── PAYMENT_PENDING ◄┘    │     │
                  │                  │                │     │
                  │            callback               │     │
                  │           approved                │     │
                  │                │                  │     │
                  │                ▼                  │     │
                  │         PAYMENT_APPROVED          │     │
                  │         (terminal — success)      │     │
                  │                                   │     │
                  └──────────────────────────────►    │     │
                          CANCELLED ◄─────────────────┘     │
                         (terminal)  rejected >= 3x         │
                              ▲                              │
                              └──────────────────────────────┘
                                DELETE /orders (before approved)
```

**Valid transitions:**
- `OPEN` → `CONFIRMED` (via POST /confirm, requires ≥ 1 item)
- `OPEN` → `CANCELLED` (via DELETE /orders)
- `CONFIRMED` → `PAYMENT_PENDING` (via POST /payments)
- `CONFIRMED` → `CANCELLED` (via DELETE /orders)
- `PAYMENT_PENDING` → `PAYMENT_APPROVED` (via callback — approved)
- `PAYMENT_PENDING` → `CONFIRMED` (via callback — rejected, attempts < 3)
- `PAYMENT_PENDING` → `CANCELLED` (via callback — rejected, attempts = 3)

**Any other transition MUST throw a domain exception.**

## Development Workflow

- Feature work MUST happen on dedicated branches (prefix: `feature/`).
- Every PR MUST pass all unit + integration tests and the Pitest MSI gate before merge.
- `docs/architecture.md` MUST be kept up-to-date with Bounded Context decisions and ADRs.
- Repository structure MUST follow the challenge layout:
  `docs/`, `order-service/`, `wiremock/mappings/`, `docker-compose.yml`, `.github/workflows/ci.yml`.
- **Delivery deadline: 2026-06-19 at 23:55** via LMS platform (GitHub repository link).

## Governance

This constitution supersedes all other development guidelines for the `order-service`.
- Amendments require updating this file, bumping the version per SemVer, and updating `LAST_AMENDED_DATE`.
- All implementation decisions MUST be verifiable against the principles above.
- Complexity beyond what a principle mandates MUST be documented as an ADR in `docs/architecture.md`.
- PRs MUST include a Constitution Check confirming no principle violations, or document justified exceptions.

**Version**: 1.2.0 | **Ratified**: 2026-06-08 | **Last Amended**: 2026-06-08
