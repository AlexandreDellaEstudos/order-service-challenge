# Quickstart — Validação do Order Service

**Branch**: `001-order-service-lifecycle` | **Date**: 2026-06-10

---

## Pré-requisitos

- Docker e Docker Compose instalados
- Java 21 (para build local)
- Maven 3.9+

---

## Subir o ambiente

```bash
# Na raiz do repositório
docker-compose up -d
```

**Serviços que sobem**:
| Serviço | Porta | URL |
|---|---|---|
| order-service | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | localhost:5432 |
| WireMock | 8081 | http://localhost:8081 |
| Kafka | 9092 | localhost:9092 |
| Jaeger UI | 16686 | http://localhost:16686 |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |
| Swagger UI | — | http://localhost:8080/swagger-ui.html |

---

## Cenário 1 — Fluxo Completo com Pagamento Aprovado

```bash
# 1. Criar pedido (usar UUID de cliente ativo mapeado no WireMock)
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"customerId": "11111111-1111-1111-1111-111111111111"}'
# Esperado: 201 Created, status: "OPEN"

# 2. Adicionar item
curl -X POST http://localhost:8080/api/v1/orders/{orderId}/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"productId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "quantity": 2}'
# Esperado: 200 OK, item aparece na lista

# 3. Confirmar pedido
curl -X POST http://localhost:8080/api/v1/orders/{orderId}/confirm \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)"
# Esperado: 200 OK, status: "CONFIRMED", totalAmount preenchido

# 4. Iniciar pagamento
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"orderId": "{orderId}"}'
# Esperado: 201 Created, status: "PENDING"

# 5. Simular callback de aprovação
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/callback \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"status": "APPROVED", "externalId": "gtw-ref-001"}'
# Esperado: 200 OK, payment status: "APPROVED"

# 6. Verificar pedido final
curl http://localhost:8080/api/v1/orders/{orderId} \
  -H "Authorization: Bearer <token>"
# Esperado: status: "PAYMENT_APPROVED"
```

---

## Cenário 2 — 3 Rejeições → Cancelamento Automático

```bash
# Após confirmar pedido, iniciar pagamento...

# Callback 1: rejeição
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/callback \
  -d '{"status": "REJECTED", "externalId": "gtw-ref-001"}' ...
# Esperado: pedido volta para "CONFIRMED", paymentAttempts: 1

# Novo pagamento
curl -X POST http://localhost:8080/api/v1/payments ...
# Esperado: novo Payment criado, pedido vai para "PAYMENT_PENDING"

# Callback 2: rejeição
# paymentAttempts: 2, pedido volta para "CONFIRMED"

# Novo pagamento
# paymentAttempts: 2, pedido vai para "PAYMENT_PENDING"

# Callback 3: rejeição
# Esperado: pedido vai para "CANCELLED", paymentAttempts: 3
curl http://localhost:8080/api/v1/orders/{orderId} ...
# Esperado: status: "CANCELLED"
```

---

## Cenário 3 — Idempotência

```bash
IKEY="minha-chave-idempotente-001"

# Primeira chamada — cria o pedido
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Idempotency-Key: $IKEY" \
  -d '{"customerId": "11111111-..."}' ...
# Esperado: 201 Created

# Segunda chamada com mesma chave — retorna a mesma resposta
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Idempotency-Key: $IKEY" \
  -d '{"customerId": "11111111-..."}' ...
# Esperado: 201 Created, mesmo ID de pedido, sem novo pedido criado
```

---

## Cenário 4 — Concorrência (Optimistic Locking)

```bash
# Duas requisições simultâneas de confirmação para o mesmo pedido
# Uma deve ter sucesso (200 OK), a outra deve retornar:
# Esperado: 409 Conflict com Problem Details indicando conflito de versão
```

---

## Verificar Observabilidade

```bash
# Logs estruturados (JSON)
docker logs order-service --tail 20

# Métricas Prometheus
curl http://localhost:8080/actuator/prometheus | grep orders_

# Traces no Jaeger
# Abrir http://localhost:16686 → buscar por service: order-service
```

---

## Executar Testes

```bash
cd order-service

# Testes unitários
mvn test -pl .

# Testes de integração (requer Docker para Testcontainers)
mvn verify -P integration-tests

# Mutation Testing (Pitest) — MSI alvo: ≥ 75%
mvn test-compile org.pitest:pitest-maven:mutationCoverage
# Relatório em: target/pit-reports/index.html
```
