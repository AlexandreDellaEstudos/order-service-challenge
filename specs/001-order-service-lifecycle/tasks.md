# Tasks: Order Service — Ciclo de Vida Completo de Pedidos

**Input**: Design documents from `specs/001-order-service-lifecycle/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅

**TDD**: Obrigatório pela constituição (Princípio III NON-NEGOTIABLE). Testes de domínio são escritos ANTES da implementação (Red → Green → Refactor).

**Paths base**:
- Domínio: `order-service/src/main/java/com/plataforma/order/`
- Testes: `order-service/src/test/java/com/plataforma/order/`
- Migrations: `order-service/src/main/resources/db/migration/`

## Formato: `[ID] [P?] [Story?] Descrição com caminho`

- **[P]**: Pode rodar em paralelo (arquivos distintos, sem dependências pendentes)
- **[Story]**: User story correspondente (US1–US5)

---

## Phase 1: Setup — Estrutura do Repositório

**Purpose**: Criar o esqueleto do repositório conforme layout do desafio antes de qualquer código.

- [ ] T001 Criar estrutura de diretórios do repositório: `docs/`, `order-service/`, `wiremock/mappings/`, `wiremock/__files/`, `.github/workflows/`
- [ ] T002 Inicializar projeto Spring Boot 3.x com Maven em `order-service/pom.xml` (dependências: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-oauth2-resource-server, spring-kafka, resilience4j-spring-boot3, mapstruct, flyway-core, springdoc-openapi-starter-webmvc-ui, micrometer-registry-prometheus, opentelemetry-spring-boot-starter, logstash-logback-encoder, testcontainers-junit-jupiter, wiremock-testcontainers, pitest-maven)
- [ ] T003 [P] Configurar `order-service/src/main/resources/application.yml` (datasource, kafka, security, flyway, actuator, springdoc, resilience4j)
- [ ] T004 [P] Criar `order-service/Dockerfile` multi-stage (builder JDK 21 + runtime distroless/eclipse-temurin:21-jre)
- [ ] T005 [P] Criar `docker-compose.yml` na raiz com serviços: order-service (porta 8080), postgres:16 (5432), kafka (9092), wiremock (8081), jaeger (16686), prometheus (9090), grafana (3000)
- [ ] T006 [P] Criar `.github/workflows/ci.yml` com stages: build → unit-tests → integration-tests → trivy-scan
- [ ] T007 [P] Criar `wiremock/mappings/` com 10 arquivos JSON: `customers-active.json`, `customers-blocked.json`, `customers-not-found.json`, `products-available.json`, `products-unavailable.json`, `products-not-found.json`, `payments-approved.json`, `payments-rejected.json`, `payments-gateway-error.json`, `notifications.json`

**Checkpoint**: Repositório inicializado, Docker Compose funcional com `docker-compose up -d`

---

## Phase 2: Foundational — Domínio Base e Infraestrutura Core

**Purpose**: Fundação que todas as user stories dependem. NENHUMA history pode começar antes disso.

**⚠️ CRÍTICO**: Toda implementação de use case depende desta fase.

### Value Objects e Enums (domínio puro — sem framework)

- [ ] T008 [P] Criar Value Objects de identidade em `domain/model/`: `OrderId.java`, `CustomerId.java`, `ProductId.java`, `OrderItemId.java`, `PaymentId.java` (records com UUID)
- [ ] T009 [P] Criar Value Object `Money.java` em `domain/model/Money.java` (record com BigDecimal + validação de não-negativo)
- [ ] T010 [P] Criar enums `OrderStatus.java` e `PaymentStatus.java` em `domain/model/`
- [ ] T011 [P] Criar interface base `DomainEvent.java` e classe `DomainEventPublisher.java` (port) em `domain/event/` e `domain/port/`

### Port Interfaces (contratos do domínio)

- [ ] T012 [P] Criar `OrderRepository.java` em `domain/port/` (save, findById, findByCustomerId, findOpenByCustomerId)
- [ ] T013 [P] Criar `PaymentRepository.java` em `domain/port/` (save, findById, findByOrderId)
- [ ] T014 [P] Criar `CustomerPort.java` em `domain/port/` com record `CustomerValidationResult`
- [ ] T015 [P] Criar `CatalogPort.java` em `domain/port/` com record `ProductInfo`
- [ ] T016 [P] Criar `PaymentGatewayPort.java` em `domain/port/` com record `PaymentGatewayResponse`

### Domain Events

- [ ] T017 [P] Criar Domain Events em `domain/event/`: `OrderConfirmed.java`, `PaymentInitiated.java`, `PaymentApproved.java`, `PaymentRejected.java`, `OrderCancelled.java` (records implementando DomainEvent)

### Persistência e Migrations

- [ ] T018 Criar migration `order-service/src/main/resources/db/migration/V1__create_orders.sql` (tabela `orders` com id, customer_id, status, total_amount, payment_attempts, version, created_at, updated_at)
- [ ] T019 Criar migration `V2__create_order_items.sql` (tabela `order_items` com id, order_id FK, product_id, quantity, unit_price, created_at; UNIQUE order_id+product_id)
- [ ] T020 Criar migration `V3__create_payments.sql` (tabela `payments` com id, order_id FK UNIQUE, status, external_id, callback_count, created_at, updated_at)
- [ ] T021 Criar migration `V4__create_idempotency_keys.sql` (tabela `idempotency_keys` com idempotency_key PK, response_status, response_body, endpoint, created_at)

### Infraestrutura Core

- [ ] T022 Criar `CorrelationIdFilter.java` em `infrastructure/config/` (extrai ou gera X-Correlation-ID, popula MDC com correlationId, propaga em chamadas outbound)
- [ ] T023 [P] Criar `SecurityConfig.java` em `infrastructure/config/` (OAuth2 Resource Server JWT, scopes: orders:write, orders:read, payments:write, payments:read, OWASP headers)
- [ ] T024 [P] Criar `ObservabilityConfig.java` em `infrastructure/config/` (Micrometer counters de negócio: orders.created, orders.confirmed, payments.initiated, payments.approved, payments.rejected, orders.cancelled)
- [ ] T025 [P] Criar `GlobalExceptionHandler.java` em `infrastructure/adapter/http/` com `@RestControllerAdvice` convertendo exceções de domínio em RFC 7807 ProblemDetail
- [ ] T026 [P] Criar classe base de testes de integração `BaseIntegrationTest.java` em `test/infrastructure/` com `@Testcontainers` (PostgreSQL 16 + Kafka + WireMock via Testcontainers, apontando para `wiremock/mappings/`)
- [ ] T027 [P] Criar `IdempotencyService.java` em `infrastructure/` + `IdempotencyJpaRepository.java` em `infrastructure/adapter/persistence/` (verificar e persistir chaves de idempotência)

**Checkpoint**: Fundação pronta — VOs, ports, migrations, segurança e observabilidade configurados. Iniciar user stories.

---

## Phase 3: User Story 1 — Criar e Gerenciar Pedido (P1) 🎯 MVP

**Goal**: Cliente ativo cria pedido, adiciona/remove itens e consulta estado via API.

**Independent Test**: `POST /orders` com cliente ativo → `POST /orders/{id}/items` → `GET /orders/{id}` deve retornar status OPEN com itens.

### Testes de Domínio — Escritos ANTES da implementação (TDD Red phase)

- [ ] T028 [US1] Criar `OrderTest.java` em `test/domain/` com testes que FALHAM: criar Order com customerId válido deve ter status OPEN; addItem com productId novo cria item com qty=1; addItem com mesmo productId incrementa quantidade; removeItem existente remove da lista; removeItem inexistente lança ItemNotFoundException; addItem em Order não-OPEN lança InvalidOrderStateTransitionException
- [ ] T029 [US1] Criar `CreateOrderUseCaseTest.java` em `test/application/` com testes que FALHAM: cliente ativo cria pedido; cliente inexistente lança exceção; cliente bloqueado lança exceção; cliente com pedido OPEN existente lança exceção
- [ ] T030 [P] [US1] Criar `AddItemUseCaseTest.java` em `test/application/` com testes que FALHAM: produto disponível é adicionado; produto inexistente lança exceção; produto indisponível lança exceção; qty <= 0 lança exceção; pedido não-OPEN lança exceção
- [ ] T031 [P] [US1] Criar `RemoveItemUseCaseTest.java` em `test/application/` com testes que FALHAM: item existente é removido; item inexistente lança exceção; pedido não-OPEN lança exceção

### Implementação — Camada de Domínio (TDD Green phase)

- [ ] T032 [US1] Implementar `Order.java` em `domain/model/Order.java` (Aggregate Root com métodos: addItem, removeItem, validando invariantes; campo version para @Version JPA; estado inicial OPEN; lança InvalidOrderStateTransitionException para operações inválidas)
- [ ] T033 [P] [US1] Implementar `OrderItem.java` em `domain/model/OrderItem.java` (entity com id, productId, quantity, unitPrice; método incrementQuantity)
- [ ] T034 [P] [US1] Criar `InvalidOrderStateTransitionException.java` e `ItemNotFoundException.java` em `domain/model/`

### Implementação — Camada de Aplicação

- [ ] T035 [US1] Implementar `CreateOrderUseCase.java` em `application/usecase/CreateOrderUseCase.java` (valida CustomerPort, verifica findOpenByCustomerId, salva Order via OrderRepository)
- [ ] T036 [US1] Implementar `AddItemUseCase.java` em `application/usecase/AddItemUseCase.java` (valida CatalogPort, chama order.addItem, salva)
- [ ] T037 [US1] Implementar `RemoveItemUseCase.java` em `application/usecase/RemoveItemUseCase.java` (chama order.removeItem, salva)
- [ ] T038 [US1] Implementar `GetOrderUseCase.java` em `application/usecase/GetOrderUseCase.java` (findById, lança OrderNotFoundException se não encontrado)

### Implementação — Infraestrutura

- [ ] T039 [US1] Criar JPA entity `OrderJpaEntity.java` e `OrderItemJpaEntity.java` em `infrastructure/adapter/persistence/` (mapeamento para tabelas orders/order_items com @Version)
- [ ] T040 [US1] Implementar `OrderJpaRepository.java` em `infrastructure/adapter/persistence/` (Spring Data JPA implementando OrderRepository port, com query findOpenByCustomerId)
- [ ] T041 [US1] Implementar `CustomerWebClient.java` em `infrastructure/adapter/client/` (WebClient chamando WireMock /customers/{id}, implementando CustomerPort)
- [ ] T042 [US1] Implementar `CatalogWebClient.java` em `infrastructure/adapter/client/` (WebClient chamando WireMock /products/{id}, implementando CatalogPort)
- [ ] T043 [US1] Criar `OrderMapper.java` em `infrastructure/mapper/` (MapStruct @Mapper: Order → OrderResponse, OrderItem → OrderItemResponse, CreateOrderRequest → CreateOrderCommand)
- [ ] T044 [US1] Implementar `OrderController.java` em `infrastructure/adapter/http/` com endpoints: POST /api/v1/orders, POST /api/v1/orders/{id}/items, DELETE /api/v1/orders/{id}/items/{itemId}, GET /api/v1/orders/{id} (integra IdempotencyService em endpoints de mutação)
- [ ] T045 [US1] Criar `OrderControllerIntegrationTest.java` em `test/infrastructure/` estendendo BaseIntegrationTest: fluxo criar pedido + adicionar item com cliente ativo (WireMock customers-active + products-available); rejeição com cliente bloqueado; idempotência na criação

**Checkpoint US1**: `POST /orders` → `POST /orders/{id}/items` → `GET /orders/{id}` funcional. Status OPEN, itens visíveis.

---

## Phase 4: User Story 2 — Confirmar Pedido (P2)

**Goal**: Pedido OPEN com itens é confirmado, valor total calculado com preços atuais do catálogo.

**Independent Test**: Pedido OPEN com 1 item → `POST /orders/{id}/confirm` → status CONFIRMED, totalAmount preenchido, evento OrderConfirmed publicado no Kafka.

### Testes de Domínio (TDD Red phase)

- [ ] T046 [US2] Criar `OrderConfirmTest.java` em `test/domain/` com testes que FALHAM: confirm() em OPEN com itens transita para CONFIRMED; confirm() em OPEN sem itens lança exceção; confirm() em não-OPEN lança InvalidOrderStateTransitionException; confirm() define totalAmount com preços recebidos
- [ ] T047 [US2] Criar `ConfirmOrderUseCaseTest.java` em `test/application/` com testes que FALHAM: pedido com item confirma e publica OrderConfirmed; pedido sem item lança exceção

### Implementação — Domínio e Aplicação (TDD Green phase)

- [ ] T048 [US2] Adicionar método `confirm(List<Money> prices)` em `domain/model/Order.java` (valida OPEN, valida items não vazio, calcula totalAmount, transita para CONFIRMED, retorna evento OrderConfirmed)
- [ ] T049 [US2] Implementar `ConfirmOrderUseCase.java` em `application/usecase/ConfirmOrderUseCase.java` (busca Order, busca preços via CatalogPort para cada item, chama order.confirm(prices), salva, publica OrderConfirmed via DomainEventPublisher)

### Infraestrutura

- [ ] T050 [US2] Implementar `KafkaEventPublisher.java` em `infrastructure/adapter/messaging/KafkaEventPublisher.java` (implementa DomainEventPublisher, serializa Domain Events para Kafka topics orders.confirmed, orders.payment-approved, orders.payment-rejected, orders.cancelled)
- [ ] T051 [US2] Criar `OrderEventMapper.java` em `infrastructure/mapper/` (MapStruct: OrderConfirmed → OrderConfirmedEvent record para Kafka)
- [ ] T052 [US2] Adicionar endpoint `POST /api/v1/orders/{id}/confirm` em `OrderController.java` (com Idempotency-Key, trata OptimisticLockException → 409 Problem Details)
- [ ] T053 [US2] Criar `ConfirmOrderIntegrationTest.java` em `test/infrastructure/`: confirma pedido com item (WireMock products-available), verifica totalAmount, verifica evento publicado no Kafka via consumer de teste

**Checkpoint US2**: Pedido confirmado com totalAmount correto. Evento OrderConfirmed no tópico `orders.confirmed`.

---

## Phase 5: User Story 3 — Processar Pagamento e Callback (P3)

**Goal**: Pedido CONFIRMED tem pagamento iniciado, gateway responde via callback com aprovação ou rejeição (até 3 tentativas).

**Independent Test**: `POST /payments` → PAYMENT_PENDING → `POST /payments/{id}/callback` com APPROVED → pedido PAYMENT_APPROVED. Rejeição 3x → pedido CANCELLED.

### Testes de Domínio (TDD Red phase)

- [ ] T054 [US3] Criar `PaymentTest.java` em `test/domain/` com testes que FALHAM: Payment criado com status PENDING; approve() transita para APPROVED; reject() com callbackCount < 3 mantém PENDING para retry; callbackCount == 3 identifica que pedido deve ser cancelado; processCallback com mesmo externalId é idempotente (callbackCount não incrementa)
- [ ] T055 [US3] Criar `InitiatePaymentUseCaseTest.java` em `test/application/` com testes que FALHAM: pedido CONFIRMED inicia pagamento → PAYMENT_PENDING; pedido não-CONFIRMED lança exceção; pedido já com pagamento PENDING lança exceção
- [ ] T056 [US3] Criar `ProcessPaymentCallbackUseCaseTest.java` em `test/application/` com testes que FALHAM: callback APPROVED → PAYMENT_APPROVED; callback REJECTED attempt 1 → volta CONFIRMED; callback REJECTED attempt 3 → CANCELLED; callback duplicado é idempotente

### Implementação — Domínio e Aplicação (TDD Green phase)

- [ ] T057 [P] [US3] Implementar `Payment.java` em `domain/model/Payment.java` (entity com id, orderId, status PENDING, externalId, callbackCount; métodos: approve, reject com lógica de callbackCount, isCallbackIdempotent)
- [ ] T058 [US3] Implementar `InitiatePaymentUseCase.java` em `application/usecase/InitiatePaymentUseCase.java` (valida Order CONFIRMED, verifica payment existente, chama PaymentGatewayPort, cria Payment PENDING, transita Order para PAYMENT_PENDING, salva ambos)
- [ ] T059 [US3] Implementar `ProcessPaymentCallbackUseCase.java` em `application/usecase/ProcessPaymentCallbackUseCase.java` (busca Payment, verifica idempotência via callbackCount+externalId, aplica approve/reject, transita Order, publica PaymentApproved/PaymentRejected/OrderCancelled)
- [ ] T060 [P] [US3] Implementar `GetPaymentUseCase.java` em `application/usecase/GetPaymentUseCase.java`

### Infraestrutura

- [ ] T061 [US3] Criar JPA entity `PaymentJpaEntity.java` em `infrastructure/adapter/persistence/`
- [ ] T062 [US3] Implementar `PaymentJpaRepository.java` em `infrastructure/adapter/persistence/` (implementa PaymentRepository port, com findByOrderId)
- [ ] T063 [US3] Implementar `PaymentGatewayWebClient.java` em `infrastructure/adapter/client/` (implementa PaymentGatewayPort, WebClient para WireMock /payments, Resilience4j CircuitBreaker + Retry com backoff exponencial + jitter maxAttempts=3 delay=250ms apenas para 5xx)
- [ ] T064 [P] [US3] Criar `PaymentMapper.java` em `infrastructure/mapper/` (MapStruct: Payment → PaymentResponse, InitiatePaymentRequest → InitiatePaymentCommand, PaymentCallbackRequest → ProcessCallbackCommand)
- [ ] T065 [P] [US3] Criar `OrderEventMapper.java` atualizações para PaymentApprovedEvent, PaymentRejectedEvent em `infrastructure/mapper/OrderEventMapper.java`
- [ ] T066 [US3] Implementar `PaymentController.java` em `infrastructure/adapter/http/PaymentController.java` (POST /api/v1/payments, GET /api/v1/payments/{id}, POST /api/v1/payments/{id}/callback — com Idempotency-Key nos endpoints de mutação; trata gateway 503 → 503 com Retry-After: 30)
- [ ] T067 [US3] Criar `PaymentIntegrationTest.java` em `test/infrastructure/`: fluxo completo aprovação (WireMock payments-approved); fluxo 3 rejeições → CANCELLED (WireMock payments-rejected); gateway instável com circuit breaker (WireMock payments-gateway-error); idempotência do callback

**Checkpoint US3**: Fluxo completo de pagamento funcional: aprovação, rejeição + retry, cancelamento automático após 3 rejeições.

---

## Phase 6: User Story 4 — Cancelar Pedido (P4)

**Goal**: Pedido pode ser cancelado nos estados OPEN, CONFIRMED e PAYMENT_PENDING. Após PAYMENT_APPROVED, cancelamento é rejeitado.

**Independent Test**: `DELETE /orders/{id}` com pedido OPEN → status CANCELLED. `DELETE /orders/{id}` com pedido PAYMENT_APPROVED → 409 Conflict.

### Testes de Domínio (TDD Red phase)

- [ ] T068 [US4] Criar `OrderCancelTest.java` em `test/domain/` com testes que FALHAM: cancel() em OPEN → CANCELLED; cancel() em CONFIRMED → CANCELLED; cancel() em PAYMENT_PENDING → CANCELLED; cancel() em PAYMENT_APPROVED lança InvalidOrderStateTransitionException; cancel() em CANCELLED lança InvalidOrderStateTransitionException
- [ ] T069 [US4] Criar `CancelOrderUseCaseTest.java` em `test/application/` com testes que FALHAM: cancelamento manual publica OrderCancelled(reason=MANUAL); pedido terminal rejeita cancelamento

### Implementação (TDD Green phase)

- [ ] T070 [US4] Adicionar método `cancel()` em `domain/model/Order.java` (válido para OPEN, CONFIRMED, PAYMENT_PENDING; lança exceção para PAYMENT_APPROVED e CANCELLED; retorna evento OrderCancelled(reason=MANUAL))
- [ ] T071 [US4] Implementar `CancelOrderUseCase.java` em `application/usecase/CancelOrderUseCase.java` (chama order.cancel(), salva, publica OrderCancelled via DomainEventPublisher)
- [ ] T072 [US4] Adicionar endpoint `DELETE /api/v1/orders/{id}` em `OrderController.java` (com Idempotency-Key)
- [ ] T073 [US4] Criar `CancelOrderIntegrationTest.java` em `test/infrastructure/`: cancelar OPEN → CANCELLED; cancelar CONFIRMED → CANCELLED; cancelar PAYMENT_PENDING → CANCELLED; cancelar PAYMENT_APPROVED → 409; evento OrderCancelled no tópico `orders.cancelled`

**Checkpoint US4**: Cancelamento funcional em todos os estados válidos. Estados terminais protegidos.

---

## Phase 7: User Story 5 — Consultar Pedidos (P5)

**Goal**: Listar pedidos de um cliente via query param.

**Independent Test**: `GET /orders?customerId={id}` retorna todos os pedidos do cliente, incluindo os de históricos de outros testes.

### Testes e Implementação

- [ ] T074 [US5] Criar `ListOrdersByCustomerUseCaseTest.java` em `test/application/` com testes que FALHAM: retorna lista de pedidos do cliente; retorna lista vazia se nenhum pedido
- [ ] T075 [US5] Implementar `ListOrdersByCustomerUseCase.java` em `application/usecase/ListOrdersByCustomerUseCase.java` (findByCustomerId, retorna lista vazia se não encontrar)
- [ ] T076 [US5] Adicionar endpoint `GET /api/v1/orders?customerId={id}` em `OrderController.java`
- [ ] T077 [US5] Criar `ListOrdersIntegrationTest.java` em `test/infrastructure/`: listagem retorna múltiplos pedidos de um cliente; listagem de cliente sem pedidos retorna lista vazia

**Checkpoint US5**: Todas as user stories implementadas e testadas independentemente.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Qualidade, documentação e entrega final.

- [ ] T078 [P] Adicionar anotações OpenAPI 3.1 (`@Operation`, `@ApiResponse`, `@Parameter`) em `OrderController.java` e `PaymentController.java` para Swagger UI completo em `/swagger-ui.html`
- [ ] T079 [P] Configurar headers de segurança OWASP em `SecurityConfig.java`: HSTS, X-Content-Type-Options, X-Frame-Options, Content-Security-Policy
- [ ] T080 [P] Executar Pitest em `order-service/` e corrigir mutantes sobreviventes até MSI ≥ 75% nos testes do módulo `domain/`
- [ ] T081 Verificar cobertura de linhas do módulo `domain/` ≥ 80% via JaCoCo (configurar plugin no `pom.xml` com failOnViolation)
- [ ] T082 [P] Criar `docs/architecture.md` com: identificação dos Bounded Contexts, responsabilidades de cada serviço, Mapa de Contextos (Order/Billing/Catalog/Customer), decisões de design (ADRs D-001 a D-010 do research.md), justificativa do optimistic locking
- [ ] T083 [P] Criar `README.md` na raiz com: pré-requisitos, `docker-compose up -d`, URLs dos serviços, como executar os testes, link para Swagger UI
- [ ] T084 Executar cenários do `quickstart.md` end-to-end contra o ambiente Docker Compose local e validar todos os 4 cenários (fluxo aprovação, 3 rejeições, idempotência, concorrência)

---

## Dependencies & Execution Order

### Dependências entre Fases

```
Phase 1 (Setup)
    └──► Phase 2 (Foundational) ──BLOQUEIA──► Phase 3 (US1)
                                               └──► Phase 4 (US2)  [depende de US1: Order.confirm()]
                                               └──► Phase 5 (US3)  [depende de US2: Order status CONFIRMED]
                                               └──► Phase 6 (US4)  [depende de domínio Order pronto]
                                               └──► Phase 7 (US5)  [independente]
                                               └──► Phase 8 (Polish) [depende de todas]
```

### Dependências entre User Stories

- **US1 (P1)**: Após Phase 2 — sem dependências entre stories
- **US2 (P2)**: Após US1 — Order.confirm() precisa de Order.addItem() funcionando
- **US3 (P3)**: Após US2 — InitiatePayment precisa de Order em estado CONFIRMED
- **US4 (P4)**: Após Phase 2 — Order.cancel() é independente de US2/US3
- **US5 (P5)**: Após Phase 2 — consulta é totalmente independente

### Dentro de Cada User Story

```
Testes (Red) → Domínio (Green) → Aplicação → Infraestrutura → Teste de Integração
```

---

## Parallel Opportunities

### Phase 2 — Paralelos após T007 (WireMock)

```
T008 (VOs)     ─┐
T009 (Enums)   ─┤
T010 (Events)  ─┤─► T018 (migrations) ─► T022 (CorrelationFilter)
T011 (Ports)   ─┤                        T023 (SecurityConfig)
T012-T016      ─┘                        T024 (ObservabilityConfig)
T017 (Events)                            T025 (ExceptionHandler)
                                         T026 (BaseIntegrationTest)
                                         T027 (IdempotencyService)
```

### Phase 3 (US1) — Paralelos após T032 (Order aggregate)

```
T033 (OrderItem)  ─┐
T034 (Exceptions) ─┤─► T035-T038 (use cases) ─► T039-T044 (infra) ─► T045 (integration)
T028-T031 (tests) ─┘
```

---

## Implementation Strategy

### MVP — Apenas User Story 1

1. Completar Phase 1 (Setup)
2. Completar Phase 2 (Foundational) — crítico
3. Completar Phase 3 (US1): `POST /orders`, `POST /orders/{id}/items`, `DELETE /orders/{id}/items/{itemId}`, `GET /orders/{id}`
4. **PARAR e VALIDAR**: curl nos endpoints, verificar logs JSON, verificar estado no banco

### Entrega Incremental (ordem recomendada dado o prazo)

| Dia | Foco | Entregável |
|---|---|---|
| 10–11/06 | Phase 1 + Phase 2 | Estrutura + domínio base + migrations + infra core |
| 12/06 | Phase 3 (US1) | CRUD de pedidos + itens funcionando |
| 13/06 | Phase 4 (US2) | Confirmação + Kafka |
| 14–15/06 | Phase 5 (US3) | Pagamento + callback + Circuit Breaker |
| 16/06 | Phase 6 (US4) + Phase 7 (US5) | Cancelamento + listagem |
| 17–18/06 | Phase 8 (Polish) | Docs, OpenAPI, Pitest, CI verde |
| 19/06 | Buffer + entrega | Validação final + link GitHub no LMS |

---

## Notes

- `[P]` = arquivos distintos, sem dependências pendentes — podem ser iniciados em paralelo
- Testes de domínio são obrigatórios e devem FALHAR antes da implementação (Red-Green-Refactor)
- `BaseIntegrationTest` (T026) deve ser criado antes de qualquer teste de integração
- O `docker-compose.yml` (T005) com WireMock deve estar funcional antes dos testes de integração
- Pitest (T080) e JaCoCo (T081) são gates de CI — rodar localmente antes do push final
- Commit após cada Checkpoint de user story
