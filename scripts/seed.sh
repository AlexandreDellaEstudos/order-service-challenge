#!/usr/bin/env bash
# Popula o banco com pedidos de exemplo em estados variados (via API).
# Pré-requisito: stack no ar.  Uso: BASE_URL=http://localhost:8081 ./scripts/seed.sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8081}"
API="$BASE_URL/api/v1"
SECRET="${APP_JWT_SECRET:-0f75da60a576f8c7aedc5bb75b19d221abcd68d61df47a078a3b5117722dc2fd}"

b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64)
payload=$(printf '%s' "{\"sub\":\"seed\",\"scope\":\"orders:write orders:read\",\"exp\":$(($(date +%s) + 3600))}" | b64)
sig=$(printf '%s' "$header.$payload" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64)
TOKEN="$header.$payload.$sig"

post() { curl -s -X POST "$1" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" ${2:+-d "$2"}; }
get()  { curl -s -X GET  "$1" -H "Authorization: Bearer $TOKEN"; }
novo_id() { post "$API/pedidos" "{\"clienteId\":\"$1\"}" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*'; }

echo "Criando massa de pedidos de exemplo..."

# 1) ABERTO com itens
ID=$(novo_id "cliente-aberto")
post "$API/pedidos/$ID/itens" '{"produtoId":"mouse","quantidade":2}' >/dev/null
echo "  pedido $ID -> ABERTO (cliente-aberto)"

# 2) CONFIRMADO
ID=$(novo_id "cliente-confirmado")
post "$API/pedidos/$ID/itens" '{"produtoId":"notebook","quantidade":1}' >/dev/null
post "$API/pedidos/$ID/confirmacao" >/dev/null
echo "  pedido $ID -> CONFIRMADO (cliente-confirmado)"

# 3) PAGAMENTO_APROVADO
ID=$(novo_id "cliente-pago")
post "$API/pedidos/$ID/itens" '{"produtoId":"notebook","quantidade":1}' >/dev/null
post "$API/pedidos/$ID/itens" '{"produtoId":"mouse","quantidade":3}' >/dev/null
post "$API/pedidos/$ID/confirmacao" >/dev/null
post "$API/payments" "{\"pedidoId\":$ID}" >/dev/null
post "$API/payments/$ID/callback" '{"resultado":"APROVADO"}' >/dev/null
echo "  pedido $ID -> PAGAMENTO_APROVADO (cliente-pago)"

# 4) CANCELADO
ID=$(novo_id "cliente-cancelado")
post "$API/pedidos/$ID/itens" '{"produtoId":"mouse","quantidade":1}' >/dev/null
curl -s -X DELETE "$API/pedidos/$ID" -H "Authorization: Bearer $TOKEN" >/dev/null
echo "  pedido $ID -> CANCELADO (cliente-cancelado)"

echo "Massa criada com sucesso."
