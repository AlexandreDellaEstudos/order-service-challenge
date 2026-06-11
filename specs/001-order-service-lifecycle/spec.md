# Feature Specification: Order Service — Ciclo de Vida Completo de Pedidos

**Feature Branch**: `001-order-service-lifecycle`

**Created**: 2026-06-10

**Status**: Draft

**Input**: Implementar o order-service de uma plataforma de e-commerce com ciclo de vida completo de pedidos, desde a criação até o pagamento, com controle de estados, idempotência e integração com serviços externos.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Criar e Gerenciar um Pedido (Priority: P1)

Um cliente ativo inicia um pedido, adiciona itens ao carrinho e pode removê-los antes de confirmar. Somente clientes válidos e ativos podem criar pedidos, e cada cliente só pode ter um pedido aberto por vez.

**Why this priority**: É o ponto de entrada de todo o fluxo. Sem criação e gestão de itens, nenhum outro fluxo existe.

**Independent Test**: Pode ser testado criando um pedido com `POST /orders`, adicionando itens com `POST /orders/{id}/items` e verificando o estado `OPEN` via `GET /orders/{id}`.

**Acceptance Scenarios**:

1. **Given** um cliente ativo existe no sistema, **When** ele solicita a criação de um pedido, **Then** um pedido em estado `OPEN` é criado e retornado com identificador único.
2. **Given** um pedido em estado `OPEN`, **When** um item de produto disponível é adicionado, **Then** o item aparece na lista do pedido com quantidade 1.
3. **Given** um pedido com o produto X já adicionado, **When** o mesmo produto X é adicionado novamente, **Then** a quantidade do item existente é incrementada (sem duplicação).
4. **Given** um pedido em estado `OPEN` com itens, **When** um item é removido, **Then** o item deixa de constar no pedido.
5. **Given** um cliente que já possui um pedido `OPEN`, **When** ele tenta criar outro pedido, **Then** a solicitação é rejeitada com erro apropriado.
6. **Given** um cliente inexistente ou bloqueado, **When** ele tenta criar um pedido, **Then** a solicitação é rejeitada com erro apropriado.
7. **Given** um produto indisponível ou inexistente, **When** tentativa de adicioná-lo ao pedido, **Then** a solicitação é rejeitada com erro apropriado.

---

### User Story 2 — Confirmar o Pedido (Priority: P2)

Um cliente confirma seu pedido, acionando a validação final dos produtos e o cálculo do valor total com os preços vigentes no momento da confirmação.

**Why this priority**: A confirmação é o ponto de transição crítico do fluxo — é onde o sistema valida disponibilidade e trava o valor do pedido. Depende da criação (P1).

**Independent Test**: Pode ser testado confirmando um pedido com itens via `POST /orders/{id}/confirm` e verificando a transição para o estado `CONFIRMED` com valor total calculado.

**Acceptance Scenarios**:

1. **Given** um pedido `OPEN` com ao menos um item, **When** a confirmação é solicitada, **Then** o pedido transita para `CONFIRMED` e o valor total é calculado com os preços atuais do catálogo.
2. **Given** um pedido `OPEN` sem itens, **When** a confirmação é solicitada, **Then** a solicitação é rejeitada com erro indicando ausência de itens.
3. **Given** um pedido `CONFIRMED`, **When** uma nova tentativa de confirmação é feita com o mesmo `Idempotency-Key`, **Then** a resposta original é retornada sem efeito colateral.
4. **Given** um pedido `CONFIRMED`, **When** tentativa de adicionar ou remover itens, **Then** a solicitação é rejeitada com erro apropriado.

---

### User Story 3 — Processar Pagamento e Receber Callback (Priority: P3)

Um pedido confirmado tem seu pagamento iniciado junto ao gateway externo. O gateway responde via callback (webhook) informando aprovação ou rejeição. O sistema suporta até 3 tentativas de pagamento antes de cancelar automaticamente.

**Why this priority**: Depende da confirmação (P2). É o fluxo de monetização do negócio.

**Independent Test**: Pode ser testado iniciando pagamento via `POST /payments` e simulando callbacks de aprovação/rejeição via `POST /payments/{id}/callback`.

**Acceptance Scenarios**:

1. **Given** um pedido `CONFIRMED`, **When** o pagamento é iniciado, **Then** o pedido transita para `PAYMENT_PENDING` e o gateway externo é acionado.
2. **Given** um pedido `PAYMENT_PENDING`, **When** o callback do gateway informa aprovação, **Then** o pedido transita para `PAYMENT_APPROVED` (estado terminal de sucesso).
3. **Given** um pedido `PAYMENT_PENDING` com menos de 3 tentativas rejeitadas, **When** o callback informa rejeição, **Then** o pedido retorna para `CONFIRMED`, permitindo nova tentativa.
4. **Given** um pedido `PAYMENT_PENDING` com exatamente 3 rejeições acumuladas, **When** o callback informa rejeição, **Then** o pedido transita automaticamente para `CANCELLED`.
5. **Given** o mesmo evento de callback recebido mais de uma vez, **When** processado novamente, **Then** nenhum efeito colateral é gerado (idempotência).
6. **Given** um pedido `PAYMENT_PENDING` e o gateway instável retorna erro, **When** a chamada ao gateway falha, **Then** o sistema tenta novamente com recuo progressivo antes de falhar graciosamente.

---

### User Story 4 — Cancelar um Pedido (Priority: P4)

Um cliente pode cancelar seu pedido enquanto o pagamento ainda não foi aprovado. Após a aprovação, o cancelamento não é mais possível.

**Why this priority**: Depende da criação (P1). É uma funcionalidade complementar ao ciclo de vida.

**Independent Test**: Pode ser testado cancelando um pedido `OPEN` ou `CONFIRMED` via `DELETE /orders/{id}` e verificando a transição para `CANCELLED`.

**Acceptance Scenarios**:

1. **Given** um pedido em estado `OPEN`, **When** o cancelamento é solicitado, **Then** o pedido transita para `CANCELLED`.
2. **Given** um pedido em estado `CONFIRMED`, **When** o cancelamento é solicitado, **Then** o pedido transita para `CANCELLED`.
3. **Given** um pedido em estado `PAYMENT_APPROVED`, **When** o cancelamento é solicitado, **Then** a solicitação é rejeitada com erro indicando que pedido aprovado não pode ser cancelado.
4. **Given** um pedido já `CANCELLED`, **When** qualquer modificação é tentada, **Then** a solicitação é rejeitada com erro.

---

### User Story 5 — Consultar Pedidos (Priority: P5)

Um cliente ou operador consulta os detalhes de um pedido específico ou lista os pedidos de um cliente.

**Why this priority**: Funcionalidade de leitura que apoia todos os outros fluxos. Menor impacto no negócio se entregue após os fluxos de mutação.

**Independent Test**: Pode ser testado via `GET /orders/{id}` e `GET /orders?customerId={id}` após criação de pedidos.

**Acceptance Scenarios**:

1. **Given** um pedido existente, **When** consultado pelo identificador, **Then** todos os detalhes são retornados incluindo estado, itens e valor total.
2. **Given** um cliente com múltiplos pedidos, **When** a listagem por cliente é solicitada, **Then** todos os pedidos daquele cliente são retornados.
3. **Given** um identificador de pedido inexistente, **When** consultado, **Then** erro de não encontrado é retornado.

---

### Edge Cases

- O que acontece quando dois pedidos são confirmados para o mesmo cliente simultaneamente (requisições concorrentes)?
- O que acontece quando o gateway de pagamento retorna status inesperado ou malformado?
- O que acontece quando o callback de pagamento chega para um pedido já `CANCELLED`?
- O que acontece quando o serviço de catálogo retorna preço zero ou negativo na confirmação?
- O que acontece quando a requisição de criação de pedido é reenviada com o mesmo `Idempotency-Key`?
- O que acontece quando a adição de item é tentada com quantidade zero ou negativa?

---

## Requirements *(mandatory)*

### Functional Requirements

#### Gestão de Pedidos

- **FR-001**: O sistema DEVE permitir a criação de um pedido somente para clientes ativos e existentes, validados via serviço externo.
- **FR-002**: O sistema DEVE rejeitar a criação de pedido para cliente inexistente ou bloqueado.
- **FR-003**: O sistema DEVE impedir que um cliente tenha mais de um pedido em estado `OPEN` simultaneamente.
- **FR-004**: O sistema DEVE permitir adicionar itens a um pedido somente enquanto ele estiver em estado `OPEN`.
- **FR-005**: O sistema DEVE verificar disponibilidade do produto no serviço de catálogo externo antes de adicionar o item.
- **FR-006**: O sistema DEVE incrementar a quantidade de um item já existente no pedido, em vez de duplicá-lo.
- **FR-007**: O sistema DEVE rejeitar adição de item com quantidade menor ou igual a zero.
- **FR-008**: O sistema DEVE permitir remoção de itens somente em pedidos `OPEN`.
- **FR-009**: O sistema DEVE retornar erro ao tentar remover item inexistente no pedido.
- **FR-010**: O sistema DEVE permitir a confirmação somente de pedidos `OPEN` com ao menos um item.
- **FR-011**: O sistema DEVE calcular o valor total do pedido com os preços vigentes no catálogo **no momento da confirmação**.
- **FR-012**: O sistema DEVE impedir adição ou remoção de itens em pedidos `CONFIRMED` ou em estados posteriores.
- **FR-013**: O sistema DEVE permitir cancelamento de pedidos nos estados `OPEN`, `CONFIRMED` e `PAYMENT_PENDING` (desde que pagamento não aprovado).
- **FR-014**: O sistema DEVE rejeitar cancelamento de pedidos em estado `PAYMENT_APPROVED`.
- **FR-015**: O sistema DEVE rejeitar qualquer modificação em pedidos `CANCELLED`.

#### Gestão de Pagamentos

- **FR-016**: O sistema DEVE permitir iniciar pagamento somente para pedidos `CONFIRMED`.
- **FR-017**: O sistema DEVE impedir que um pagamento seja iniciado novamente para o mesmo pedido (idempotência).
- **FR-018**: O sistema DEVE transitar o pedido para `PAYMENT_APPROVED` ao receber callback de aprovação.
- **FR-019**: O sistema DEVE retornar o pedido ao estado `CONFIRMED` ao receber callback de rejeição com menos de 3 tentativas acumuladas.
- **FR-020**: O sistema DEVE transitar o pedido para `CANCELLED` ao receber callback de rejeição com 3 ou mais tentativas acumuladas.
- **FR-021**: O sistema DEVE processar o mesmo evento de callback sem gerar efeitos colaterais duplicados (idempotência).

#### Idempotência e Concorrência

- **FR-022**: Todos os endpoints de mutação (`POST`, `DELETE`) DEVEM suportar o header `Idempotency-Key`.
- **FR-023**: Reenviar a mesma operação com o mesmo `Idempotency-Key` DEVE retornar a resposta original sem efeitos colaterais.
- **FR-024**: O sistema DEVE lidar corretamente com requisições concorrentes sobre o mesmo pedido, retornando conflito (HTTP 409) quando detectado.

#### Observabilidade e Rastreabilidade

- **FR-025**: O sistema DEVE registrar cada transição de estado de pedido com contexto suficiente para rastrear o histórico completo.
- **FR-026**: O sistema DEVE propagar um identificador de correlação em todas as chamadas a serviços externos e mensagens publicadas.
- **FR-027**: O sistema DEVE publicar eventos de domínio para cada transição significativa de estado (confirmação, aprovação de pagamento, rejeição, cancelamento).

#### Segurança

- **FR-028**: Todos os endpoints DEVEM exigir autenticação via token portador.
- **FR-029**: O acesso a operações de escrita (criação, confirmação, pagamento) DEVE exigir escopo de autorização específico.
- **FR-030**: Erros DEVEM retornar respostas padronizadas sem expor detalhes internos do sistema.

### Key Entities

- **Pedido (Order)**: Representa a intenção de compra de um cliente. Possui estado, lista de itens, valor total calculado e contagem de tentativas de pagamento. É o agregado central do domínio.
- **Item de Pedido (OrderItem)**: Representa um produto dentro de um pedido, com quantidade e preço unitário registrado no momento da confirmação. Pertence a um único pedido.
- **Pagamento (Payment)**: Representa a operação de cobrança associada a um pedido confirmado. Registra o estado da cobrança e o resultado do callback do gateway.
- **Cliente (Customer)**: Entidade externa ao domínio, consultada via serviço externo para validação. Identificado por um ID único.
- **Produto (Product)**: Entidade externa ao domínio, consultada via serviço de catálogo para validação de disponibilidade e obtenção de preço. Identificado por um ID único.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um cliente ativo consegue completar o fluxo completo — criar pedido, adicionar itens, confirmar e iniciar pagamento — em menos de 30 segundos em condições normais de operação.
- **SC-002**: 100% dos endpoints de mutação rejeitam operações duplicadas quando o mesmo `Idempotency-Key` é reenviado, sem gerar efeitos colaterais adicionais.
- **SC-003**: O sistema mantém consistência de estado do pedido sob requisições concorrentes, sem permitir transições de estado inválidas ou duplicação de registros.
- **SC-004**: Nenhum pedido de cliente inválido (inexistente ou bloqueado) é aceito pelo sistema — taxa de rejeição correta de 100% para clientes inválidos.
- **SC-005**: Após 3 rejeições consecutivas de pagamento, 100% dos pedidos afetados são automaticamente cancelados sem intervenção manual.
- **SC-006**: O mesmo evento de callback de pagamento processado múltiplas vezes não altera o estado do pedido após o primeiro processamento válido.
- **SC-007**: Todas as transições de estado inválidas são rejeitadas com resposta de erro padronizada, sem deixar o pedido em estado inconsistente.
- **SC-008**: O sistema continua operando para criação e consulta de pedidos mesmo quando o gateway de pagamento está temporariamente indisponível.

---

## Assumptions

- Clientes e produtos são gerenciados por serviços externos, fora do escopo do `order-service`. Toda validação depende de chamadas HTTP a esses serviços simulados.
- O gateway de pagamento é assíncrono: a iniciação do pagamento retorna imediatamente, e o resultado chega via callback (webhook) em momento posterior.
- A autenticação é delegada a um provedor externo (ex: servidor de autorização). O `order-service` apenas valida tokens recebidos.
- Não há implementação de entrega, logística ou gestão de estoque neste escopo — esses são serviços downstream que consomem eventos do `order-service`.
- O `Notification Service` não é chamado diretamente pelo `order-service`; ele consome eventos de domínio publicados de forma assíncrona.
- Preços de produtos podem mudar entre a adição do item e a confirmação — o sistema sempre usa o preço vigente no momento da confirmação.
- Um pedido cancelado é um estado terminal: não pode ser reaberto ou reativado.
- O controle de tentativas de pagamento é por pedido, não por sessão do usuário.
