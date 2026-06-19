# Contrato: Payments API — /api/v1/payments

**Versão**: v1 | **Auth**: Bearer JWT obrigatório em todos os endpoints

---

## POST /api/v1/payments
Inicia o pagamento de um pedido confirmado. Idempotente por `orderId`.

**Scope**: `payments:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Request**:
```json
{ "orderId": "uuid" }
```

**Responses**:
- `201 Created` → `PaymentResponse`
- `404 Not Found` → pedido inexistente
- `409 Conflict` → pedido não está em estado CONFIRMED
- `409 Conflict` → pagamento já iniciado para este pedido
- `503 Service Unavailable` → gateway de pagamento indisponível (com header `Retry-After: 30`)

---

## GET /api/v1/payments/{paymentId}
Retorna o status de um pagamento.

**Scope**: `payments:read`

**Responses**:
- `200 OK` → `PaymentResponse`
- `404 Not Found` → pagamento inexistente

---

## POST /api/v1/payments/{paymentId}/callback
Recebe o resultado do gateway de pagamento (webhook). Totalmente idempotente: reprocessar o mesmo evento não gera efeito colateral.

**Scope**: `payments:write`
**Headers**: `Idempotency-Key: <uuid>` (obrigatório)

**Request**:
```json
{
  "status": "APPROVED | REJECTED",
  "externalId": "gtw-ref-uuid"
}
```

**Responses**:
- `200 OK` → `PaymentResponse` com estado atualizado
- `200 OK` → resposta idempotente (sem mudança de estado se callback já processado)
- `404 Not Found` → pagamento inexistente
- `409 Conflict` → pagamento não está em estado PENDING

**Lógica de transição após callback**:
- `APPROVED` → pedido vai para `PAYMENT_APPROVED` (terminal)
- `REJECTED` com `paymentAttempts < 3` → pedido volta para `CONFIRMED`
- `REJECTED` com `paymentAttempts = 3` → pedido vai para `CANCELLED`

---

## PaymentResponse (schema compartilhado)
```json
{
  "id": "uuid",
  "orderId": "uuid",
  "status": "PENDING | APPROVED | REJECTED",
  "externalId": "gtw-ref-uuid",
  "createdAt": "2026-06-10T10:00:00Z",
  "updatedAt": "2026-06-10T10:05:00Z"
}
```
