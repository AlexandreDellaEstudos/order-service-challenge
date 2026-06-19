#!/usr/bin/env bash
# Cenários de teste da API do order-service (sucesso e erro).
# Pré-requisito: stack no ar (docker compose up).  Uso: BASE_URL=http://localhost:8081 ./scripts/testar-api.sh
set -u

BASE_URL="${BASE_URL:-http://localhost:8081}"
API="$BASE_URL/api/v1"
SECRET="${APP_JWT_SECRET:-0f75da60a576f8c7aedc5bb75b19d221abcd68d61df47a078a3b5117722dc2fd}"
PASS=0
FAIL=0
BODY="$(mktemp)"

b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

gerar_token() {
  local scope="$1"
  local header payload sig
  header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64)
  payload=$(printf '%s' "{\"sub\":\"tester\",\"scope\":\"$scope\",\"exp\":$(($(date +%s) + 3600))}" | b64)
  sig=$(printf '%s' "$header.$payload" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64)
  printf '%s.%s.%s' "$header" "$payload" "$sig"
}

TOKEN="$(gerar_token 'orders:write orders:read')"
AUTH=(-H "Authorization: Bearer $TOKEN")
JSON=(-H "Content-Type: application/json")

# req METODO URL [DADOS] -> escreve corpo em $BODY, retorna o status HTTP
req() {
  local metodo="$1" url="$2" dados="${3:-}"
  if [ -n "$dados" ]; then
    curl -s -o "$BODY" -w '%{http_code}' -X "$metodo" "$url" "${AUTH[@]}" "${JSON[@]}" -d "$dados"
  else
    curl -s -o "$BODY" -w '%{http_code}' -X "$metodo" "$url" "${AUTH[@]}"
  fi
}

check() {
  local desc="$1" esperado="$2" obtido="$3"
  if [ "$obtido" = "$esperado" ]; then
    echo "  OK   $desc ($obtido)"
    PASS=$((PASS + 1))
  else
    echo "  FALHA $desc -> esperado $esperado, veio $obtido"
    FAIL=$((FAIL + 1))
  fi
}

id_do_corpo() { grep -o '"id":[0-9]*' "$BODY" | head -1 | grep -o '[0-9]*'; }

echo "== SUCESSO =="

st=$(req POST "$API/pedidos" '{"clienteId":"cliente-1"}'); check "criar pedido" 201 "$st"
PID=$(id_do_corpo)

st=$(req POST "$API/pedidos/$PID/itens" '{"produtoId":"notebook","quantidade":1}'); check "adicionar notebook" 200 "$st"
st=$(req POST "$API/pedidos/$PID/itens" '{"produtoId":"mouse","quantidade":2}');     check "adicionar mouse"    200 "$st"
st=$(req POST "$API/pedidos/$PID/confirmacao");                                        check "confirmar pedido"  200 "$st"
st=$(req POST "$API/payments" "{\"pedidoId\":$PID}");                                  check "iniciar pagamento" 200 "$st"
st=$(req POST "$API/payments/$PID/callback" '{"resultado":"APROVADO"}');               check "callback aprovado" 200 "$st"
st=$(req GET  "$API/payments/$PID");                                                   check "status pagamento"  200 "$st"
st=$(req GET  "$API/pedidos/$PID");                                                    check "consultar pedido"  200 "$st"
st=$(req GET  "$API/pedidos?clienteId=cliente-1");                                     check "listar por cliente" 200 "$st"

# idempotência: mesma chave duas vezes nao duplica
KEY="chave-$(date +%s)"
st=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$API/pedidos" "${AUTH[@]}" "${JSON[@]}" -H "Idempotency-Key: $KEY" -d '{"clienteId":"cliente-idem"}'); ID_A=$(id_do_corpo); check "idempotencia 1a" 201 "$st"
st=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$API/pedidos" "${AUTH[@]}" "${JSON[@]}" -H "Idempotency-Key: $KEY" -d '{"clienteId":"cliente-idem"}'); ID_B=$(id_do_corpo); check "idempotencia 2a" 201 "$st"
check "idempotencia mesmo id" "$ID_A" "$ID_B"

echo "== ERRO =="

st=$(curl -s -o "$BODY" -w '%{http_code}' -X POST "$API/pedidos" "${JSON[@]}" -d '{"clienteId":"cliente-1"}'); check "sem token -> 401" 401 "$st"
st=$(req POST "$API/pedidos" '{"clienteId":"bloqueado"}');   check "cliente bloqueado -> 422"  422 "$st"
st=$(req POST "$API/pedidos" '{"clienteId":"inexistente"}'); check "cliente inexistente -> 422" 422 "$st"

st=$(req POST "$API/pedidos" '{"clienteId":"cliente-2"}'); PID2=$(id_do_corpo)
st=$(req POST "$API/pedidos/$PID2/itens" '{"produtoId":"indisponivel","quantidade":1}'); check "produto indisponivel -> 422" 422 "$st"
st=$(req POST "$API/pedidos/$PID2/itens" '{"produtoId":"inexistente","quantidade":1}');  check "produto inexistente -> 404"  404 "$st"

st=$(req POST "$API/pedidos" '{"clienteId":"cliente-3"}'); PID3=$(id_do_corpo)
st=$(req POST "$API/pedidos/$PID3/confirmacao"); check "confirmar sem itens -> 422" 422 "$st"

st=$(req GET "$API/pedidos/99999999"); check "pedido inexistente -> 404" 404 "$st"

st=$(req DELETE "$API/pedidos/$PID"); check "cancelar pedido aprovado -> 422" 422 "$st"

echo "== FLUXO 3 REJEICOES -> CANCELA =="
st=$(req POST "$API/pedidos" '{"clienteId":"cliente-rej"}'); PIDR=$(id_do_corpo)
req POST "$API/pedidos/$PIDR/itens" '{"produtoId":"mouse","quantidade":1}' >/dev/null
req POST "$API/pedidos/$PIDR/confirmacao" >/dev/null
for i in 1 2 3; do
  req POST "$API/payments" "{\"pedidoId\":$PIDR}" >/dev/null
  req POST "$API/payments/$PIDR/callback" '{"resultado":"REJEITADO"}' >/dev/null
done
req GET "$API/pedidos/$PIDR" >/dev/null
estado=$(grep -o '"status":"[A-Z_]*"' "$BODY" | head -1 | cut -d'"' -f4)
check "3 rejeicoes -> CANCELADO" "CANCELADO" "$estado"

rm -f "$BODY"
echo
echo "==================== RESUMO ===================="
echo "  PASSOU: $PASS    FALHOU: $FAIL"
[ "$FAIL" -eq 0 ] && echo "  Todos os cenarios OK." || echo "  Ha cenarios com falha."
exit "$FAIL"
