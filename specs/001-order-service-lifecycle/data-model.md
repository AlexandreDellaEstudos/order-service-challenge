# Data Model: Order Service

**Branch**: `001-order-service-lifecycle` | **Date**: 2026-06-10

---

## Camada de Domínio

### Aggregate: Order (Raiz de Consistência)

```
Order
├── id: OrderId (UUID — Value Object)
├── customerId: CustomerId (UUID — Value Object)
├── status: OrderStatus (enum)
├── items: List<OrderItem>
├── totalAmount: Money (BigDecimal + Currency — Value Object)
├── paymentAttempts: int (0..3)
├── version: long (@Version — Optimistic Locking)
├── createdAt: Instant
└── updatedAt: Instant
```

**Invariantes do Aggregate**:
- `paymentAttempts` só incrementa; nunca volta a zero
- `totalAmount` é calculado apenas no momento da confirmação; até lá é zero
- Somente o `Order` pode modificar `items` (via métodos de domínio)
- Qualquer transição de estado inválida lança `InvalidOrderStateTransitionException`

---

### Entity: OrderItem (dentro do Aggregate)

```
OrderItem
├── id: OrderItemId (UUID)
├── productId: ProductId (UUID — Value Object)
├── quantity: int (> 0)
└── unitPrice: Money (preenchido na confirmação; zero até lá)
```

**Regras**:
- Mesmo `productId` → incrementa `quantity`, não cria novo item
- Remoção de `productId` inexistente → lança `ItemNotFoundException`
- `quantity` nunca pode ser ≤ 0

---

### Entity: Payment (Entidade raiz separada — ligada a Order por orderId)

> **Nota DDD**: Payment não está dentro do aggregate Order. É uma entidade com ciclo de vida próprio, identificável por `PaymentId`, persistida e buscada de forma independente. Isso é necessário porque o endpoint `GET /payments/{id}` precisa carregar um Payment diretamente sem passar pelo Order. O vínculo com Order é via `orderId` (referência, não composição).

```
Payment
├── id: PaymentId (UUID)
├── orderId: OrderId (referência ao Order relacionado)
├── status: PaymentStatus (enum)
├── externalId: String (ID retornado pelo gateway)
├── callbackCount: int (quantas vezes o callback foi processado — idempotência)
├── createdAt: Instant
└── updatedAt: Instant
```

---

### Enums de Domínio

```
OrderStatus:
  OPEN             → pedido criado, itens podem ser adicionados
  CONFIRMED        → pedido confirmado, valor calculado, aguardando pagamento
  PAYMENT_PENDING  → pagamento iniciado junto ao gateway
  PAYMENT_APPROVED → pagamento aprovado (TERMINAL — sucesso)
  CANCELLED        → pedido cancelado (TERMINAL)

PaymentStatus:
  PENDING   → aguardando callback
  APPROVED  → aprovado pelo gateway
  REJECTED  → rejeitado pelo gateway
```

---

### Máquina de Estados — Transições Válidas

```
OPEN            ──[confirm]──────────────────► CONFIRMED
OPEN            ──[cancel manual]────────────► CANCELLED
CONFIRMED       ──[initiate payment]─────────► PAYMENT_PENDING
CONFIRMED       ──[cancel manual]────────────► CANCELLED
PAYMENT_PENDING ──[cancel manual]────────────► CANCELLED       ← pedido cancelável antes da aprovação
PAYMENT_PENDING ──[callback: approved]──────► PAYMENT_APPROVED
PAYMENT_PENDING ──[callback: rejected < 3]──► CONFIRMED
PAYMENT_PENDING ──[callback: rejected ≥ 3]──► CANCELLED

Estados terminais: PAYMENT_APPROVED, CANCELLED — qualquer modificação lança InvalidOrderStateTransitionException
```

---

### Value Objects

```
OrderId     : record OrderId(UUID value)
CustomerId  : record CustomerId(UUID value)
ProductId   : record ProductId(UUID value)
OrderItemId : record OrderItemId(UUID value)
PaymentId   : record PaymentId(UUID value)
Money       : record Money(BigDecimal amount, Currency currency)
```

**Regras dos VOs**:
- `Money.amount` nunca negativo
- Todos os IDs são UUID versão 4

---

### Domain Events

```
OrderConfirmed   { orderId, customerId, totalAmount, confirmedAt }
PaymentInitiated { orderId, paymentId, amount, initiatedAt }
PaymentApproved  { orderId, paymentId, approvedAt }
PaymentRejected  { orderId, paymentId, attemptNumber, rejectedAt }
OrderCancelled   { orderId, customerId, reason (MANUAL | MAX_REJECTIONS), cancelledAt }
```

---

## Camada de Infraestrutura — Persistência

### Tabela: `orders`

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `customer_id` | UUID | NOT NULL, INDEX |
| `status` | VARCHAR(30) | NOT NULL |
| `total_amount` | DECIMAL(19,2) | NOT NULL DEFAULT 0 |
| `payment_attempts` | INT | NOT NULL DEFAULT 0 |
| `version` | BIGINT | NOT NULL DEFAULT 0 |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

### Tabela: `order_items`

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `order_id` | UUID | FK → orders.id, INDEX |
| `product_id` | UUID | NOT NULL |
| `quantity` | INT | NOT NULL CHECK > 0 |
| `unit_price` | DECIMAL(19,2) | NOT NULL DEFAULT 0 |
| `created_at` | TIMESTAMPTZ | NOT NULL |

**Constraint**: UNIQUE (`order_id`, `product_id`)

### Tabela: `payments`

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | UUID | PK |
| `order_id` | UUID | FK → orders.id, UNIQUE, INDEX |
| `status` | VARCHAR(30) | NOT NULL |
| `external_id` | VARCHAR(255) | |
| `callback_count` | INT | NOT NULL DEFAULT 0 |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

### Tabela: `idempotency_keys`

| Coluna | Tipo | Constraint |
|---|---|---|
| `idempotency_key` | VARCHAR(255) | PK |
| `response_status` | INT | NOT NULL |
| `response_body` | TEXT | NOT NULL |
| `endpoint` | VARCHAR(255) | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

---

## Ports (Interfaces de Domínio → Infraestrutura)

```java
// Persistência
interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomerId(CustomerId customerId);
    Optional<Order> findOpenByCustomerId(CustomerId customerId);
}

interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(PaymentId id);
    Optional<Payment> findByOrderId(OrderId orderId);
}

// Serviços externos
interface CustomerPort {
    CustomerValidationResult validate(CustomerId customerId);
}

interface CatalogPort {
    ProductInfo getProduct(ProductId productId); // lança ProductUnavailableException se indisponível
}

interface PaymentGatewayPort {
    PaymentGatewayResponse initiate(OrderId orderId, Money amount);
}

// Mensageria
interface DomainEventPublisher {
    void publish(DomainEvent event);
}
```

---

## Records de Transferência (HTTP)

### Requests
```
CreateOrderRequest  { customerId: UUID }
AddItemRequest      { productId: UUID, quantity: int }
InitiatePaymentRequest { orderId: UUID }
PaymentCallbackRequest { status: String, externalId: String }
```

### Responses
```
OrderResponse {
  id: UUID, customerId: UUID, status: String,
  items: List<OrderItemResponse>, totalAmount: BigDecimal,
  paymentAttempts: int, createdAt: Instant, updatedAt: Instant
}
OrderItemResponse { id: UUID, productId: UUID, quantity: int, unitPrice: BigDecimal }
PaymentResponse   { id: UUID, orderId: UUID, status: String, externalId: String, createdAt: Instant }
```

### Kafka Event Records
```
OrderConfirmedEvent   { orderId, customerId, totalAmount, occurredAt }
PaymentApprovedEvent  { orderId, paymentId, occurredAt }
PaymentRejectedEvent  { orderId, paymentId, attemptNumber, occurredAt }
OrderCancelledEvent   { orderId, customerId, reason, occurredAt }
```
