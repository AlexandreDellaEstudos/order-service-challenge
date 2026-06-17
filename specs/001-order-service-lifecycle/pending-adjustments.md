# Ajustes Pendentes — Antes de Gerar o Projeto

> Criado em: 2026-06-12
> Aplicar **após** receber e revisar os exemplos do professor.

---

## Ajustes Confirmados (independem dos exemplos)

### A1 — tasks.md: thresholds desatualizados ✅ APLICADO em 2026-06-15

| Task | Campo | Valor anterior | Valor correto |
|------|-------|----------------|---------------|
| T080 | PITest MSI | ≥ 75% | ≥ 90% |
| T081 | JaCoCo cobertura de linhas | ≥ 80% | ≥ 90% |

**Arquivos atualizados**: `tasks.md` (T080, T081) + `constitution.md` (Princípio III).

---

### A2 — tasks.md: K6 sem nenhuma task ✅ APLICADO em 2026-06-15

Tasks adicionadas na Phase 8 — Polish:
- `T085` `k6/scripts/nominal.js`
- `T086` `k6/scripts/peak.js`
- `T087` step `k6-load-test` no CI

---

### A3 — tasks.md: Grafana dashboards sem task ✅ APLICADO em 2026-06-15

Tasks adicionadas na Phase 1 — Setup:
- `T088` provisioning datasource + dashboard
- `T089` `grafana/dashboards/order-service.json`
- `T090` alertas no dashboard

---

### A4 — data-model.md: vocabulário do Payment ✅ APLICADO em 2026-06-15

**Arquivo atualizado**: `data-model.md` — Payment agora descrito como *"Entity com identidade própria e repositório próprio, mas não é Aggregate Root — ciclo de vida acionado exclusivamente pelo Order"*.

---

### A5 — Argon2: removido ✅

Argon2 foi removido de `spec.md` (NFR-007/008 excluídas) e de `plan.md` (dependência e Constitution Check atualizados).

**Justificativa**: Argon2 é para hashing de senha. Este serviço não gerencia credenciais — auth é OAuth2/JWT externo. Não se aplica.

---

## Ajustes Condicionais (dependem dos exemplos do professor)

### B1 — Exemplos do professor como referência

Após receber os exemplos:

1. Comparar estrutura de pacotes com `research.md D-001` — ajustar se o professor usar convenção diferente
2. Comparar configuração do Testcontainers com `T026` (BaseIntegrationTest) — adotar o padrão do exemplo se mais simples
3. Comparar mapeamentos WireMock com `wiremock/mappings/` planejados — checar se o formato JSON é compatível
4. Verificar se o professor usa `@SpringBootTest` ou slice tests (`@WebMvcTest`, `@DataJpaTest`) — alinhar estratégia de teste nos tasks
5. Verificar setup do Kafka no Testcontainers — pode influenciar T050 (KafkaEventPublisher) e T053 (teste de integração Kafka)

---

## Decisões Encerradas

### C1 — Notification Service: HTTP direto ou só Kafka? ✅ DECIDIDO em 2026-06-17

**Decisão**: Manter como está — Notification Service **NÃO é chamado diretamente** pelo order-service.

**Justificativa**: A constituição (Princípio VI) e todos os artefatos (spec.md Assumptions, architecture.md, events.md) são unânimes: o notification-service é downstream Kafka. O mapping WireMock `notifications.json` existe **apenas para documentar o contrato** — não é consumido em runtime pelo order-service. Adicionar uma chamada HTTP direta violaria o Princípio IX (Communication Architecture) e o Princípio VI da constituição.

**Impacto nas tasks**: Nenhum. Tasks já refletem este design (KafkaEventPublisher publica eventos, notification-service é downstream).

**Impacto no WireMock**: `notifications.json` permanece no repositório como documentação de contrato, não como stub ativo.
