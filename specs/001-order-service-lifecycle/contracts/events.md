# Contrato: Domain Events — Kafka

**Namespace de tópicos**: `orders.*`

---

## orders.confirmed
Publicado quando um pedido é confirmado com sucesso.

**Payload**:
```json
{
  "orderId": "uuid",
  "customerId": "uuid",
  "totalAmount": 149.90,
  "occurredAt": "2026-06-10T10:05:00Z"
}
```

---

## orders.payment-approved
Publicado quando o callback do gateway confirma aprovação do pagamento.

**Payload**:
```json
{
  "orderId": "uuid",
  "paymentId": "uuid",
  "occurredAt": "2026-06-10T10:10:00Z"
}
```

---

## orders.payment-rejected
Publicado a cada rejeição de pagamento (incluindo a terceira, que também cancela o pedido).

**Payload**:
```json
{
  "orderId": "uuid",
  "paymentId": "uuid",
  "attemptNumber": 1,
  "occurredAt": "2026-06-10T10:10:00Z"
}
```

---

## orders.cancelled
Publicado quando um pedido é cancelado, seja manualmente ou por esgotamento de tentativas.

**Payload**:
```json
{
  "orderId": "uuid",
  "customerId": "uuid",
  "reason": "MANUAL | MAX_REJECTIONS",
  "occurredAt": "2026-06-10T10:15:00Z"
}
```

---

## Consumers (downstream — não implementados)

| Serviço | Tópico consumido | Finalidade |
|---|---|---|
| Notification Service | todos | Notificar cliente sobre mudanças de estado |
| Fulfillment Service | `orders.payment-approved` | Iniciar processo de separação/envio |
| Inventory Service | `orders.confirmed`, `orders.cancelled` | Reservar / liberar estoque |

> O `order-service` **não conhece** esses consumers. Eles são downstream e fora do escopo deste desafio.

---

## WireMock Mappings (serviços externos consumidos pelo order-service)

| Arquivo | Serviço | Cenário |
|---|---|---|
| `customers-active.json` | Customer Service | GET /customers/{id} → 200 cliente ativo |
| `customers-blocked.json` | Customer Service | GET /customers/{id} → 422 cliente bloqueado |
| `customers-not-found.json` | Customer Service | GET /customers/{id} → 404 não encontrado |
| `products-available.json` | Catalog Service | GET /products/{id} → 200 produto com preço |
| `products-unavailable.json` | Catalog Service | GET /products/{id} → 422 indisponível |
| `products-not-found.json` | Catalog Service | GET /products/{id} → 404 não encontrado |
| `payments-approved.json` | Payment Gateway | POST /payments → 200 aprovado |
| `payments-rejected.json` | Payment Gateway | POST /payments → 200 rejeitado |
| `payments-gateway-error.json` | Payment Gateway | POST /payments → 503 instável |
| `notifications.json` | Notification Service | POST /notifications → 202 aceito |
