# Base de Conhecimento — Módulo Java Avançado / Arquitetura

> **Contexto:** Material consolidado das 9 aulas do módulo, organizado para servir de base de conhecimento durante o desenvolvimento do **order-service** (Clean Architecture, DDD, Hexagonal). Cada aula mantém objetivo, problema gerador, tópicos, exemplos de código, estudo de caso, desafio e conclusão.

---

## Índice

1. [Java 21 Avançado](#aula-1--java-21-avançado)
2. [Spring Boot / REST APIs](#aula-2--spring-boot--rest-apis)
3. [Persistência com JPA & NoSQL](#aula-3--persistência-com-jpa--nosql)
4. [Testes Avançados: JUnit 5, Observabilidade & Carga](#aula-4--testes-avançados-junit-5-observabilidade--carga)
5. [Microsserviços, DDD & Clean Architecture](#aula-5--microsserviços-ddd--clean-architecture)
6. [Docker & CI/CD para Desenvolvedores](#aula-6--docker--cicd-para-desenvolvedores)
7. [Observabilidade em Tempo Real](#aula-7--observabilidade-em-tempo-real)
8. [Segurança: OWASP Top 10 & Zero Trust](#aula-8--segurança-owasp-top-10--zero-trust)
9. [IA Aplicada com Privacidade](#aula-9--ia-aplicada-com-privacidade)
10. [Conexões com o order-service](#conexões-com-o-order-service)

---

## Aula 1 – Java 21 Avançado

**Objetivo:** modernizar código Java para obter menor latência, maior throughput e código mais expressivo, partindo de um baseline legado.

**Problema gerador:** Como reduzir latência em workloads de alto throughput usando recursos modernos da JVM?

### Pattern Matching
Disponível em LTS a partir da JDK 17; `switch` pattern matching definitivo desde a JDK 21.

- `instanceof` patterns — final desde JDK 16 (primeira LTS com suporte: JDK 17).
- `switch` patterns — permanente em JDK 21 (JEP 441), após quatro previews.

**Antes — Java 11 (verificações encadeadas):**
```java
if (o instanceof String s) {
    processText(s);
} else if (o instanceof Number n) {
    processNumber(n);
} else {
    throw new IllegalArgumentException(o.toString());
}
```

**Depois — Java 21 (switch com patterns):**
```java
return switch (o) {
    case String s  -> processText(s);
    case Number n  -> processNumber(n);
    default        -> throw new IllegalArgumentException(o.toString());
};
```

**Aplicação:** roteamento de mensagens ou transformação de DTOs heterogêneos sem casting explícito.
**Atenção:**
- O `switch` agora aceita `null`; cubra explicitamente esse caso ou use `default`.
- Regra de *dominance* — ordens incorretas de padrões resultam em erro de compilação.

### Records
Disponível em LTS a partir da JDK 17. Tipo especial que simplifica objetos imutáveis, automatizando construtor, getters, `equals`, `hashCode` e `toString`.

**Antes — Java 11:**
```java
public class Address {
    private final String street;
    private final String city;

    public Address(String street, String city) {
        this.street = street;
        this.city = city;
    }

    public String street() { return street; }
    public String city() { return city; }

    @Override public boolean equals(Object o) { /*...*/ }
    @Override public int hashCode() { /*...*/ }
}
```

**Depois — Java 17:**
```java
public record Address(String street, String city) {}
```

**Aplicação:** DTOs ou events imutáveis em APIs (ex.: mensagens Kafka).
**Atenção:** não são adequados quando o objeto precisa ser *proxied* (Spring AOP/Hibernate) ou instanciado via reflexão sem construtor.

### Virtual Threads
Estável na LTS JDK 21. Threads leves gerenciadas pela JVM, permitindo milhões de tarefas concorrentes com baixo overhead.

**Antes — Java 11:**
```java
ExecutorService pool = Executors.newFixedThreadPool(100);
pool.submit(() -> handleRequest(socket));
```

**Depois — Java 21:**
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handleRequest(socket));
}
```

**Aplicação:** workloads I/O-bound com milhares de conexões simultâneas (chat servers, gateways HTTP).
**Atenção:** operações que causam *thread pinning* (JNI ou blocos `synchronized` longos) reduzem o benefício.

### Structured Concurrency
Em PREVIEW na LTS JDK 21. Gerencia grupos de tarefas relacionadas de forma mais segura e previsível.

**Antes — Java 11:**
```java
Future<String> u = pool.submit(this::loadUser);
Future<List<Order>> o = pool.submit(this::loadOrders);
return combine(u.get(), o.get());
```

**Depois — Java 21:**
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Supplier<String> user   = scope.fork(this::loadUser);
    Supplier<List<Order>> orders = scope.fork(this::loadOrders);

    scope.join();          // espera todas
    scope.throwIfFailed(); // propaga erros

    return combine(user.get(), orders.get());
}
```

**Aplicação:** orquestrar sub-tarefas dependentes mantendo propagação unificada de erros.
**Atenção:** recurso em preview; requer `--enable-preview` e API sujeita a mudanças.

### Sequenced Collections
Disponível na LTS JDK 21. Interfaces que formalizam sequência com início e fim definidos.

**Antes — Java 11:**
```java
Deque<String> q = new LinkedList<>();
String first = q.peekFirst();
String last  = q.peekLast();
```

**Depois — Java 21:**
```java
SequencedCollection<String> q = new ArrayList<>();
String first = q.getFirst();
String last  = q.getLast();
```

**Aplicação:** caches LRU ou filas internas com acesso constante ao primeiro/último elemento.
**Atenção:** APIs de terceiros que esperam `List`/`Deque` podem não aceitar `SequencedCollection` diretamente.

### Estudo de caso — Refatoração de código legado para Java 21
- **Baseline:** projeto Java 11 com thread pool tradicional, lógica de parsing/verificação e testes JMH (latência p95/p99).
- **Refatoração incremental:** converter classes de transporte para `record`; introduzir pattern matching em validações/roteamento; substituir `ExecutorService` por virtual threads; organizar chamadas I/O dependentes com Structured Concurrency.
- **Benchmark:** rodar JMH e comparar p95, p99 e throughput.
- **Discussão:** ganhos, custos de migração e quando evitar virtual threads (pinning, native calls).

### Desafio
Clonar o repo, criar branch `solucao-<seu-nome>`, aplicar ≥2 otimizações adicionais (SequencedCollection, StringTemplate, refatoração de switch), rodar JMH antes/depois e gerar relatório Markdown (cenário, métricas p95/p99 e throughput, trade-offs). Abrir PR.

### Conclusão
Primeiro passo na modernização: Pattern Matching, Records, Virtual Threads, Structured Concurrency e Sequenced Collections.

---

## Aula 2 – Spring Boot / REST APIs

**Objetivo:** desenvolver um endpoint transacional idempotente, bem versionado e tolerante a falhas, com documentação OpenAPI atualizada. Baseline Spring Boot 3.5 / Java 21.

**Problema gerador:** Como projetar APIs idempotentes e versionadas com resiliência a falhas?

### 1. Spring MVC vs Spring WebFlux
- **Modelo:** imperativo (`@RestController`) vs reativo (`@RestController` ou `RouterFunction`).
- **Thread model:** Servlet container (Tomcat, com opção de virtual threads) vs Netty/Reactor event-loop (com backpressure nativo).
- **Backpressure:**
  - *Identificar:* crescimento de filas internas (`reactor.netty.http.server.pending`), aumento de p95/p99, consumo de heap/GC.
  - *Lidar:* `onBackpressureBuffer`/`onBackpressureDrop`, rate-limiters (Resilience4j), HTTP 429 ou bulkheads para isolar dependências bloqueantes.
- **RouterFunction:** rotas como lambdas (`RouterFunction<ServerResponse>`), fácil composição, testes sem container; no MVC a rota fica em `@RequestMapping`.
- **Desempenho vs preferência:**
  - WebFlux entrega 30–40% mais throughput em cenários I/O-bound (WebSockets, SSE, chamadas externas não bloqueantes) com dezenas de milhares de conexões.
  - MVC é mais simples em carga CPU-bound ou APIs bloqueantes (JDBC clássico). Com virtual threads no Tomcat, MVC atinge latências equivalentes até ~1000 threads.
- **Resumo:** escolha guiada pelo perfil de carga, necessidade de streaming/backpressure e familiaridade da equipe.

### 2. DTO record + Validation
- Contratos de entrada/saída como `record` (JDK 17+) — imutáveis, concisos, compatíveis com `@JsonProperty`.
- **Validação eficiente:** modo fail-fast (`spring.validation.fail-fast=true`); *groups* (`@Validated(Create.class)`) para create/update; validação method-level (`@Validated` no service) para regra de negócio.
- **Validações personalizadas:** `@CPF` → `class CPFValidator implements ConstraintValidator<CPF,String>`; registrar com `@Constraint(validatedBy=…)`.
- **Chain of Responsibility:** componha `ValidationHandler` sequenciais antes do service; cada handler chama `next.handle(request)` apenas se `isValid`. Útil para pipelines extensíveis sem proliferar anotações.
- **Quando usar `ResponseEntity`?** Retorne objeto direto (`ProdutoDTO`) quando status é sempre 200. Use `ResponseEntity` para controlar status, headers (cache, ETag) ou cookies, e para padronizar erro (`ProblemDetail`) em `@ExceptionHandler`.
- **Records como abstrações de payload (granularidade):**
```java
public record ProdutoMinimo(UUID id, String nome) {}
public record ProdutoBasico(UUID id, String nome, BigDecimal preco) {}
public record ProdutoCompleto(UUID id, String nome, BigDecimal preco, List<Estoque> estoque, List<Avaliacao> avaliacoes) {}
```
  - Exponha endpoints distintos ou use content negotiation (`Accept: application/vnd.app.produto-basico+json`) para mapear camadas de detalhe.
- **Mapeamento:** `@RequestBody ProdutoCompleto req + @Valid`; respostas com `ResponseEntity<ProdutoBasico>` (201) ou `ProdutoMinimo` (GET collection) — reduz payload e acoplamento.

### 3. Resiliência, Versionamento & Documentação
- **Idempotency-Key header** + conditional write no repositório.
- **Retries inteligentes:**
  - `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 250))` para serviços síncronos.
  - `WebClient` `retryWhen(Retry.backoff(3, Duration.ofMillis(250)))` no flux reativo.
  - Exponential backoff com jitter; não re-tente erros 4xx nem exceções de validação.
- **Circuit Breaker (Resilience4j):**
  - `@CircuitBreaker(name="fraudeService", fallbackMethod="fraudeOffline")`.
  - Estados CLOSED → OPEN → HALF-OPEN previnem cascata; métrica em `/actuator/metrics/resilience4j.circuitbreaker.calls{kind="failed"}`.
  - Thresholds (failureRate ≥ 50%, slowCallRate ≥ 60%, waitDurationInOpenState = 30s).
- **Versionamento:**
  - URI versioning (`/api/v1/...`) para mudanças incompatíveis; política de suporte (ex.: v1 deprecada após 12 meses).
  - Header versioning (`Accept-Version: 2` ou `X-API-Version: 2`) — URL estável, requer gateway que enrute por header.
  - Media-Type versioning (`application/vnd.app.produto-v2+json`) — ótima para evoluir só a representação.
  - Evite query param (`?version=2`) por dificultar cache em CDNs.
  - Spec separada por versão: `/v3/api-docs/v1`, `/v3/api-docs/v2`; Swagger UI via `?configUrl=/v3/api-docs/v2/swagger-config`.
- **Tratamento global de erros:** `@RestControllerAdvice` + `@ExceptionHandler` convertendo exceções em `ProblemDetail` (RFC 7807) com `type, title, status, detail`.
- **Documentação automática:** SpringDoc OpenAPI 3.1; `springdoc.group-configs` para múltiplas versões; inclua exemplos de erro (429/503) e cabeçalhos `Idempotency-Key`, `Retry-After`.

### Estudo de caso — Endpoint de Transação Financeira (live-coding)
- **Escopo:** `POST /api/v1/transactions` (`{ amount, sourceAccount, targetAccount }`).
- **Idempotência:** cliente envia `Idempotency-Key` único; servidor registra hash + resposta; segunda chamada com mesma chave devolve 200 OK e corpo original, sem duplicar operação.
- **Resiliência:** Chaos Monkey for Spring Boot para falhas intermitentes; `@Retryable(maxAttempts = 3)` ou `WebClient retryWhen`.
- **Documentação:** spec OpenAPI automática; revisar descrição/códigos.
- **Observabilidade:** `/actuator/metrics/http.server.requests`; traçar p95.

### Desafio
`GET /api/v2/transactions/{id}` com WebFlux; fallback via CircuitBreaker (serviço de fraude); atualizar spec com exemplos 429/503; relatório de latência média, throughput sob 50 req/s e estratégias de versionamento.

### Conclusão
MVC vs WebFlux, records como DTO, validação declarativa, controle de exceções, OpenAPI, idempotência e resiliência com chaos engineering.

---

## Aula 3 – Persistência com JPA & NoSQL

**Objetivo:** comparar abordagens relacionais (ACID) e NoSQL (BASE) em alto throughput, analisando trade-offs de consistência, latência e custo operacional.

**Problema gerador:** Como balancear consistência forte e escalabilidade em cenários de alto volume?

### 1. Modelos de Consistência & Escalabilidade
- **ACID vs BASE:** ACID garante atomicidade e integridade (use em finanças, inventário crítico); BASE tolera inconsistências temporárias por throughput (redes sociais, analytics, caching).
- **Teorema CAP:** em partições de rede, sistemas CP preservam consistência sacrificando disponibilidade; sistemas AP permanecem ativos com dados possivelmente desatualizados. Bancos priorizam CP; streaming favorece AP.
- **Ligação com Aula 2 — backpressure no banco ≠ na API:** pool de conexões/`max_connections`, R2DBC reativo, bulkheads para isolar o repositório. Manifesta-se por conexões saturadas e latência crescente; monitore timeouts, aumente pools de forma controlada.

### 2. JPA/Hibernate em profundidade
- **Hibernate 6.4 + PostgreSQL 16:** otimize com fetch join e batching; elimine N+1. Ajustar `hibernate.default_batch_fetch_size` e índices corretos gera 20–30% mais throughput em cargas mistas.
- **Transações:** optimistic vs pessimistic locking; isolamento (READ COMMITTED, REPEATABLE READ). Optimistic para alta concorrência com poucos conflitos; pessimistic protege linhas quentes mas aumenta contenda.
- **WAL (Write-Ahead Log):** replicação assíncrona vs síncrona (demo `pg_stat_replication`). Síncrona evita perda de dados mas eleva latência de escrita; assíncrona favorece throughput. Monitore `replication_lag_bytes`.
- **Testcontainers:** spin-up PostgreSQL + pgBouncer para testes de carga; medir throughput e lock contention; reduzir conexões para validar backpressure do pool.
- **Trade-offs:** consistência forte mas escala vertical; particionamento exige sharding manual ou Citus.

### 3. NoSQL estratégico (MongoDB & Elasticsearch)
- **MongoDB 7:** documento orientado, sharding automático via shard key, transações multi-document desde 4.2 (limitadas a um shard). Útil para catálogos dinâmicos e perfis de usuário.
- **Elasticsearch 8:** full-text, event sourcing de logs/streams, agregações em tempo quase real. Sem garantias de consistência imediata em atualizações simultâneas.
- **Replicação & Failover:** Mongo primário-réplica; ES shards replicados que se realocam automaticamente. Reindexação e lag afetam latência de leitura.
- **Reactive Drivers (Mongo-Reactive, Spring Data Elasticsearch Reactive):** publicam resultados como fluxos non-blocking; liberam threads enquanto aguardam I/O, reduzindo backpressure. Exigem pensamento reativo e tratamento cuidadoso de erros.
- **Trade-offs:** latência de consistência, custos de storage, falta de joins. Sem silver bullet — escolha guiada pelo padrão de acesso.

### Estudo de caso — Postgres + WAL & Spike NoSQL
- Serviço de pedidos gravando 500 req/s em picos de campanha.
- Criar `OrderEntity` no JPA; `hibernate.jdbc.batch_size=50`.
- Análise WAL com `pg_waldump`; ajustar `checkpoint_timeout` e `wal_compression`.
- Chaos test: derrubar réplica e medir sync vs async.
- Spike NoSQL: publicar `OrderPlaced` no MongoDB (coleção particionada por dia) e indexar no Elasticsearch.
- Comparar p95 e divergência de consistência; discutir dupla escrita vs event sourcing completo.

### Desafio
Modelar persistência de Inventário (leitura massiva + escrita concorrente); propor estratégia híbrida (Postgres + Mongo/Elastic) justificada via CAP e latência alvo; docker-compose com Testcontainers; carga de 1.000 req/s + relatório comparativo.

### Conclusão
Nenhuma tecnologia isenta dos trade-offs entre consistência, disponibilidade e desempenho. PostgreSQL/Hibernate = transações fortes mas escala vertical; MongoDB/Elasticsearch = escala horizontal e consultas flexíveis à custa de consistência imediata. Alinhe o modelo de dados ao domínio e ao SLA.

---

## Aula 4 – Testes Avançados: JUnit 5, Observabilidade & Carga

**Objetivo:** estruturar testes que combinam qualidade de código (mutantes mortos) e confiabilidade de performance (load profile "blackfriday").

**Problema gerador:** Como prevenir regressões críticas e validar desempenho de picos sazonais?

### 1. JUnit 5 além do básico
- **Dynamic Tests & `@TestFactory`:** gerar cenários a partir de CSV ou API externa em tempo de execução, sem duplicar código.
- **Tagging/Filtering (`@Tag("slow")`):** segmenta rápidas/lentas/críticas para execução paralela seletiva no CI.
- **Timeout Assertions (`assertTimeoutPreemptively`):** processing-time budget check; falha rápido em operações que extrapolam o orçamento de tempo.
- **Mutation Testing (Pitest 1.16):** métrica MSI (Mutation Score Indicator), alvo ≥ 85%. Injeta mutações no bytecode para validar robustez lógica; eleva tempo de build (trade-off).

### 2. Testcontainers & WireMock
- **Testcontainers:** spin-up de PostgreSQL, Mongo, Kafka para integração real em CI; ambiente idêntico entre devs e pipelines.
- **Reuse flag:** reaproveita volumes/imagens entre execuções (cuidado com vazamento de estado compartilhado).
- **WireMock 3:** gravar/replay contratos de terceiros; injetar latência ou 503 para testar resiliência a falhas externas.
- **Contract drift:** snapshots versionados detectam mudanças inesperadas de contrato e falham o pipeline antes de chegar à produção.

### 3. Load Testing com k6/Locust
- **Perf budgets:** p95 ≤ 300 ms, erro < 1% — contrato técnico e critério de aprovação de build.
- **Script "blackfriday":** ramp-up 50 → 1.000 rps em 3 min, plateau de 5 min; identifica gargalos de ramp-up, warm-up de caches e limitação de thread pools.
- **Métricas via Prometheus + Grafana:** alertas quando `http_req_duration{quantile="0.95"} > budget`.
- **Integração CI:** "fail the build" se budget estourar; histórico de benchmarks por commit.

### Estudo de caso — TDD Relay & Carga Sazonal
- TDD Relay: grupos alternam teste → implementação → mutação até MSI ≥ 85%.
- Infra local: `@Container` PostgreSQL 16 + Redis Testcontainer; serviço externo simulado por WireMock.
- Carga: rodar k6 `blackfriday.js`; capturar dashboard e detectar gargalos (GC, DB connection pool).
- Refactor: index-tuning ou caching; rerodar suíte para provar regressão zero + ganho.

### Desafio
Teste de mutação em módulo promocional elevando MSI a 90%; perfil k6 "flash-sale" (2.000 rps pico) + gatilho de alerta; documentar gargalo, mitigação e resultado pós-fix.

### Conclusão
Mutação expõe falhas lógicas, Testcontainers traz realidade de produção, WireMock blinda contra contratos flutuantes e k6/Locust garantem SLA em datas críticas.

---

## Aula 5 – Microsserviços, DDD & Clean Architecture

**Objetivo:** definir limites de contexto (Bounded Contexts) que evitam acoplamento excessivo e suportam mudanças tecnológicas sem reescrita de regras de negócio.

**Problema gerador:** Como definir limites de contexto para evitar acoplamento excessivo?

### 1. DDD Tactical Patterns & Bounded Contexts
- **Entities, Value Objects, Aggregates:** modelam o núcleo. Value Objects → imutabilidade; Aggregates → fronteiras de consistência (concorrência segura, persistência focada no Aggregate Root).
- **Repository & Factory:** repositórios encapsulam acesso a dados e transações, libertando o domínio de detalhes técnicos; fábricas centralizam criação de objetos complexos preservando invariantes (facilita testes in-memory).
- **Context Map:** Shared Kernel (código comum), Customer/Supplier (contrato explícito), Anti-Corruption Layer (tradução, evita vazamento de modelo entre equipes).

### 2. Clean / Hexagonal Architecture
- **Port-Adapter:** domínio no centro, tecnologias nas bordas; ports = use cases, adapters traduzem protocolos. Trocar banco/transporte não afeta o core.
- **Dependency Rule (Uncle Bob):** dependências sempre do exterior para o interior; camadas internas desconhecem frameworks externos (preserva testabilidade e longevidade).
- **Screaming architecture:** o código grita o domínio, não o framework; pacotes e nomes revelam o negócio antes da tecnologia.

### 3. Orquestração, Saga & Resiliência
- **Saga vs Orchestrator:** Sagas no domínio preservam encapsulamento (simplicidade em domínio único); orchestrator centraliza fluxo quando múltiplos contextos exigem visibilidade/monitoramento central.
- **API Gateway:** consolida auth, roteamento, versionamento, rate-limit; desacopla clientes de mudanças internas. Pode virar ponto único de falha se não replicado/monitorado.
- **Circuit Breaker e Compensation:** CB corta tráfego ante falhas repetidas (half-open testa recuperação); transações compensatórias desfazem ações já executadas numa Saga (consistência eventual). Adicionam complexidade de monitoramento e reversão.

### Estudo de caso — Event Storming + Microservice Port-Adapter
- Event Storming express do domínio "Pedido & Pagamento".
- Bounded Contexts **Order** e **Billing**; mensagens de integração (`OrderPlaced`, `PaymentConfirmed`).
- Serviço Order em hexagonal: camadas Domain, Application, Infrastructure; entrada REST via adapter HTTP; saída Kafka adapter publica `OrderPlaced`.
- Saga orchestrator para consistência entre contextos; simular falha no Billing e aplicar compensating transaction.
- Medir impacto de Circuit Breaker (Resilience4j) e observabilidade nos eventos.

### Desafio
Extrair microserviço **Inventory** do contexto Order; aplicar Clean Architecture completa (ports & adapters, domain isolation); integrar via domain events com idempotência por saga de compensação; documentar trade-offs (autonomia, latência, complexidade).

### Conclusão
Microsserviços nascem de limites de domínio claros, não de decisões de framework. Clean Architecture desacopla regra de negócio de tecnologia; Sagas, gateways e circuit breakers completam a resiliência.

---

## Aula 6 – Docker & CI/CD para Desenvolvedores

**Objetivo:** criar Dockerfiles multi-stage eficientes, configurar matrizes de build/teste e publicar artefatos versionados imutáveis, com SBOM e scan de vulnerabilidades.

**Problema gerador:** Como manter pipelines de build confiáveis e imagens enxutas?

### 1. Dockerfile Multi-Stage & Slim Images
- **Estrutura básica:** builder (JDK + Maven) → runtime (JRE distroless). Separa compilação do runtime; reduz superfície de ataque e habilita cache incremental.
- **Técnicas:** `--chown`, `COPY --from`, `RUN --mount=type=cache,target=/root/.m2`. Reduzem camadas, previnem arquivos root e aceleram builds preservando dependências Maven.
- **Meta slim-image < 120 MB:** medir com `docker image ls` e `docker build --progress=plain`; se distroless, remover fontes e camadas extras.

### 2. GitHub Actions Matrix & SemVer Tags
- **Workflow matrix:** versões JDK 17/21, Ubuntu/Alpine — garante compatibilidade antes do merge.
- **Cache layers:** `actions/cache` + buildx; acelera builds em PR.
- **SemVer (`v1.3.0`, `v1.3`, `latest`) e tags imutáveis:** nunca re-tag a mesma hash (reprodutibilidade).

### 3. SBOM & Security Scans
- **SBOM (CycloneDX):** `mvn cyclonedx:makeAggregateBom` — inventário formal de componentes/versões para compliance e auditoria.
- **Scan de CVEs:** Trivy ou Grype; política "fail on critical".
- **Publicar relatório** como artefato e comentário em PR; alertar quando CVE ≥ High.

### Estudo de caso — Pipeline em Par + Desafio Slim-Image
- Dockerfile multi-stage para app Spring Boot; `docker-build.yml` com buildx + cache + matrix.
- Meta ≤ 120 MB com distroless; remover fontes de debug.
- Passo Trivy (falhar em critical); publicar tag `v{BUILD_NUMBER}` e SBOM anexado aos releases.

### Desafio
Otimizar Dockerfile (-20% do tamanho); matrix JDK 17 + 21 com testes de unidade; passo SBOM + Trivy (erro em CVE crítico); post-mortem das decisões de corte, trade-offs de debug e tempo de build.

### Conclusão
Imagens enxutas reduzem cold-start e superfície de ataque; pipelines confiáveis garantem integridade a cada commit. Controlando cache, tags, SBOM e CVEs, devs viram parte ativa da segurança e desempenho.

---

## Aula 7 – Observabilidade em Tempo Real

**Objetivo:** instrumentar logs, métricas e traces, montar dashboards acionáveis e conduzir Root Cause Analysis (RCA) diante de falhas reais injetadas.

**Problema gerador:** Como detectar anomalias em transações críticas em tempo real?

### 1. Logs Estruturados & CorrelationID
- **JSON logs** com `logback-json` + MDC; parse consistente em Loki/Elasticsearch e correlação rápida em incidentes.
- **CorrelationID (`X-Request-Id`)** propagado por filtro Servlet/WebFlux; amarra logs, métricas e trace de uma transação (drill-down rápido).
- **Prós/Contras:** simples de adotar; alto volume gera custo de storage — rotação e amostragem obrigatórias.

### 2. Métricas com Micrometer, Prometheus & Grafana
- **Micrometer:** fachada neutra; instrumente uma vez (`@Timed`) gerando `Timer`, `Counter`, `Gauge` para qualquer backend.
- **Prometheus:** coleta via pull; `histogram_quantile` abastece latência p95; PromQL para alertas de SLO e capacidade.
- **Grafana:** dashboards e alertas; variáveis para drill-down; janelas de silêncio evitam ruído em deploys.
- **Trade-offs:** resolução limitada ao scrape interval; alta cardinalidade prejudica desempenho — higiene de labels, recording rules e retenção ajustável.

### 3. Tracing Distribuído com OpenTelemetry
- **Auto-instrumentação** para Spring Boot; spans em HTTP, JDBC, Kafka sem alterar código.
- **Exportação OTLP** → Jaeger/Tempo/Grafana Cloud; trocar backend = mudar endpoint.
- **Causality chain:** árvore de spans no Jaeger; filtros destacam o caminho crítico, acelerando RCA.
- **Prós/Contras:** ótima para RCA cross-service; exige storage e sampling tuning (10% tráfego normal, 100% erros críticos).

### Estudo de caso — Chaos Injection & Dashboarding
- Injetar falha 30% em `/payments` com Chaos Monkey.
- Observar spike de 5xx no Prometheus e traço anômalo no Jaeger.
- Painel Grafana: p95, rate de erro e trace exemplar.
- Conduzir RCA e documentar cadeia causa-raiz.

### Desafio
CorrelationID end-to-end em app demo; alertas Grafana (p95 > 500 ms ou erro > 2% por 1 min); relatório RCA de falha induzida com print de trace e dashboard.

### Conclusão
Observabilidade une logs estruturados, métricas e tracing para transformar dados brutos em insight acionável, seguindo o rastro até o serviço culpado.

---

## Aula 8 – Segurança: OWASP Top 10 & Zero Trust

**Objetivo:** detectar, corrigir e prevenir vulnerabilidades críticas, implementando hardening de endpoints e pipeline contínuo de análise de CVEs. Stack: Spring Security 6, JWT/OIDC, Zero Trust.

**Problema gerador:** Como proteger APIs contra os 10 maiores vetores de ataque da OWASP?

### 1. Spring Security 6 & Keycloak OIDC
- **Resource server:** `spring-boot-starter-oauth2-resource-server` + JWT; valida assinatura e expiração, dispensa sessão e simplifica escala horizontal.
- **SSO com Keycloak:** scopes e granularidade (`hasAuthority('SCOPE_orders.read')`); menor privilégio e tokens curtos.
- **Hardening:** CORS restritivo, headers `Content-Security-Policy`, `X-Frame-Options` (bloqueiam XSS, clickjacking, requisições indevidas).
- **Zero Trust:** validar token em cada requisição; não confiar em redes internas (presume rede hostil, limita movimento lateral).

### 2. OWASP Top 10 ↔ Controles de Código
- Mapear A01-Broken Access Control até A10-SSRF a contramedidas Spring (method security, CSRF, rate-limit, input validation) num checklist com testes automatizados no pipeline.
- **Secure Development Lifecycle:** SAST (CodeQL), DAST (OWASP ZAP), secret scanning no CI; falhas críticas bloqueiam merge.
- **Princípios:** least privilege, fail-secure defaults, secure by default.

### 3. CVE Triage & Patching Pipeline
- **Monitorar CVEs:** Dependabot (PRs de atualização) + Trivy (scan de imagem/SBOM); reduz MTTR e bloqueia deploy vulnerável.
- **SemVer + feature toggle:** isola patches críticos em branches de manutenção, ativação gradual sem big-bang.
- **RedTeam vs BlueTeam:** explorar CVE prática (Log4Shell) e aplicar patch + WAF rule.

### Estudo de caso — Lab Exploit Controlado & Hardening
- RedTeam: SQLi e JWT tampering contra `/orders`.
- BlueTeam: analisa logs JSON + trace, detecta payload, aplica `@PreAuthorize`.
- Integra Keycloak como RP; valida audience e expiration.
- Patch + redeploy em CI com Trivy scan; mostrar diff no dashboard.

### Desafio
Spring Security com OAuth2 OIDC + scopes finos; rate-limit (Bucket4j) nos endpoints A07-IDOR; injetar exploit SSRF via RedTeam e entregar relatório BlueTeam (vulnerabilidade, correção, evidência).

### Conclusão
API segura nasce de Zero Trust, secure by design e atualização contínua. Mapear OWASP Top 10 em controles Spring, integrar OIDC e monitorar CVEs reduz janelas de ataque e custo de correção tardia.

---

## Aula 9 – IA Aplicada com Privacidade

**Objetivo:** prototipar assistentes internos que consultam bases privadas, garantindo governança de dados e conformidade. Técnicas: Prompt Engineering, SpringAI, RAG e modelos locais.

**Problema gerador:** Como incorporar IA generativa preservando privacidade de dados sensíveis?

### 1. Prompt Engineering & Ética
- **Estruturas system/user/assistant; few-shot vs zero-shot.** Few-shot solidifica contexto com exemplos; zero-shot dá flexibilidade quando dados de referência são escassos.
- **Red-teaming:** prompts adversários para revelar vazamentos; resultados retroalimentam filtros, políticas de acesso e ajustes de prompt.
- **Avaliação P0–P3 de risco ético** (bias, alucinação, privacidade): P0 bloqueio imediato, P1 correção urgente.

### 2. SpringAI & Integração de Modelos
- **SpringAI 1.0 — `AiClient` pluggable** (OpenAI, HuggingFace, Ollama): trocar provedor por configuração, sem SDKs proprietários no código.
- **Router de modelo local (Ollama/Mistral) + tokenização no servidor:** seleciona modelo por custo/latência/confidencialidade; evita vazar prompts para provedores externos.
- **Audit logs restritos + cifragem de prompts via Vault:** logs retêm só metadados, garantindo rastreabilidade sem expor conteúdo.

### 3. Retrieval-Augmented Generation & Modelos Locais (MCP)
- **Pipeline:** Embed → Index (PGVector) → Retrieve → Compose Answer. Nenhum dado sensível sai do banco e a latência permanece baixa.
- **Chunking & semantic search:** chunks de 200–500 tokens com bordas semânticas; busca semântica com embeddings locais (SimCSE/BGE) supera busca por palavra-chave.
- **Benefícios de modelos locais:** latência <100 ms, sem custo por chamada, soberania de dados, fine-tuning sem expor informação confidencial.

### Estudo de caso — Chatbot de Tickets Internos
- PostgreSQL + pgvector com base de tickets (5k registros).
- Indexar embeddings com SpringAI VectorStore.
- Endpoint `/chatbot`: recebe prompt, chama RAG local (Mistral 7-B), retorna resposta com citações.
- Testar vazamento via prompt injection; ajustar guardrails (regex filter, system prompt); avaliar precision@5 e latência.

### Desafio
Role-based access no chatbot (Spring Security); cache criptografado de embeddings; revisão ética (checklist bias, privacidade, logging) + relatório + patch.

### Conclusão
IA generativa segura exige Prompt Engineering consciente, modelos locais quando possível e arquitetura RAG para limitar exposição. SpringAI + guardrails éticos geram valor sem renunciar a privacidade e conformidade.

---

## Conexões com o order-service

Pontos do material que se aplicam diretamente ao desafio (Clean Architecture + DDD + Hexagonal, deadline 19/06):

- **Aula 5 é o núcleo arquitetural:** Bounded Contexts Order/Billing, camadas Domain/Application/Infrastructure, ports & adapters, e a decisão Saga vs Orchestrator para consistência entre contextos. O exemplo `OrderPlaced` via Kafka adapter espelha o desenho do serviço.
- **Concorrência e locking (Aula 3):** optimistic vs pessimistic locking e isolamento. Reforça o argumento de **lock pessimista** para o caso de contas compartilhadas (escrita concorrente real no mesmo pedido) — usar na justificativa de arquitetura.
- **Idempotência (Aula 2):** `Idempotency-Key` + conditional write é exatamente o padrão a aplicar no endpoint de criação de pedido, evitando duplicação em retries.
- **WireMock (Aula 4):** mockar serviços HTTP externos (ex.: Billing/fraude) nos testes de integração — alinhado ao que você já planejava. Testcontainers para PostgreSQL real no CI.
- **DTOs como records (Aulas 1 e 2):** records como Value Objects e payloads de granularidade variável; lembrar que entidades mutáveis não viram record (proxy Hibernate).
- **Resiliência (Aulas 2 e 5):** Circuit Breaker + retries com backoff/jitter e compensating transactions na Saga para falhas no Billing.
- **Observabilidade (Aula 7):** CorrelationID end-to-end e métricas p95 desde cedo facilitam o RCA e a defesa do design.
- **Segurança (Aula 8):** se houver auth, resource server JWT + scopes finos (`SCOPE_orders.read`) e rate-limit nos endpoints sensíveis.
