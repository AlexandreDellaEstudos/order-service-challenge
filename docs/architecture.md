# Arquitetura — Order Service (Plataforma de Pedidos)

**Versão**: 2.0.0 | **Data**: 2026-06-18

> Este documento reflete a **implementação real** do `order-service` (não um plano). Código em `order-service/`, em **português**, Java 21 + Spring Boot 3.3, Clean/Hexagonal Architecture.

---

## Visão Geral

Backend de um marketplace para processar pedidos de ponta a ponta — da criação à confirmação do pagamento. Problemas resolvidos:

| Problema | Solução implementada |
|---|---|
| Pedidos criados sem itens nunca finalizados | Confirmação exige **≥ 1 item** |
| Pagamento processado em duplicidade | **Idempotency-Key** nas mutações + callback idempotente por estado |
| Sem rastreabilidade de estado | Máquina de estados explícita + **tabela de histórico** de transições |
| Clientes inválidos chegando ao pagamento | Validação de cliente **ativo** na criação (HTTP ao Customer Service) |
| Gateway instável | Erro `503` isolado por adapter (`ServicoExternoIndisponivelException`) |
| Concorrência sobre o mesmo pedido | **Optimistic Locking** (`@Version`) → HTTP 409 |
| Abuso de requisições | **Rate limiting** in-process (429) |

---

## Escopo

Apenas o **`order-service`** é implementado. Cliente, Catálogo, Gateway de Pagamento e Notificação são **externos**, simulados via **WireMock** (mapeamentos em `wiremock/mappings/`).

```
┌──────────────────────────────────────────────────────────────┐
│                      order-service  ✅                        │
│                                                               │
│   API REST (/api/v1)                                          │
│        │                                                      │
│        ▼                                                      │
│   use cases ──► ports ──► adapters HTTP ──► WireMock          │
│        │                    (Cliente, Catálogo, Pagamento)    │
│        │                                                      │
│        ├──► PostgreSQL (pedidos, itens, idempotência, histórico)
│        └──► Kafka (1 tópico: "pedidos-eventos", tipo via enum)│
└──────────────────────────────────────────────────────────────┘
```

---

## Bounded Contexts

| Contexto | Serviço | Situação | Contrato consumido |
|---|---|---|---|
| **Order** | `order-service` | ✅ implementado | — |
| **Customer** | customer-service | 🔵 WireMock | `GET /customers/{id}` → 200 ativo / 422 bloqueado / 404 inexistente |
| **Catalog** | catalog-service | 🔵 WireMock | `GET /products/{id}` → 200 `{name, price, available}` / 422 / 404 |
| **Payment** | payment-gateway | 🔵 WireMock | `POST /payments` (inicia cobrança) → 200 / 503; resultado chega via callback |
| **Notification** | notification-service | 🔵 downstream Kafka | consome eventos; não é chamado diretamente |

> **Pagamento é externo.** O `order-service` apenas aciona o gateway e recebe o callback de aprovação/rejeição — **não há agregado `Payment` interno**. O pedido guarda só os estados de pagamento e a contagem de tentativas.

---

## Clean / Hexagonal Architecture

Dependências apontam **sempre para dentro**. O `domain/` é Java puro (verificado: não importa Spring, JPA, infra ou api).

```
api (REST)            →  controllers, DTOs (record), RFC 7807
   │
application           →  use cases (POJO), ports de saída, ACL
   │
domain                →  Pedido (agregado), ItemPedido, StatusPedido,
                          EventoPedido + TipoEventoPedido, exceções
   ▲
infrastructure        →  adapters: persistência (JPA), HTTP (WireMock),
                          Kafka, histórico, idempotência, segurança, observabilidade
```

### Estrutura de pacotes (`br.com.meli.order`)

```
domain/
  pedido/      Pedido, ItemPedido, StatusPedido
  evento/      EventoPedido (record), TipoEventoPedido (enum)
  excecao/     ExcecaoDeDominio + OperacaoInvalida, PedidoNaoEncontrado,
               ItemNaoEncontrado, ClienteInvalido, PedidoAbertoJaExiste,
               ProdutoIndisponivel, ProdutoNaoEncontrado, ServicoExternoIndisponivel
application/
  CriarPedido / AdicionarItem / RemoverItem / ConfirmarPedido / CancelarPedido /
  IniciarPagamento / ProcessarCallbackPagamento / BuscarPedido / ListarPedidosPorCliente  (UseCase + Command)
  acl/         SituacaoCliente, ProdutoCatalogo, ResultadoPagamento
  port/out/    PedidoRepository, ClientePort, CatalogoPort, PagamentoGatewayPort,
               PublicadorDeEventos, HistoricoPedidoPort
infrastructure/
  persistencia/  PedidoEntity, ItemPedidoEmbeddable, PedidoJpaRepository, PedidoRepositorioJpaAdapter
  cliente/       ClienteHttpAdapter
  catalogo/      CatalogoHttpAdapter
  pagamento/     PagamentoGatewayHttpAdapter
  messaging/     KafkaPublicadorDeEventos
  historico/     HistoricoPedidoEntity/Repository/JpaAdapter
  idempotencia/  ChaveIdempotenciaEntity/Repository, IdempotenciaFilter
  seguranca/     SecurityConfig, RateLimitFilter
  observabilidade/ CorrelationIdFilter, PropagacaoCorrelationIdInterceptor
  config/        UseCaseConfig, OpenApiConfig
api/
  PedidoController, PagamentoController, ManipuladorDeErros (RFC 7807),
  PedidoResponseMapper, PagamentoResponseMapper, dto/ (records)
OrderServiceApplication
```

**Decisões:** o domínio usa **classes** (entidade `Pedido` com identidade própria); `record` é reservado para DTOs/mensagens. Mapeamento entre camadas é **manual** no adapter (sem MapStruct).

---

## Modelo de Domínio

**Pedido** (agregado raiz, imutável — cada operação retorna nova instância):
```
Pedido { id: Long, clienteId: String, itens: List<ItemPedido>,
         status: StatusPedido, valorTotal: BigDecimal,
         tentativasPagamento: int (0..3), criadoEm: Instant }
```

**ItemPedido** (Value Object):
```
ItemPedido { produtoId, nomeProduto, quantidade (>0), precoUnitario }
```
> O preço entra como `0` na adição do item e é definido na **confirmação** (preço atual do catálogo).

---

## Máquina de Estados

```
                POST /pedidos
                     │
                     ▼
                  ABERTO ───── DELETE /pedidos/{id} ─────►┐
                     │                                    │
        POST /pedidos/{id}/confirmacao                    │
                     ▼                                    │
                 CONFIRMADO ─── DELETE /pedidos/{id} ────►┤
                     │                                    │
        POST /payments (iniciar)                          │
                     ▼                                    │
            PAGAMENTO_PENDENTE ─ DELETE /pedidos/{id} ───►┤
               │           │                              ▼
   callback    │           │ callback                 CANCELADO
   APROVADO    │           │ REJEITADO               (terminal)
               ▼           ├─ tentativas < 3 → CONFIRMADO
       PAGAMENTO_APROVADO  └─ tentativas = 3 → CANCELADO
          (terminal)
```

Transição inválida → `OperacaoInvalidaException` → HTTP 422. Conflito de concorrência → HTTP 409.

---

## API

Base versionada `/api/v1`. Erros em **RFC 7807** (`ProblemDetail`). Mutações aceitam `Idempotency-Key`.

### Pedidos — `/api/v1/pedidos`
| Método | Endpoint | Escopo |
|---|---|---|
| POST | `/pedidos` | `orders:write` |
| GET | `/pedidos/{id}` | `orders:read` |
| GET | `/pedidos?clienteId={id}` | `orders:read` |
| POST | `/pedidos/{id}/itens` | `orders:write` |
| DELETE | `/pedidos/{id}/itens/{produtoId}` | `orders:write` |
| POST | `/pedidos/{id}/confirmacao` | `orders:write` |
| DELETE | `/pedidos/{id}` (cancelar) | `orders:write` |
| POST | `/pedidos/{id}/pagamento` | `orders:write` |
| GET | `/pedidos/{id}/pagamento` | `orders:read` |
| POST | `/pedidos/{id}/pagamento/callback` | público (webhook) |

### Pagamentos (nomenclatura literal do desafio) — `/api/v1/payments`
| Método | Endpoint | Escopo |
|---|---|---|
| POST | `/payments` (body `{pedidoId}`) | `orders:write` |
| GET | `/payments/{paymentId}` | `orders:read` |
| POST | `/payments/{paymentId}/callback` | público (webhook) |

> `paymentId == pedidoId` (não há agregado Payment). Swagger UI em `/swagger-ui.html`.

---

## Regras de Negócio

- **Cliente** ativo e existente (HTTP ao WireMock); bloqueado/inexistente → rejeitado; no máx. 1 pedido `ABERTO` por cliente.
- **Itens**: produto deve existir/estar disponível (catálogo); quantidade > 0; mesmo produto **incrementa** (não duplica); remover inexistente → erro.
- **Confirmação**: ≥ 1 item; **total calculado na confirmação** com preço do catálogo; depois de confirmado não altera itens.
- **Pagamento**: só para `CONFIRMADO`; iniciar é idempotente; aprovado → `PAGAMENTO_APROVADO`; rejeitado → volta a `CONFIRMADO` até a **3ª tentativa**, que **cancela**; callback idempotente por estado.
- **Cancelamento**: permitido em `ABERTO`/`CONFIRMADO`/`PAGAMENTO_PENDENTE`; proibido após aprovado; cancelado é imutável.

---

## Idempotência

Tabela `chaves_idempotencia` (`chave` PK, `status`, `corpo_resposta`, `criado_em`). O `IdempotenciaFilter` intercepta `POST`/`DELETE` com header `Idempotency-Key`: na 1ª vez processa e grava a resposta; em repetições, devolve a resposta gravada sem reexecutar.

## Concorrência

`@Version` na `PedidoEntity`. Conflito de escrita concorrente → `OptimisticLockingFailureException` → **HTTP 409**.

---

## Eventos de Domínio (Kafka)

**Decisão:** todos os eventos vão para **um único tópico** `pedidos-eventos`, com o tipo no **enum** `TipoEventoPedido` (`PEDIDO_CONFIRMADO`, `PAGAMENTO_APROVADO`, `PAGAMENTO_REJEITADO`, `PEDIDO_CANCELADO`) — **sem tópico/partição por tipo de resultado**. A chave da mensagem é o id do pedido (ordenação por pedido) e o `correlationId` viaja como header Kafka.

`EventoPedido` (record): `pedidoId, clienteId, tipo, statusResultante, valorTotal, ocorridoEm`.

## Histórico de Transições

Cada transição relevante (confirmar, aprovar, rejeitar, cancelar) grava em `historico_pedido` (`pedido_id, tipo_evento, status_resultante, valor_total, correlation_id, ocorrido_em`) — rastreabilidade completa do ciclo de vida.

---

## Segurança

- **JWT** (OAuth2 Resource Server, HS256). O segredo fica em `app.security.jwt.secret` — o valor no repositório é **placeholder de dev**; em produção deve ser **injetado via env `APP_JWT_SECRET`** (secret manager / GitHub Secret), com **≥ 32 bytes**, e **nunca commitado**.
- **Escopos**: `orders:read` (GET), `orders:write` (POST/DELETE). Callbacks de pagamento são públicos (webhook). Actuator/Swagger liberados.
- **Rate limiting**: `RateLimitFilter` (janela por minuto/IP → 429); limite via `app.rate-limit.requisicoes-por-minuto` (default 120).
- **Headers de segurança** padrão do Spring Security; validação de entrada com `jakarta.validation`; erros sem stack trace (ProblemDetail).

## Observabilidade

- **Logs JSON** (logback + logstash-encoder) com `correlationId` no MDC.
- **CorrelationId**: `CorrelationIdFilter` lê/gera `X-Correlation-Id` (inbound) e o `PropagacaoCorrelationIdInterceptor` + header Kafka **propagam** nas chamadas de saída.
- **Métricas**: Micrometer → Prometheus em `/actuator/prometheus`.
- **Tracing**: OpenTelemetry (OTLP) → **Jaeger**.
- **Grafana**: datasource + dashboard provisionados (throughput, p50/p95/p99, taxa de erro).

---

## Banco de Dados

PostgreSQL 16 + Flyway (migrations versionadas):

```
V1  pedidos (id, cliente_id, status, valor_total, tentativas_pagamento, criado_em, versao)
    itens_pedido (@ElementCollection: pedido_id, produto_id, nome_produto, quantidade, preco_unitario)
V2  chaves_idempotencia (chave, status, corpo_resposta, criado_em)
V3  historico_pedido (id, pedido_id, tipo_evento, status_resultante, valor_total, correlation_id, ocorrido_em)
```

---

## Testes

| Tipo | Ferramenta | O que cobre |
|---|---|---|
| Unitário — domínio | JUnit 5 | `Pedido` (máquina de estados, regras), `ItemPedido`, `EventoPedido`, exceções |
| Unitário — use cases | JUnit 5 + fakes | fluxos de aplicação com ports falsos |
| Integração — adapters HTTP | **WireMock via Testcontainers** lendo `wiremock/mappings/` | `ClienteHttpAdapterIT`, `CatalogoHttpAdapterIT` |
| Integração — persistência | Testcontainers (PostgreSQL 16) | `PedidoRepositorioJpaAdapterIT` |
| Integração — E2E | `@SpringBootTest` + Postgres + WireMock | fluxo criar→item→confirmar→pagamento; segurança; idempotência |
| Mutation | **Pitest** | domínio — **MSI 94%** (threshold 90) |
| Carga | **K6** | budgets p95 < 500ms / p99 < 900ms / erro < 1% |

`mvn test` roda unitários (surefire); `mvn verify` inclui os ITs (failsafe + Testcontainers). Nomenclatura: `*Test` = unitário, `*IT` = integração.

---

## Infra Local (Docker Compose) — 7 serviços

| Serviço | Porta | Finalidade |
|---|---|---|
| `order-service` | 8081→8080 | API |
| `postgres` | 5432 | Banco (com healthcheck) |
| `kafka` | 9092 | Mensageria (KRaft) |
| `wiremock` | 8080 | Serviços externos simulados |
| `jaeger` | 16686 | Tracing UI |
| `prometheus` | 9090 | Métricas |
| `grafana` | 3000 | Dashboard |

## CI — GitHub Actions

`build` (`mvn clean verify`) → `Pitest` → `Trivy` (scan FS) → job `load-test` (sobe a stack, gera JWT, roda `k6`).

---

## Decisões de Design (ADRs)

- **ADR-01 — Pedido como único agregado; pagamento externo.** Não há agregado `Payment`; o gateway é externo (port + adapter WireMock). Endpoints `/payments` são uma fachada order-centric (`paymentId == pedidoId`).
- **ADR-02 — Domínio em classe, DTO em record.** Entidade tem identidade própria; igualdade por valor (de record) seria incorreta para o agregado.
- **ADR-03 — Mapeamento manual (sem MapStruct).** O domínio é imutável com accessors fluentes e construtor privado; MapStruct exigiria configuração extra. Mapeamento explícito no adapter é mais simples.
- **ADR-04 — 1 tópico Kafka + enum.** Em vez de um tópico/partição por tipo de resultado, um único tópico com o tipo no enum — mantém ordenação por pedido e simplifica a topologia.
- **ADR-05 — IDs `Long` (IDENTITY).** Simplicidade; sem necessidade de UUID distribuído neste escopo.
- **ADR-06 — Optimistic Locking.** `@Version` → 409; conflitos são raros, evita contenção do pessimistic.
- **ADR-07 — Idempotência via banco.** Tabela `chaves_idempotencia` (sem Redis), reaproveitando o PostgreSQL já presente.
- **ADR-08 — WireMock como contrato vivo.** Os JSON de `wiremock/mappings/` são a fonte única — usados no Docker Compose **e** nos testes (Testcontainers).
- **ADR-09 — Rate limiting in-process.** `RateLimitFilter` simples (janela/min por IP). Em produção real poderia migrar para o API Gateway; aqui foi implementado no serviço para cobrir o controle OWASP do desafio.
- **ADR-10 — Resiliência simples (sem Circuit Breaker).** Erros 5xx/timeout dos serviços externos viram `ServicoExternoIndisponivelException` → 503. Circuit Breaker (Resilience4j) é evolução futura natural.

---

## Estrutura do Repositório

```
/
├── docs/architecture.md          ← este documento
├── order-service/                ← único serviço (pom, src, Dockerfile)
├── wiremock/mappings/            ← contratos dos serviços externos (JSON)
├── prometheus/prometheus.yml
├── grafana/provisioning/         ← datasource + dashboard
├── k6/script.js                  ← teste de carga
├── docker-compose.yml            ← 7 serviços
├── README.md
└── .github/workflows/ci.yml
```
