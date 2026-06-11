# Contrato: Orders API — /api/v1/orders

**Versão**: v1 | **Auth**: Bearer JWT obrigatório em todos os endpoints

---

## POST /api/v1/orders
Cria um novo pedido para um cliente ativo.

**Scope**: `orders:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Request**:
```json
{ "customerId": "uuid" }
```

**Responses**:
- `201 Created` → `OrderResponse`
- `404 Not Found` → cliente inexistente
- `422 Unprocessable Entity` → cliente bloqueado
- `409 Conflict` → cliente já possui pedido OPEN
- `409 Conflict` → `Idempotency-Key` com conflito de recurso diferente

---

## GET /api/v1/orders/{orderId}
Retorna os detalhes de um pedido.

**Scope**: `orders:read`

**Responses**:
- `200 OK` → `OrderResponse`
- `404 Not Found` → pedido inexistente

---

## GET /api/v1/orders?customerId={id}
Lista todos os pedidos de um cliente.

**Scope**: `orders:read`

**Responses**:
- `200 OK` → `List<OrderResponse>`
- `200 OK` → lista vazia `[]` se nenhum pedido encontrado

---

## POST /api/v1/orders/{orderId}/items
Adiciona um item ao pedido. Se o produto já estiver no pedido, incrementa a quantidade.

**Scope**: `orders:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Request**:
```json
{ "productId": "uuid", "quantity": 2 }
```

**Responses**:
- `200 OK` → `OrderResponse` (estado atualizado)
- `404 Not Found` → pedido ou produto inexistente
- `422 Unprocessable Entity` → produto indisponível
- `409 Conflict` → pedido não está em estado OPEN
- `400 Bad Request` → quantidade ≤ 0

---

## DELETE /api/v1/orders/{orderId}/items/{itemId}
Remove um item do pedido.

**Scope**: `orders:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Responses**:
- `200 OK` → `OrderResponse` (estado atualizado)
- `404 Not Found` → pedido ou item inexistente
- `409 Conflict` → pedido não está em estado OPEN

---

## POST /api/v1/orders/{orderId}/confirm
Confirma o pedido. Calcula o valor total com preços atuais do catálogo. Idempotente.

**Scope**: `orders:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Responses**:
- `200 OK` → `OrderResponse` com status `CONFIRMED` e `totalAmount` preenchido
- `404 Not Found` → pedido inexistente
- `409 Conflict` → pedido sem itens
- `409 Conflict` → pedido não está em estado OPEN
- `409 Conflict` → conflito de versão (concurrent modification)

---

## DELETE /api/v1/orders/{orderId}
Cancela o pedido. Permitido nos estados OPEN, CONFIRMED e PAYMENT_PENDING (antes de aprovação).

**Scope**: `orders:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Responses**:
- `200 OK` → `OrderResponse` com status `CANCELLED`
- `404 Not Found` → pedido inexistente
- `409 Conflict` → pedido já PAYMENT_APPROVED ou CANCELLED

---

## OrderResponse (schema compartilhado)
```json
{
  "id": "uuid",
  "customerId": "uuid",
  "status": "OPEN | CONFIRMED | PAYMENT_PENDING | PAYMENT_APPROVED | CANCELLED",
  "items": [
    { "id": "uuid", "productId": "uuid", "quantity": 2, "unitPrice": 49.90 }
  ],
  "totalAmount": 99.80,
  "paymentAttempts": 0,
  "createdAt": "2026-06-10T10:00:00Z",
  "updatedAt": "2026-06-10T10:05:00Z"
}
```

---

## Erro Padrão — RFC 7807 Problem Details
```json
{
  "type": "https://api.plataforma.com/problems/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "Order with id '...' was not found.",
  "instance": "/api/v1/orders/..."
}
```
