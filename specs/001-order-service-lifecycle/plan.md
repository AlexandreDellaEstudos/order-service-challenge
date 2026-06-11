# Implementation Plan: Order Service — Ciclo de Vida Completo de Pedidos

**Branch**: `001-order-service-lifecycle` | **Date**: 2026-06-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-order-service-lifecycle/spec.md`

**Note**: Plano gerado com base no `desafio.md` e na constituição do projeto (`.specify/memory/constitution.md`).

---

## Summary

Implementar o `order-service`, único serviço da plataforma de e-commerce a ser desenvolvido. O serviço gerencia o ciclo de vida completo de pedidos (OPEN → CONFIRMED → PAYMENT_PENDING → PAYMENT_APPROVED / CANCELLED) seguindo Clean Architecture com três camadas estritas (Domain, Application, Infrastructure), DDD com `Order` como Aggregate Root, e integração com serviços externos via WireMock. Todos os endpoints de mutação são idempotentes, o controle de concorrência usa optimistic locking, e o sistema é totalmente observável desde o primeiro commit.

---

## Technical Context

**Language/Version**: Java 21 (LTS) — Records, Pattern Matching, Virtual Threads habilitados no Tomcat

**Primary Dependencies**:
- Spring Boot 3.x (MVC, Data JPA, Security, WebClient)
- Spring Kafka
- Resilience4j (Circuit Breaker no Payment Gateway)
- MapStruct `@Mapper(componentModel = "spring")` — mapeamento Records ↔ Domain
- Flyway (migrations versionadas)
- SpringDoc OpenAPI 3.1 — Swagger UI em `/swagger-ui.html`
- logstash-logback-encoder (logs JSON estruturados)
- Micrometer + OpenTelemetry (métricas + tracing)
- Testcontainers (PostgreSQL 16, Kafka, WireMock)
- Pitest (mutation testing — MSI ≥ 75% no módulo domain)

**Storage**: PostgreSQL 16 — tabelas `orders`, `order_items`, `payments`, `idempotency_keys`

**Testing**: JUnit 5, Testcontainers, WireMock via Testcontainers, Pitest

**Target Platform**: Linux container (Docker), CI via GitHub Actions

**Performance Goals**: p95 < 300ms para operações de leitura; concorrência tratada via optimistic locking

**Constraints**: Zero dependência de framework no `domain/`; Records para todos os DTOs; MapStruct para todo mapeamento; zero mock beans no código de produção

**Scale/Scope**: Serviço único num monorepo; prazo de entrega: 2026-06-19 às 23:55

---

## Constitution Check

| Princípio | Status | Evidência no plano |
|---|---|---|
| I. Clean Architecture | PASS | 3 camadas: `domain/`, `application/`, `infrastructure/` com Dependency Rule respeitada |
| II. DDD | PASS | `Order` como Aggregate Root; VOs para OrderId, CustomerId, ProductId, Money; Domain Events em `domain/event/` |
| III. TDD NON-NEGOTIABLE | PASS | Unitários no domínio escritos primeiro (Red-Green-Refactor); cobertura de linhas ≥ 80% no módulo domain; Testcontainers para integração; Pitest MSI ≥ 75% |
| IV. Idempotência | PASS | Tabela `idempotency_keys`; header `Idempotency-Key` em todos os endpoints POST/DELETE |
| V. Observabilidade | PASS | MDC com correlationId + traceId + spanId; Micrometer counters de negócio; OTel traces; Docker Compose com Prometheus + Grafana + Jaeger |
| VI. WireMock Only | PASS | CustomerWebClient, CatalogWebClient, PaymentGatewayWebClient → WireMock; zero stub/mock bean em produção |
| VII. Segurança | PASS | Spring Security OAuth2 Resource Server; scopes `orders:write`, `orders:read`, `payments:write`, `payments:read`; OWASP headers; RFC 7807 errors |
| VIII. Concorrência | PASS | `@Version` na entidade Order JPA; OptimisticLockException → HTTP 409 Problem Details |
| IX. Comunicação | PASS | REST síncrono (inbound); HTTP síncrono para Customer/Catalog/Payment (WireMock); Kafka assíncrono para domain events |

**Gate: APROVADO** — nenhuma violação detectada. Pronto para `/speckit-tasks`.

---

## Project Structure

### Documentação desta feature

```text
specs/001-order-service-lifecycle/
├── plan.md              ← este arquivo
├── research.md          ← decisões de design (D-001 a D-010)
├── data-model.md        ← entidades, VOs, tabelas, ports, records
├── quickstart.md        ← cenários de validação end-to-end
├── contracts/
│   ├── api-orders.md    ← contrato REST /api/v1/orders
│   ├── api-payments.md  ← contrato REST /api/v1/payments
│   └── events.md        ← eventos Kafka + mapeamentos WireMock
├── checklists/
│   └── requirements.md
└── tasks.md             ← gerado pelo /speckit-tasks (próximo passo)
```

### Estrutura do Repositório (raiz do projeto)

```text
/
├── docs/
│   └── architecture.md
├── order-service/
│   ├── src/
│   │   ├── main/java/com/plataforma/order/
│   │   │   ├── domain/
│   │   │   │   ├── model/           ← Order, OrderItem, Payment, OrderStatus, PaymentStatus
│   │   │   │   ├── event/           ← OrderConfirmed, PaymentApproved, PaymentRejected, OrderCancelled
│   │   │   │   └── port/            ← OrderRepository, CustomerPort, CatalogPort, PaymentGatewayPort, DomainEventPublisher
│   │   │   ├── application/
│   │   │   │   └── usecase/         ← 9 use cases (ver tabela abaixo)
│   │   │   └── infrastructure/
│   │   │       ├── adapter/
│   │   │       │   ├── http/        ← OrderController, PaymentController
│   │   │       │   ├── persistence/ ← OrderJpaRepository, PaymentJpaRepository, IdempotencyJpaRepository
│   │   │       │   ├── client/      ← CustomerWebClient, CatalogWebClient, PaymentGatewayWebClient
│   │   │       │   └── messaging/   ← KafkaEventPublisher
│   │   │       ├── config/          ← SecurityConfig, KafkaConfig, WebClientConfig, ResilienceConfig, ObservabilityConfig
│   │   │       └── mapper/          ← OrderMapper, PaymentMapper, OrderEventMapper (MapStruct)
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/        ← V1__orders.sql, V2__order_items.sql, V3__payments.sql, V4__idempotency_keys.sql
│   └── test/java/com/plataforma/order/
│       ├── domain/                  ← testes unitários (TDD — escritos antes da implementação)
│       ├── application/             ← testes unitários dos use cases
│       └── infrastructure/          ← testes de integração com Testcontainers + WireMock
├── wiremock/
│   ├── mappings/                    ← 10 arquivos JSON de mapeamento
│   └── __files/
├── docker-compose.yml
├── README.md
└── .github/workflows/ci.yml
```

**Structure Decision**: Pacotes por camada (não por feature), conforme Screaming Architecture da constituição. O código deve gritar o domínio, não o framework.

---

## Use Cases — Application Layer

| Use Case | Comando de Entrada | Transição | Eventos |
|---|---|---|---|
| `CreateOrderUseCase` | `CreateOrderCommand(customerId)` | → OPEN | — |
| `AddItemUseCase` | `AddItemCommand(orderId, productId, qty)` | OPEN (sem transição) | — |
| `RemoveItemUseCase` | `RemoveItemCommand(orderId, itemId)` | OPEN (sem transição) | — |
| `ConfirmOrderUseCase` | `ConfirmOrderCommand(orderId)` | OPEN → CONFIRMED | `OrderConfirmed` |
| `CancelOrderUseCase` | `CancelOrderCommand(orderId)` | OPEN/CONFIRMED/PAYMENT_PENDING → CANCELLED | `OrderCancelled` |
| `InitiatePaymentUseCase` | `InitiatePaymentCommand(orderId)` | CONFIRMED → PAYMENT_PENDING | — |
| `ProcessPaymentCallbackUseCase` | `ProcessCallbackCommand(paymentId, status)` | PAYMENT_PENDING → APPROVED/CONFIRMED/CANCELLED | `PaymentApproved` / `PaymentRejected` / `OrderCancelled` |
| `GetOrderUseCase` | `OrderId` | — leitura | — |
| `ListOrdersByCustomerUseCase` | `CustomerId` | — leitura | — |
| `GetPaymentUseCase` | `PaymentId` | — leitura | — |

---

## Complexity Tracking

> Sem violações da constituição. Nenhuma entrada nesta seção.

---

## Artifacts Gerados

| Artefato | Caminho |
|---|---|
| Spec | `specs/001-order-service-lifecycle/spec.md` |
| Decisões de design (research) | `specs/001-order-service-lifecycle/research.md` |
| Modelo de dados | `specs/001-order-service-lifecycle/data-model.md` |
| Contrato Orders API | `specs/001-order-service-lifecycle/contracts/api-orders.md` |
| Contrato Payments API | `specs/001-order-service-lifecycle/contracts/api-payments.md` |
| Contrato Kafka + WireMock | `specs/001-order-service-lifecycle/contracts/events.md` |
| Guia de validação | `specs/001-order-service-lifecycle/quickstart.md` |
| Tasks (próximo) | `specs/001-order-service-lifecycle/tasks.md` |
