<!--
  SYNC IMPACT REPORT
  Version change: (none) → 1.0.0 (initial ratification)
  Added principles: I through VIII (all new)
  Added sections: Stack & Technology Decisions, Development Workflow
  Removed sections: none (initial version)
  Templates reviewed:
    - .specify/templates/plan-template.md ✅ no changes required (generic gates reference)
    - .specify/templates/spec-template.md ✅ no changes required (generic placeholders)
    - .specify/templates/tasks-template.md ✅ no changes required (generic structure)
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
- Domain layer MUST achieve a minimum of **80% line coverage**.
- Mutation Testing with **Pitest** MUST achieve a minimum **MSI of 75%** on the domain module.
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
- **Logs**: Structured JSON via Logback. Every log entry MUST include a `correlationId` propagated
  through MDC across all service boundaries (inbound and outbound HTTP).
- **Metrics**: Exposed via Micrometer + Prometheus at `/actuator/prometheus`. Business metrics
  (orders created, payments processed, payment rejections) MUST be instrumented.
- **Tracing**: Distributed tracing via OpenTelemetry, exporting spans to Jaeger (or console in dev).
  All inbound/outbound HTTP calls MUST produce spans.
- Docker Compose MUST include Prometheus + Grafana for local visualization.

### VI. External Services via WireMock Only
No stub, mock bean, or fake implementation of external services MUST exist in production code:
- Customer Service, Catalog Service, Payment Gateway, and Notification Service MUST be consumed as
  real HTTP endpoints pointing to WireMock in all environments.
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
- **Resilience**: Resilience4j Circuit Breaker on Payment Gateway calls (handles 503 instability)
- **API Documentation**: OpenAPI 3.1 via SpringDoc — Swagger UI at `/swagger-ui.html`
- **API Versioning**: URI path prefix `/api/v1/`
- **Containerization**: Docker + Docker Compose (app, PostgreSQL, WireMock, Prometheus, Grafana, Jaeger)
- **CI/CD**: GitHub Actions — build → unit tests → integration tests → Trivy vulnerability scan

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

**Version**: 1.0.0 | **Ratified**: 2026-06-08 | **Last Amended**: 2026-06-08
