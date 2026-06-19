# Order Service

Serviço de pedidos (`order-service`) de uma plataforma de e-commerce. É o **único** serviço implementado — Cliente, Catálogo, Gateway de Pagamento e Notificação são **externos**, simulados via **WireMock**.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL 16 + Flyway · Kafka · OAuth2/JWT · Prometheus + Grafana + Jaeger. Arquitetura **Clean/Hexagonal**, em português.

📐 Detalhes de arquitetura, decisões (ADRs) e modelo de domínio: [`docs/architecture.md`](docs/architecture.md).

---

## Pré-requisitos

- **Docker** + Docker Compose (forma recomendada de rodar)
- Para build/testes locais: **JDK 21** e **Maven 3.8+**

---

## Como rodar

### Opção A — tudo via Docker Compose (recomendado)

```bash
docker compose up --build
```

Sobe os 7 serviços: `order-service`, `postgres`, `kafka`, `wiremock`, `jaeger`, `prometheus`, `grafana`.
A API fica em **http://localhost:8081**.

Para parar e limpar:

```bash
docker compose down -v
```

### Opção B — infra no Docker, app local

```bash
docker compose up -d postgres kafka wiremock jaeger prometheus grafana
cd order-service
mvn spring-boot:run
```

### URLs úteis

| O quê | URL |
|---|---|
| API | http://localhost:8081/api/v1 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| Health | http://localhost:8081/actuator/health |
| Métricas (Prometheus scrape) | http://localhost:8081/actuator/prometheus |
| Grafana (dashboard) | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Jaeger (tracing) | http://localhost:16686 |

---

## Autenticação (JWT)

A API é protegida por **JWT** com escopos `orders:read` (GET) e `orders:write` (POST/DELETE). Os callbacks de pagamento e os endpoints de actuator/swagger são públicos.

> 🔐 **Segredo do JWT:** a aplicação valida o token com `app.security.jwt.secret` (`${APP_JWT_SECRET:<default de dev>}`). Para usar um segredo próprio **localmente**, copie `.env.example` → `.env` e ajuste `APP_JWT_SECRET` (o `.env` **não** é versionado; o `docker-compose` injeta no container). Em **produção**, injete via **`APP_JWT_SECRET`** (GitHub Secret / secret manager) — **nunca** comite um segredo real. Mínimo **32 bytes** (HS256). O valor que **gera** o token (Postman/scripts/CI) precisa ser o **mesmo** que a aplicação usa para **validar**.

Para testar localmente, gere um token HS256 assinado com o segredo de desenvolvimento (`app.security.jwt.secret`):

```bash
python3 - <<'PY'
import hmac, hashlib, base64, json, time
def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
secret = b'0f75da60a576f8c7aedc5bb75b19d221abcd68d61df47a078a3b5117722dc2fd'
header  = b64(json.dumps({"alg":"HS256","typ":"JWT"}).encode())
payload = b64(json.dumps({"sub":"dev","scope":"orders:write orders:read","exp":int(time.time())+3600}).encode())
sig     = b64(hmac.new(secret,(header+"."+payload).encode(),hashlib.sha256).digest())
print(header+"."+payload+"."+sig)
PY
```

Use o token nas chamadas: `-H "Authorization: Bearer <token>"`.

### Exemplo de fluxo (curl)

```bash
TOKEN="<token gerado acima>"

# cria pedido (cliente ativo no WireMock)
curl -X POST http://localhost:8081/api/v1/pedidos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"clienteId":"cliente-1"}'

# adiciona item (produto disponível no catálogo)
curl -X POST http://localhost:8081/api/v1/pedidos/1/itens \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"produtoId":"p1","quantidade":2}'

# confirma (calcula o total com o preço do catálogo)
curl -X POST http://localhost:8081/api/v1/pedidos/1/confirmacao -H "Authorization: Bearer $TOKEN"

# inicia o pagamento e simula o callback de aprovação
curl -X POST http://localhost:8081/api/v1/payments -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"pedidoId":1}'
curl -X POST http://localhost:8081/api/v1/payments/1/callback \
  -H "Content-Type: application/json" -d '{"resultado":"APROVADO"}'
```

> Repetir uma mutação com o mesmo header `Idempotency-Key: <uuid>` devolve a resposta original, sem efeito duplicado.

---

## Testar a API (coleção e scripts)

Com a stack no ar (`docker compose up`), há duas formas prontas de exercitar a API — ambas com cenários de **sucesso e erro**:

### Postman / Insomnia
A pasta [`postman/`](postman/) tem uma coleção importável (formato Postman v2.1 — **também funciona no Insomnia**, que importa coleções Postman):

- `order-service.postman_collection.json` — **5 pastas / 35 requisições**, com **asserts** de status e corpo.
- `order-service.postman_environment.json` — environment local (`baseUrl`, `jwtSecret`).

O **token JWT é gerado automaticamente** (pre-request da coleção) — não precisa colar token. Importe os 2 arquivos, selecione o environment **"Order Service - Local"** e use o **Collection Runner** para rodar tudo de uma vez. Cobre: fluxo feliz, erros (401/422/404/409), operações de item, **3 rejeições → cancelamento automático** e **idempotência**. Detalhes em [`postman/README.md`](postman/README.md).

### Scripts shell ([`scripts/`](scripts/))

```bash
# 21 cenários (sucesso + erro), com resumo PASS/FALHA — sai != 0 se algum falhar
BASE_URL=http://localhost:8081 bash scripts/testar-api.sh

# popula o banco com pedidos de exemplo em estados variados (ABERTO/CONFIRMADO/PAGO/CANCELADO)
BASE_URL=http://localhost:8081 bash scripts/seed.sh
```

O `testar-api.sh` gera o JWT, dispara os cenários via `curl` e imprime um resumo no final.

---

## Endpoints

### Pedidos — `/api/v1/pedidos`
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/pedidos` | cria pedido para um cliente |
| GET | `/pedidos/{id}` | detalhes do pedido |
| GET | `/pedidos?clienteId={id}` | lista pedidos do cliente |
| POST | `/pedidos/{id}/itens` | adiciona item (valida no catálogo) |
| DELETE | `/pedidos/{id}/itens/{produtoId}` | remove item |
| POST | `/pedidos/{id}/confirmacao` | confirma (total com preço do catálogo) |
| DELETE | `/pedidos/{id}` | cancela |

### Pagamentos — `/api/v1/payments`
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/payments` (`{pedidoId}`) | inicia o pagamento de um pedido confirmado |
| GET | `/payments/{paymentId}` | status do pagamento (`paymentId == pedidoId`) |
| POST | `/payments/{paymentId}/callback` | resultado do gateway (webhook, idempotente) |

Erros seguem **RFC 7807 (Problem Details)**.

---

## Ciclo de vida do pedido

```
ABERTO → CONFIRMADO → PAGAMENTO_PENDENTE → PAGAMENTO_APROVADO
                                         ↘ (até 3 rejeições) → CANCELADO
ABERTO / CONFIRMADO / PAGAMENTO_PENDENTE → CANCELADO
```

Eventos de cada transição vão para o **Kafka** (tópico único `pedidos-eventos`, tipo no enum) e ficam registrados na tabela `historico_pedido`.

---

## Testes

```bash
cd order-service
mvn test      # unitários (domínio + use cases + web)
mvn verify    # inclui integração (Testcontainers: PostgreSQL + WireMock)

# mutation testing (domínio, MSI alvo ≥ 90%)
mvn test-compile org.pitest:pitest-maven:mutationCoverage

# teste de carga (precisa da stack no ar e do binário k6)
BASE_URL=http://localhost:8081 TOKEN=<jwt> k6 run k6/script.js
```

- `*Test` → unitários (surefire). `*IT` → integração (failsafe + Testcontainers).
- Os ITs de HTTP usam **WireMock via Testcontainers**, reaproveitando os mapeamentos de `wiremock/mappings/`.

---

## Observabilidade

- **Logs JSON** com `correlationId` (propagado para chamadas HTTP de saída e mensagens Kafka).
- **Métricas** Micrometer/Prometheus + **dashboard Grafana** provisionado (throughput, p50/p95/p99, taxa de erro).
- **Tracing** OpenTelemetry → Jaeger.

---

## CI/CD e deploy

Pipeline em **GitHub Actions** (`.github/workflows/ci.yml`), com jobs encadeados:

| Job | Quando | O que faz |
|---|---|---|
| `build` | push (main/develop) e PRs | `mvn clean verify` (unitários + integração Testcontainers) + **Pitest** + scan **Trivy** |
| `load-test` | idem | sobe a stack e roda o **k6** (falha se estourar os budgets) |
| `publicar-imagem` (**deploy**) | push em `develop`/`main`, **se build + load-test passarem** | builda e publica a imagem Docker no **GHCR**: `ghcr.io/<owner>/order-service:<branch>` e `:<sha>` |
| `abrir-pr-para-main` | push em `develop`, **se build + load-test + deploy passarem** | abre automaticamente um **PR `develop → main`** (idempotente) |

**Fluxo:** trabalha-se na `develop` → CI verde → **imagem publicada (deploy)** → **PR automático** para `main`. Se qualquer etapa falhar, o PR não é aberto. A `main` é protegida por *branch protection* exigindo os checks `build` e `load-test`, então o merge só ocorre com o pipeline verde.

**Rodar a imagem publicada (deploy):**

```bash
docker pull ghcr.io/<owner>/order-service:develop
```

Para subir com a infra, use o `docker-compose.yml` trocando, no serviço `order-service`, a linha `build: ./order-service` por `image: ghcr.io/<owner>/order-service:develop`.

> Requer, uma única vez no repositório: **Settings → Actions → General → "Allow GitHub Actions to create and approve pull requests"** (para o PR automático). O deploy no GHCR usa o `GITHUB_TOKEN` (sem secrets externos).

---

## Estrutura do repositório

```
/
├── docs/architecture.md          # arquitetura, ADRs, modelo de domínio
├── order-service/                # o serviço (pom.xml, src, Dockerfile)
├── wiremock/mappings/            # contratos dos serviços externos (JSON)
├── prometheus/ , grafana/        # observabilidade
├── k6/script.js                  # teste de carga
├── postman/                      # coleção Postman/Insomnia (sucesso + erro)
├── scripts/                      # testar-api.sh (cenários) e seed.sh (massa)
├── docker-compose.yml            # 7 serviços
└── .github/workflows/ci.yml      # build → testes → Pitest → Trivy → k6
```
