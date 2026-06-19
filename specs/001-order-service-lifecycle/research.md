# Research: Order Service — Ciclo de Vida de Pedidos

**Branch**: `001-order-service-lifecycle` | **Date**: 2026-06-10

---

## Decisões de Design

### D-001: Estrutura de Pacotes — Clean Architecture

**Decision**: Pacotes separados por camada: `domain`, `application`, `infrastructure`.

**Rationale**: A constituição é NON-NEGOTIABLE nesse ponto. O domínio não pode ter dependência de Spring, JPA ou qualquer framework. Cada camada só pode importar camadas mais internas.

**Structure**:
```
com.plataforma.order/
├── domain/
│   ├── model/         (Order, OrderItem, Payment — Entities e VOs)
│   ├── event/         (OrderConfirmed, PaymentApproved, etc.)
│   └── port/          (OrderRepository, CustomerPort, CatalogPort, PaymentGatewayPort, EventPublisher)
├── application/
│   └── usecase/       (CreateOrderUseCase, ConfirmOrderUseCase, etc.)
└── infrastructure/
    ├── adapter/
    │   ├── http/       (OrderController, PaymentController — REST adapters)
    │   ├── persistence/ (OrderJpaRepository, JPA entities)
    │   ├── client/     (CustomerWebClient, CatalogWebClient, PaymentGatewayWebClient)
    │   └── messaging/  (KafkaEventPublisher)
    └── config/         (SecurityConfig, KafkaConfig, WebClientConfig, etc.)
```

**Alternatives considered**: Estrutura por feature (ex: `orders/`, `payments/`) — rejeitada porque misturaria camadas e violaria a Dependency Rule da constituição.

---

### D-002: Aggregate Root — Order como fronteira de consistência

**Decision**: `Order` é o único Aggregate Root. `OrderItem` e `Payment` são entidades internas controladas pelo aggregate.

**Rationale**: Todas as transições de estado do pedido (confirmação, pagamento, cancelamento) passam pelo `Order`. Isso garante que invariantes como "pedido sem itens não pode ser confirmado" e "limite de 3 tentativas de pagamento" sejam verificados em um único lugar.

**Payment como aggregate separado?**: Considerado, mas rejeitado para esta fase. O `Payment` nasce atrelado a um único `Order`, e todas as suas transições de estado são acionadas por eventos do ciclo de vida do pedido. Manter no mesmo aggregate simplifica a consistência sem perder clareza.

---

### D-003: Idempotência — Tabela de chaves

**Decision**: Tabela `idempotency_keys` no banco de dados armazenando hash da chave + resposta serializada.

**Rationale**: Antes de executar qualquer use case de mutação, verificar se a chave já foi processada. Se sim, retornar a resposta original. Isso garante idempotência mesmo em cenários de retry de rede.

**Schema**:
```sql
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    response_status INT NOT NULL,
    response_body   TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Alternatives considered**: Cache Redis — mais performático, mas adiciona dependência de infraestrutura não prevista na constituição. PostgreSQL é suficiente para o volume esperado.

---

### D-004: Controle de Concorrência — Optimistic Locking

**Decision**: `@Version` no campo `version` da entidade `Order` via JPA.

**Rationale**: A constituição define explicitamente optimistic locking como padrão. Em caso de conflito, Spring lança `OptimisticLockException` que deve ser capturada e convertida em HTTP 409 Conflict com Problem Details (RFC 7807).

**Quando pessimistic seria necessário**: Apenas se análise de produção demonstrasse contenda excessiva na mesma `Order` com altas taxas de conflito. Um ADR documentaria essa decisão em `docs/architecture.md`.

---

### D-005: Resiliência no Payment Gateway — Circuit Breaker

**Decision**: Resilience4j Circuit Breaker envolvendo `PaymentGatewayWebClient`.

**Config**:
- `failureRateThreshold`: 50%
- `slowCallRateThreshold`: 60%
- `waitDurationInOpenState`: 30s
- `slidingWindowSize`: 10

**Retry antes do CB**: `maxAttempts = 3`, exponential backoff com jitter (delay inicial 250ms). Somente para erros 5xx — erros 4xx não são retentados (indicam problema no request, não transiente).

**Fallback**: lança `PaymentGatewayUnavailableException` → HTTP 503 com `Retry-After: 30` header.

**Rationale**: O gateway de pagamento (WireMock) tem mapeamento de 503 — o desafio exige que a plataforma não pare quando o gateway estiver instável. Retry + CB combinados seguem o padrão da Aula 2 (backoff com jitter) + Aula 5 (Circuit Breaker para proteção do sistema).

---

### D-006: Records como DTOs — Mapeamento com MapStruct

**Decision**: Todos os objetos de transferência entre camadas são Java Records. MapStruct faz todas as conversões.

**Direção do mapeamento**:
```
HTTP Request Record  →  Domain Command  →  Domain Entity  →  HTTP Response Record
Kafka Event Record   ←  Domain Event    (via OrderEventMapper)
```

**Proibido**: classes com getters/setters como DTOs. Entidades de domínio não podem ser serializadas diretamente.

---

### D-007: Kafka — Publish-and-forget com outbox pattern simplificado

**Decision**: Publicação síncrona no mesmo contexto de transação, sem outbox table completo.

**Rationale**: Para o escopo do desafio, publicar evento após commit da transação é suficiente. O desafio não exige garantia exactly-once na publicação Kafka. Um ADR em `docs/architecture.md` documenta essa limitação e propõe outbox pattern completo como evolução futura.

**Topics**:
| Evento | Tópico |
|---|---|
| `OrderConfirmed` | `orders.confirmed` |
| `PaymentApproved` | `orders.payment-approved` |
| `PaymentRejected` | `orders.payment-rejected` |
| `OrderCancelled` | `orders.cancelled` |

---

### D-008: Segurança — JWT Resource Server com scopes

**Decision**: Spring Security como OAuth2 Resource Server validando JWT Bearer Token.

**Scopes**:
| Operação | Scope exigido |
|---|---|
| Criar/confirmar/cancelar pedido | `orders:write` |
| Consultar pedidos | `orders:read` |
| Iniciar pagamento / callback | `payments:write` |
| Consultar pagamento | `payments:read` |

**Auth server**: Para testes locais, usar stub estático de JWKS ou Keycloak via Docker Compose. O filtro de segurança de produção é sempre real.

---

### D-009: Observabilidade — Três pilares desde o início

**Decision**: Logs JSON via logstash-logback-encoder + Micrometer/Prometheus + OpenTelemetry.

**MDC obrigatório em todo log**:
- `correlationId`: extraído de `X-Correlation-ID` ou gerado
- `traceId`, `spanId`: populados pelo OTel bridge
- `service`: `order-service`
- Campos de negócio (`orderId`, `customerId`) quando disponíveis

**Métricas de negócio**:
- `orders.created.total` (Counter)
- `orders.confirmed.total` (Counter)
- `payments.initiated.total` (Counter)
- `payments.approved.total` (Counter)
- `payments.rejected.total` (Counter)
- `orders.cancelled.total` (Counter, por motivo: `reason=manual|max_rejections`)

---

### D-010: Estrutura do Repositório — Conforme desafio

**Decision**: Seguir exatamente o layout do `desafio.md`.

```
/
├── docs/
│   └── architecture.md
├── order-service/
│   ├── src/
│   └── Dockerfile
├── wiremock/
│   ├── mappings/
│   └── __files/
├── docker-compose.yml
├── README.md
└── .github/
    └── workflows/
        └── ci.yml
```
