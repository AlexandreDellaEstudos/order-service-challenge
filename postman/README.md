# Coleção Postman / Insomnia — Order Service

Coleção para testar a API do `order-service` (cenários de sucesso e erro). Funciona no **Postman** e no **Insomnia** (importa coleção Postman v2.1).

## Arquivos
- `order-service.postman_collection.json` — a coleção (5 pastas, 35 requisições).
- `order-service.postman_environment.json` — environment local (`baseUrl`, `token`).

## Como usar (clica e roda)
1. Suba a stack: `docker compose up` (a API fica em `http://localhost:8081`).
2. **Import** dos 2 arquivos no Postman/Insomnia.
3. Selecione o environment **"Order Service - Local"**.
4. Clique em qualquer requisição (ou use o **Runner**). O token JWT já vem **embutido** na variável `token`.

> O token vem pronto e válido por alguns dias — **não precisa configurar nada**. Os fluxos encadeados (criar → usar o id) guardam o `pedidoId` automaticamente entre as requisições.

## Pastas
1. **Fluxo feliz** — criar → itens → confirmar → pagar → callback aprovado → status → buscar → listar.
2. **Cenários de erro** — 401, cliente bloqueado/inexistente (422), produto indisponível (422)/inexistente (404), pedido inexistente (404), pedido aberto duplicado (409), confirmar sem itens (422), cancelar aprovado (422).
3. **Operações avulsas** — adicionar → remover item → cancelar.
4. **Pagamento rejeitado (3x)** — 3 rejeições → cancelamento automático.
5. **Idempotência** — mesma `Idempotency-Key` → mesmo pedido.

## Token expirou?
Gere um novo (assinado com o mesmo segredo da app, `APP_JWT_SECRET`) e atualize a variável `token`:

```bash
# precisa do openssl (vem no Git Bash)
SECRET="0f75da60a576f8c7aedc5bb75b19d221abcd68d61df47a078a3b5117722dc2fd"
b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64)
P=$(printf '%s' "{\"sub\":\"tester\",\"scope\":\"orders:write orders:read\",\"exp\":$(($(date +%s)+604800))}" | b64)
S=$(printf '%s' "$H.$P" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64)
echo "$H.$P.$S"
```

> Alternativa sem Postman: o script `scripts/testar-api.sh` roda os mesmos cenários via `curl` (gera o token sozinho).
