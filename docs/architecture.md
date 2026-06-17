# Arquitetura — Plataforma de Pedidos (E-commerce Order Platform)

**Versão**: 1.0.0 | **Data**: 2026-06-10 | **Prazo de entrega**: 2026-06-19

---

## Visão Geral

Uma startup de marketplace precisa de um backend confiável para processar pedidos de ponta a ponta: desde a criação até a confirmação do pagamento. O projeto resolve seis problemas reais identificados pelo time de produto:

| Problema | Solução implementada |
|---|---|
| Pedidos criados sem itens nunca finalizados | Regra: pedido só avança para confirmação com ≥ 1 item |
| Pagamento processado em duplicidade | Idempotência obrigatória via `Idempotency-Key` em todos os endpoints de mutação |
| Sem rastreabilidade de estado | Máquina de estados explícita com 5 estados e transições validadas |
| Pedidos de clientes inválidos chegando ao pagamento | Validação de cliente ativo no momento da criação do pedido |
| Gateway instável derruba a plataforma | Circuit Breaker + Retry com backoff no cliente do gateway |
| Dois processos modificando o mesmo pedido | Optimistic Locking com `@Version` — conflito retorna HTTP 409 |

---

## Escopo: O que será implementado

Apenas o **`order-service`** é implementado. Todos os demais serviços da plataforma são **simulados via WireMock**.

```
┌─────────────────────────────────────────────────────────────┐
│                    PLATAFORMA DE PEDIDOS                    │
│                                                             │
│  ┌──────────────────┐    ┌───────────────────────────────┐  │
│  │   order-service  │    │         WireMock              │  │
│  │   ✅ IMPLEMENTADO│───►│  customer-service  (simulado) │  │
│  │                  │    │  catalog-service   (simulado) │  │
│  │                  │───►│  payment-gateway   (simulado) │  │
│  │                  │    │  notification-svc  (simulado) │  │
│  └──────────────────┘    └───────────────────────────────┘  │
│           │                                                  │
│           │ publica eventos                                  │
│           ▼                                                  │
│       ┌───────┐                                              │
│       │ Kafka │ ◄── downstream consumers NÃO implementados  │
│       └───────┘                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Bounded Contexts e Responsabilidades

A plataforma foi decomposta em 5 contextos de domínio identificados via Event Storming:

### 1. Order Context — `order-service` ✅ implementado

**Responsabilidade**: Gerenciar o ciclo de vida completo de pedidos.

- Criar, confirmar e cancelar pedidos
- Gerenciar itens do pedido
- Orquestrar o fluxo de pagamento
- Publicar eventos de domínio para consumidores downstream

**Aggregate Root**: `Order`

---

### 2. Customer Context — `customer-service` 🔵 simulado via WireMock

**Responsabilidade**: Cadastro e status de clientes.

- Validar se cliente existe e está ativo
- O `order-service` chama este serviço ao criar um pedido

**Contrato consumido pelo order-service**:
```
GET /customers/{customerId}
→ 200 OK   { "id": "...", "status": "ACTIVE" }
→ 422      { "status": "BLOCKED" }
→ 404      cliente não encontrado
```

---

### 3. Catalog Context — `catalog-service` 🔵 simulado via WireMock

**Responsabilidade**: Catálogo de produtos com disponibilidade e preços.

- Verificar se produto existe e está disponível (ao adicionar item)
- Retornar preço atual do produto (ao confirmar pedido — preço calculado no momento da confirmação)

**Contrato consumido pelo order-service**:
```
GET /products/{productId}
→ 200 OK   { "id": "...", "name": "...", "price": 49.90, "available": true }
→ 422      produto indisponível
→ 404      produto não encontrado
```

---

### 4. Payment Context — `payment-gateway` 🔵 simulado via WireMock

**Responsabilidade**: Processamento de cobranças.

- Receber solicitação de cobrança do `order-service`
- Responder de forma assíncrona via webhook (callback) com aprovação ou rejeição
- O gateway pode estar instável (503) — o `order-service` tem Circuit Breaker para isso

**Contrato consumido pelo order-service**:
```
POST /payments
→ 200 OK   { "paymentId": "...", "status": "PROCESSING" }
→ 503      gateway instável

Webhook enviado para order-service:
POST /api/v1/payments/{paymentId}/callback
{ "status": "APPROVED" | "REJECTED", "externalId": "..." }
```

---

### 5. Notification Context — `notification-service` 🔵 downstream Kafka (não chamado diretamente)

**Responsabilidade**: Notificar clientes sobre mudanças de estado.

- **O `order-service` NÃO chama este serviço diretamente**
- Ele consome eventos Kafka publicados pelo `order-service`
- Mapeamento WireMock existe apenas para documentar o contrato

---

## Arquitetura do order-service — Clean Architecture

O serviço segue três camadas com dependências estritas: **nunca** do interior para o exterior.

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE                           │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐  │
│  │ OrderController│  │ OrderJpaRepo   │  │CustomerClient│  │
│  │PaymentController  │PaymentJpaRepo  │  │CatalogClient │  │
│  │(REST adapters) │  │(persistence)   │  │PaymentClient │  │
│  └───────┬────────┘  └───────┬────────┘  └──────┬───────┘  │
│          │                   │                   │          │
├──────────┼───────────────────┼───────────────────┼──────────┤
│          │        APPLICATION│                   │          │
│          ▼                   ▼                   ▼          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  CreateOrderUseCase    ConfirmOrderUseCase            │  │
│  │  AddItemUseCase        InitiatePaymentUseCase         │  │
│  │  RemoveItemUseCase     ProcessPaymentCallbackUseCase  │  │
│  │  CancelOrderUseCase    GetOrderUseCase                │  │
│  │  ListOrdersByCustomerUseCase   GetPaymentUseCase      │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                              │
├──────────────────────────────┼──────────────────────────────┤
│                              ▼         DOMAIN               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Order (Aggregate Root)    OrderItem                 │   │
│  │  Payment                   Money, OrderId, ...       │   │
│  │  OrderStatus, PaymentStatus                          │   │
│  │  OrderConfirmed, PaymentApproved, OrderCancelled...  │   │
│  │                                                      │   │
│  │  Ports: OrderRepository, CustomerPort, CatalogPort   │   │
│  │         PaymentGatewayPort, DomainEventPublisher     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Regra de ouro**: O código em `domain/` não importa nada de Spring, JPA ou qualquer framework. É Java puro.

---

## Estrutura de Pacotes

```
order-service/src/main/java/com/plataforma/order/
│
├── domain/
│   ├── model/
│   │   ├── Order.java               ← Aggregate Root
│   │   ├── OrderItem.java           ← Entity (dentro do aggregate)
│   │   ├── Payment.java             ← Entity (identidade própria, não é Aggregate Root)
│   │   ├── OrderStatus.java         ← OPEN, CONFIRMED, PAYMENT_PENDING, PAYMENT_APPROVED, CANCELLED
│   │   ├── PaymentStatus.java       ← PENDING, APPROVED, REJECTED
│   │   ├── Money.java               ← Value Object (BigDecimal + validação)
│   │   ├── OrderId.java             ← Value Object (UUID)
│   │   ├── CustomerId.java          ← Value Object (UUID)
│   │   ├── ProductId.java           ← Value Object (UUID)
│   │   └── PaymentId.java           ← Value Object (UUID)
│   ├── event/
│   │   ├── OrderConfirmed.java
│   │   ├── PaymentApproved.java
│   │   ├── PaymentRejected.java
│   │   └── OrderCancelled.java
│   └── port/
│       ├── OrderRepository.java
│       ├── PaymentRepository.java
│       ├── CustomerPort.java
│       ├── CatalogPort.java
│       ├── PaymentGatewayPort.java
│       └── DomainEventPublisher.java
│
├── application/
│   └── usecase/
│       ├── CreateOrderUseCase.java
│       ├── AddItemUseCase.java
│       ├── RemoveItemUseCase.java
│       ├── ConfirmOrderUseCase.java
│       ├── CancelOrderUseCase.java
│       ├── InitiatePaymentUseCase.java
│       ├── ProcessPaymentCallbackUseCase.java
│       ├── GetOrderUseCase.java
│       ├── ListOrdersByCustomerUseCase.java
│       └── GetPaymentUseCase.java
│
└── infrastructure/
    ├── adapter/
    │   ├── http/
    │   │   ├── OrderController.java      ← 6 endpoints de pedido
    │   │   ├── PaymentController.java    ← 3 endpoints de pagamento
    │   │   └── GlobalExceptionHandler.java ← RFC 7807 ProblemDetail
    │   ├── persistence/
    │   │   ├── OrderJpaEntity.java
    │   │   ├── OrderItemJpaEntity.java
    │   │   ├── PaymentJpaEntity.java
    │   │   ├── OrderJpaRepository.java
    │   │   ├── PaymentJpaRepository.java
    │   │   └── IdempotencyJpaRepository.java
    │   ├── client/
    │   │   ├── CustomerWebClient.java    ← chama WireMock /customers
    │   │   ├── CatalogWebClient.java     ← chama WireMock /products
    │   │   └── PaymentGatewayWebClient.java ← chama WireMock /payments + Circuit Breaker
    │   └── messaging/
    │       └── KafkaEventPublisher.java  ← publica Domain Events no Kafka
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── KafkaConfig.java
    │   ├── WebClientConfig.java
    │   ├── ResilienceConfig.java
    │   ├── ObservabilityConfig.java
    │   └── CorrelationIdFilter.java
    └── mapper/
        ├── OrderMapper.java              ← MapStruct: Record ↔ Domain
        ├── PaymentMapper.java            ← MapStruct: Record ↔ Domain
        └── OrderEventMapper.java         ← MapStruct: Domain Event → Kafka Record
```

---

## Modelo de Domínio

### Entidades Principais

**Order** — Aggregate Root

```
Order {
  id: UUID
  customerId: UUID
  status: OrderStatus
  items: List<OrderItem>
  totalAmount: BigDecimal   ← preenchido apenas na confirmação
  paymentAttempts: int      ← 0 a 3; ao atingir 3 rejeições → CANCELLED automático
  version: long             ← @Version para Optimistic Locking
}
```

**OrderItem**

```
OrderItem {
  id: UUID
  productId: UUID
  quantity: int             ← sempre > 0; mesmo produto → incrementa, não duplica
  unitPrice: BigDecimal     ← preenchido na confirmação com preço atual do catálogo
}
```

**Payment**

```
Payment {
  id: UUID
  orderId: UUID             ← referência ao pedido (1 pedido = 1 pagamento)
  status: PaymentStatus
  externalId: String        ← ID retornado pelo gateway
  callbackCount: int        ← contador para idempotência do callback
}
```

---

## Máquina de Estados do Pedido

```
                    POST /orders
                        │
                        ▼
                      OPEN ──────────────────────────────────────┐
                        │                                        │
         POST /orders/{id}/confirm                  DELETE /orders/{id}
                        │                                        │
                        ▼                                        │
                   CONFIRMED ───────────────────────────────────►│
                        │                                        │
          POST /payments (InitiatePayment)          DELETE /orders/{id}
                        │                                        │
                        ▼                                        │
               PAYMENT_PENDING ────────────────────────────────►│
                   │       │                                     │
          callback │       │ callback                            │
          APPROVED │       │ REJECTED                            ▼
                   │       │                                CANCELLED
                   │       ├─ attempts < 3 → volta para CONFIRMED (terminal)
                   │       └─ attempts = 3 → CANCELLED ──────────►
                   ▼
          PAYMENT_APPROVED
             (terminal)
```

**Transições válidas**:

| De | Para | Gatilho |
|---|---|---|
| OPEN | CONFIRMED | POST /orders/{id}/confirm |
| OPEN | CANCELLED | DELETE /orders/{id} |
| CONFIRMED | PAYMENT_PENDING | POST /payments |
| CONFIRMED | CANCELLED | DELETE /orders/{id} |
| PAYMENT_PENDING | PAYMENT_APPROVED | callback APPROVED |
| PAYMENT_PENDING | CONFIRMED | callback REJECTED (attempts < 3) |
| PAYMENT_PENDING | CANCELLED | callback REJECTED (attempts = 3) |
| PAYMENT_PENDING | CANCELLED | DELETE /orders/{id} |

Qualquer outra tentativa de transição → `InvalidOrderStateTransitionException` → HTTP 409

---

## API — Endpoints Obrigatórios

### Pedidos — `/api/v1/orders`

| Método | Endpoint | Descrição | Scope |
|---|---|---|---|
| `POST` | `/api/v1/orders` | Cria pedido para cliente ativo | `orders:write` |
| `GET` | `/api/v1/orders/{orderId}` | Consulta detalhes do pedido | `orders:read` |
| `GET` | `/api/v1/orders?customerId={id}` | Lista pedidos de um cliente | `orders:read` |
| `POST` | `/api/v1/orders/{orderId}/items` | Adiciona item (mesmo produto → incrementa qty) | `orders:write` |
| `DELETE` | `/api/v1/orders/{orderId}/items/{itemId}` | Remove item do pedido | `orders:write` |
| `POST` | `/api/v1/orders/{orderId}/confirm` | Confirma pedido e calcula valor total | `orders:write` |
| `DELETE` | `/api/v1/orders/{orderId}` | Cancela pedido (antes da aprovação) | `orders:write` |

### Pagamentos — `/api/v1/payments`

| Método | Endpoint | Descrição | Scope |
|---|---|---|---|
| `POST` | `/api/v1/payments` | Inicia pagamento de pedido CONFIRMED | `payments:write` |
| `GET` | `/api/v1/payments/{paymentId}` | Consulta status do pagamento | `payments:read` |
| `POST` | `/api/v1/payments/{paymentId}/callback` | Recebe resultado do gateway (webhook) | `payments:write` |

**Todos os endpoints de mutação** aceitam o header `Idempotency-Key: <uuid>`.

**Erros** seguem RFC 7807 ProblemDetail:
```json
{
  "type": "https://api.plataforma.com/problems/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "Order 'abc-123' not found.",
  "instance": "/api/v1/orders/abc-123"
}
```

---

## Regras de Negócio Implementadas

### Clientes
- Pedido só é criado para cliente **ativo** — validado via chamada HTTP ao WireMock
- Cliente bloqueado ou inexistente → rejeição imediata com erro apropriado
- Um cliente só pode ter **um pedido OPEN** por vez

### Itens
- Item só é adicionado se o produto **existe e está disponível** (verificado via WireMock no momento da adição)
- Quantidade deve ser **> 0**
- Mesmo produto adicionado mais de uma vez → **incrementa a quantidade**, não duplica
- Remoção de item inexistente → erro 404

### Confirmação
- Pedido precisa ter **ao menos 1 item**
- O **valor total é calculado no momento da confirmação** com o preço atual do catálogo — não o preço de quando o item foi adicionado
- Após confirmação, itens **não podem ser adicionados nem removidos**

### Pagamento
- Só pode ser iniciado para pedido **CONFIRMED**
- Cada pedido tem **no máximo 1 pagamento ativo** (idempotência na iniciação)
- O gateway pode rejeitar o pagamento — o sistema permite **até 3 tentativas**
- Na **3ª rejeição**, o pedido é automaticamente **CANCELLED**
- O callback é **idempotente**: processar o mesmo evento múltiplas vezes não altera o estado

### Cancelamento
- Permitido nos estados: **OPEN**, **CONFIRMED**, **PAYMENT_PENDING**
- **Proibido** após **PAYMENT_APPROVED**
- Pedido **CANCELLED** não pode ser modificado de forma alguma

---

## Idempotência

Todos os endpoints de mutação armazenam resultado na tabela `idempotency_keys`:

```
POST /orders           + Idempotency-Key: K → cria pedido → salva (K, 201, {body})
POST /orders           + Idempotency-Key: K → retorna (201, {body}) sem criar novo pedido
```

Isso garante segurança em retentativas de rede sem efeitos colaterais duplicados.

---

## Controle de Concorrência

A entidade `Order` tem um campo `version` gerenciado pelo JPA (`@Version`):

```
Requisição A lê Order(version=1) → modifica → salva(version=2) ✅
Requisição B lê Order(version=1) → modifica → salva(version=2) ❌ OptimisticLockException
                                                                  → HTTP 409 Conflict
```

Isso evita que duas requisições simultâneas corrompam o estado do pedido.

---

## Resiliência

### Circuit Breaker no Payment Gateway

O `PaymentGatewayWebClient` tem um Circuit Breaker (Resilience4j) para proteger o sistema quando o gateway estiver instável:

```
         chamadas normais
CLOSED ─────────────────────────────────────────────────►
         ↑                   falhas ≥ 50% em 10 chamadas
         │                              ↓
HALF-OPEN ◄─── aguarda 30s ─── OPEN (corta chamadas)
  (testa 1 chamada)
```

Configuração:
- `failureRateThreshold`: 50%
- `waitDurationInOpenState`: 30s
- Fallback: HTTP 503 com header `Retry-After: 30`

### Retry com Backoff

Antes do Circuit Breaker abrir, o WebClient tenta até 3 vezes com backoff exponencial + jitter (250ms base). Somente para erros 5xx — erros 4xx não são retentados.

---

## Eventos de Domínio (Kafka)

O `order-service` publica eventos para cada transição significativa. **Ele não sabe quem consome** — isso é responsabilidade dos serviços downstream.

| Evento | Tópico Kafka | Gatilho |
|---|---|---|
| `OrderConfirmed` | `orders.confirmed` | Pedido confirmado |
| `PaymentApproved` | `orders.payment-approved` | Callback aprovado |
| `PaymentRejected` | `orders.payment-rejected` | Callback rejeitado (cada tentativa) |
| `OrderCancelled` | `orders.cancelled` | Cancelamento manual ou 3ª rejeição |

**Consumers downstream (não implementados)**:
- `notification-service` → notifica o cliente
- `fulfillment-service` → inicia separação após aprovação
- `inventory-service` → reserva/libera estoque

---

## Segurança

**Autenticação**: JWT Bearer Token (OAuth2 Resource Server). O token é validado em toda requisição.

**Autorização por escopo**:

| Escopo | Operações protegidas |
|---|---|
| `orders:write` | POST /orders, POST items, DELETE items, POST confirm, DELETE order |
| `orders:read` | GET /orders/{id}, GET /orders?customerId |
| `payments:write` | POST /payments, POST callback |
| `payments:read` | GET /payments/{id} |

**Headers de segurança** (OWASP):
- `Strict-Transport-Security`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy`

**Erros** nunca expõem stack traces — apenas ProblemDetail padronizado.

---

## Observabilidade

### Logs Estruturados (JSON)

Todo log carrega estes campos obrigatórios via MDC:

```json
{
  "@timestamp": "2026-06-10T10:00:00Z",
  "level": "INFO",
  "service": "order-service",
  "correlationId": "uuid-extraído-do-header-X-Correlation-ID",
  "traceId": "otel-trace-id",
  "spanId": "otel-span-id",
  "orderId": "uuid-quando-disponível",
  "message": "Order confirmed"
}
```

### Métricas (Prometheus + Grafana)

Disponível em `/actuator/prometheus`. Métricas de negócio instrumentadas:

| Métrica | Tipo | Descrição |
|---|---|---|
| `orders.created.total` | Counter | Pedidos criados |
| `orders.confirmed.total` | Counter | Pedidos confirmados |
| `payments.initiated.total` | Counter | Pagamentos iniciados |
| `payments.approved.total` | Counter | Pagamentos aprovados |
| `payments.rejected.total` | Counter | Pagamentos rejeitados |
| `orders.cancelled.total` | Counter | Pedidos cancelados (por reason: MANUAL, MAX_REJECTIONS) |

### Tracing Distribuído (OpenTelemetry → Jaeger)

Todos os spans de entrada HTTP, saída HTTP (WebClient) e Kafka são rastreados automaticamente. UI disponível em `http://localhost:16686`.

---

## Banco de Dados

**PostgreSQL 16** com migrations versionadas via **Flyway**.

```sql
-- orders
id UUID PK | customer_id UUID | status VARCHAR(30) | total_amount DECIMAL(19,2)
payment_attempts INT | version BIGINT | created_at TIMESTAMPTZ | updated_at TIMESTAMPTZ

-- order_items
id UUID PK | order_id UUID FK | product_id UUID | quantity INT | unit_price DECIMAL(19,2)
UNIQUE(order_id, product_id)

-- payments
id UUID PK | order_id UUID FK UNIQUE | status VARCHAR(30) | external_id VARCHAR(255)
callback_count INT | created_at TIMESTAMPTZ | updated_at TIMESTAMPTZ

-- idempotency_keys
idempotency_key VARCHAR(255) PK | response_status INT | response_body TEXT
endpoint VARCHAR(255) | created_at TIMESTAMPTZ
```

---

## Testes

A suíte de testes segue o princípio **TDD Red→Green→Refactor** obrigatório pela constituição:

| Tipo | Ferramenta | Cobertura alvo | O que testa |
|---|---|---|---|
| Unitários — domínio | JUnit 5 | ≥ 90% linhas | Order aggregate, state machine, invariantes |
| Unitários — use cases | JUnit 5 | — | Fluxos de aplicação com ports mockados |
| Arquitetura | ArchUnit | 100% das regras | Dependency Rule Clean Architecture (domain sem Spring/JPA, app sem infra, etc.) |
| Integração | Testcontainers + WireMock | — | API completa com PostgreSQL real + WireMock |
| Mutation | Pitest | MSI ≥ 90% | Robustez lógica do módulo domain |
| Carga | K6 | p95 leitura < 300ms; p95 escrita < 500ms | Perfis nominal (100 rps) e peak (1.000 rps) |

**WireMock nos testes**: os mesmos arquivos de `wiremock/mappings/` são carregados via Testcontainers — sem duplicação de mapeamentos.

---

## Infraestrutura Local (Docker Compose)

```
docker-compose up -d
```

| Serviço | Imagem | Porta | Finalidade |
|---|---|---|---|
| `order-service` | build local | 8080 | API principal |
| `postgres` | postgres:16 | 5432 | Banco de dados |
| `kafka` | confluentinc/cp-kafka | 9092 | Mensageria |
| `wiremock` | wiremock/wiremock | 8081 | Simula serviços externos |
| `jaeger` | jaegertracing/all-in-one | 16686 | Tracing UI |
| `prometheus` | prom/prometheus | 9090 | Coleta de métricas |
| `grafana` | grafana/grafana | 3000 | Dashboard de métricas |

**URLs**:
- Swagger UI: http://localhost:8080/swagger-ui.html
- Jaeger: http://localhost:16686
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090

---

## CI/CD — GitHub Actions

```
push / pull_request
        │
        ▼
   ┌─── build ────────────────────────────────────┐
   │  mvn compile                                 │
   └──────────────────────────────────────────────┘
        │
        ▼
   ┌─── unit-tests ───────────────────────────────┐
   │  mvn test (JUnit 5 + Pitest + JaCoCo)        │
   │  Gate: cobertura ≥ 90%, MSI ≥ 90%            │
   │  ArchUnit: Dependency Rule — falha em violação│
   └──────────────────────────────────────────────┘
        │
        ▼
   ┌─── integration-tests ────────────────────────┐
   │  mvn verify -P integration-tests             │
   │  (Testcontainers + WireMock)                 │
   └──────────────────────────────────────────────┘
        │
        ▼
   ┌─── k6-load-test ─────────────────────────────┐
   │  k6 run k6/scripts/nominal.js                │
   │  Gate: p95 < 300ms leitura; erro < 1%        │
   └──────────────────────────────────────────────┘
        │
        ▼
   ┌─── trivy-scan ───────────────────────────────┐
   │  trivy image order-service:latest            │
   │  Falha em CVE CRITICAL                       │
   └──────────────────────────────────────────────┘
```

---

## Stack de Tecnologias

| Camada | Tecnologia | Justificativa |
|---|---|---|
| Linguagem | Java 21 | LTS mais recente; Records, Pattern Matching, Virtual Threads (Aula 1) |
| Framework | Spring Boot 3.x | Ecossistema maduro para todos os requisitos (Aula 2) |
| Persistência | Spring Data JPA + Hibernate | ORM com suporte a @Version para optimistic locking (Aula 3) |
| Banco | PostgreSQL 16 | ACID para dados transacionais (Aula 3) |
| Migrations | Flyway | Versionamento de schema (Aula 3) |
| Mensageria | Apache Kafka + Spring Kafka | Eventos de domínio assíncronos (Aula 5) |
| Resiliência | Resilience4j | Circuit Breaker + Retry no gateway (Aulas 2 e 5) |
| DTOs | Java Records | Imutáveis, concisos, sem getters/setters (Aulas 1 e 2) |
| Mapeamento | MapStruct | Geração de código de mapeamento em compile-time |
| Segurança | Spring Security + OAuth2 | JWT Resource Server + scopes (Aula 8) |
| Documentação | SpringDoc OpenAPI 3.1 | Swagger UI automático (Aula 2) |
| Logs | logstash-logback-encoder | JSON estruturado com MDC (Aula 7) |
| Métricas | Micrometer + Prometheus | Padrão de mercado, integra com Grafana (Aula 7) |
| Tracing | OpenTelemetry + Jaeger | Rastreamento distribuído (Aula 7) |
| Testes integração | Testcontainers | PostgreSQL + WireMock reais no CI (Aula 4) |
| Testes de arquitetura | ArchUnit | Valida Dependency Rule Clean Architecture em CI |
| Mutation testing | Pitest | MSI ≥ 90% no domínio (Aula 4) |
| Testes de carga | K6 | Perfis nominal (100 rps) e peak (1.000 rps); gate no CI |
| Containerização | Docker + Docker Compose | Ambiente reproduzível (Aula 6) |
| CI/CD | GitHub Actions + Trivy | Build → testes → k6 → scan de vulnerabilidades (Aula 6) |

---

## Decisões de Design (ADRs)

### ADR-001: Order como único Aggregate Root

**Decisão**: `Order` é o Aggregate Root. `OrderItem` é entity interna ao aggregate.

**Motivação**: Todas as invariantes de negócio (confirmação, cancelamento, limite de tentativas) estão concentradas no `Order`. Isso garante consistência em uma única transação.

### ADR-002: Payment como Entity com identidade própria (não é Aggregate Root)

**Decisão**: `Payment` tem seu próprio `PaymentRepository` e não é composto dentro de `Order`, mas **não é Aggregate Root** — seu ciclo de vida é acionado exclusivamente pelo `Order`.

**Motivação**: O endpoint `GET /payments/{id}` precisa carregar um Payment diretamente sem passar pelo Order, o que justifica o repositório próprio. Porém, toda transição de estado do Payment (approve, reject) é iniciada pelo Order, portanto não faz sentido elevá-lo a Aggregate Root — isso quebraria a fronteira de consistência do `Order`.

### ADR-003: Optimistic Locking em vez de Pessimistic

**Decisão**: `@Version` no `Order` — rejeita com 409 em caso de conflito.

**Motivação**: A constituição define optimistic locking como padrão. Conflitos são raros num e-commerce normal. Pessimistic locking geraria contenção desnecessária e seria usado apenas se análise de produção justificasse (seria um novo ADR).

### ADR-004: Idempotência via banco, não Redis

**Decisão**: Tabela `idempotency_keys` no PostgreSQL.

**Motivação**: Redis adicionaria uma dependência de infraestrutura não prevista na constituição. O volume do desafio não justifica a complexidade adicional. PostgreSQL é suficiente e já está presente.

### ADR-005: Kafka publish-and-forget (sem outbox completo)

**Decisão**: Publicação de eventos Kafka após commit da transação, sem outbox table.

**Motivação**: O desafio não exige garantia exactly-once na publicação. Um outbox completo aumentaria a complexidade além do escopo. Limitação documentada — evolução futura óbvia.

### ADR-006: WireMock como contratos vivos

**Decisão**: Os mapeamentos em `wiremock/mappings/` são a única fonte de verdade dos serviços externos — tanto em produção (Docker Compose) quanto nos testes (Testcontainers).

**Motivação**: Evita divergência entre stub de teste e comportamento real. Os arquivos JSON documentam o contrato que existiria num microserviço real.

---

## Estrutura do Repositório (entrega final)

```
/
├── docs/
│   └── architecture.md          ← este documento
├── order-service/
│   ├── src/
│   │   ├── main/java/com/plataforma/order/
│   │   │   ├── domain/          ← zero dependências de framework
│   │   │   ├── application/     ← use cases
│   │   │   └── infrastructure/  ← Spring, JPA, Kafka, WebClient
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    ← V1 a V4 Flyway
│   ├── pom.xml
│   └── Dockerfile
├── wiremock/
│   ├── mappings/                ← 10 arquivos JSON de contrato
│   └── __files/
├── docker-compose.yml           ← 7 serviços: app, postgres, kafka, wiremock, jaeger, prometheus, grafana
├── README.md                    ← instruções para rodar localmente
└── .github/
    └── workflows/
        └── ci.yml               ← build → unit → integration → trivy
```
