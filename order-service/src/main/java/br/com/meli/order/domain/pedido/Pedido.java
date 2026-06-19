package br.com.meli.order.domain.pedido;

import br.com.meli.order.domain.excecao.ItemNaoEncontradoException;
import br.com.meli.order.domain.excecao.OperacaoInvalidaException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Pedido {

    public static final int MAX_TENTATIVAS_PAGAMENTO = 3;

    private final Long id;
    private final String clienteId;
    private final List<ItemPedido> itens;
    private final StatusPedido status;
    private final BigDecimal valorTotal;
    private final int tentativasPagamento;
    private final Instant criadoEm;

    private Pedido(Long id, String clienteId, List<ItemPedido> itens, StatusPedido status,
                   BigDecimal valorTotal, int tentativasPagamento, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.itens = itens != null ? List.copyOf(itens) : List.of();
        this.status = status;
        this.valorTotal = valorTotal != null ? valorTotal : BigDecimal.ZERO;
        this.tentativasPagamento = tentativasPagamento;
        this.criadoEm = criadoEm;
    }

    public static Pedido criar(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new OperacaoInvalidaException("O pedido precisa de um identificador de cliente.");
        }
        return new Pedido(null, clienteId, List.of(), StatusPedido.ABERTO,
                BigDecimal.ZERO, 0, Instant.now());
    }

    public static Pedido reconstituir(Long id, String clienteId, List<ItemPedido> itens, StatusPedido status,
                                      BigDecimal valorTotal, int tentativasPagamento, Instant criadoEm) {
        return new Pedido(id, clienteId, itens, status, valorTotal, tentativasPagamento, criadoEm);
    }

    public Pedido comId(Long id) {
        return new Pedido(id, clienteId, itens, status, valorTotal, tentativasPagamento, criadoEm);
    }

    public Pedido adicionarItem(ItemPedido novoItem) {
        garantirEstado(StatusPedido.ABERTO, "adicionar item");
        if (novoItem.quantidade() <= 0) {
            throw new OperacaoInvalidaException("A quantidade do item deve ser maior que zero.");
        }
        List<ItemPedido> novosItens = new ArrayList<>(itens);
        int indice = indiceDoProduto(novoItem.produtoId());
        if (indice >= 0) {
            ItemPedido existente = novosItens.get(indice);
            novosItens.set(indice, existente.comQuantidade(existente.quantidade() + novoItem.quantidade()));
        } else {
            novosItens.add(novoItem);
        }
        return comItens(novosItens);
    }

    public Pedido removerItem(String produtoId) {
        garantirEstado(StatusPedido.ABERTO, "remover item");
        int indice = indiceDoProduto(produtoId);
        if (indice < 0) {
            throw new ItemNaoEncontradoException(produtoId);
        }
        List<ItemPedido> novosItens = new ArrayList<>(itens);
        novosItens.remove(indice);
        return comItens(novosItens);
    }

    public Pedido reprecificar(Map<String, BigDecimal> precosPorProduto) {
        garantirEstado(StatusPedido.ABERTO, "reprecificar");
        List<ItemPedido> novosItens = itens.stream()
                .map(item -> item.comPreco(precosPorProduto.getOrDefault(item.produtoId(), item.precoUnitario())))
                .toList();
        return new Pedido(id, clienteId, novosItens, status, recalcularTotal(novosItens),
                tentativasPagamento, criadoEm);
    }

    public Pedido confirmar() {
        garantirEstado(StatusPedido.ABERTO, "confirmar");
        if (itens.isEmpty()) {
            throw new OperacaoInvalidaException("Não é possível confirmar um pedido sem itens.");
        }
        return new Pedido(id, clienteId, itens, StatusPedido.CONFIRMADO,
                recalcularTotal(itens), tentativasPagamento, criadoEm);
    }

    public Pedido iniciarPagamento() {
        garantirEstado(StatusPedido.CONFIRMADO, "iniciar pagamento");
        return comStatus(StatusPedido.PAGAMENTO_PENDENTE);
    }

    public Pedido aprovarPagamento() {
        garantirEstado(StatusPedido.PAGAMENTO_PENDENTE, "aprovar pagamento");
        return comStatus(StatusPedido.PAGAMENTO_APROVADO);
    }

    public Pedido rejeitarPagamento() {
        garantirEstado(StatusPedido.PAGAMENTO_PENDENTE, "rejeitar pagamento");
        int tentativas = tentativasPagamento + 1;
        StatusPedido novoStatus = tentativas >= MAX_TENTATIVAS_PAGAMENTO
                ? StatusPedido.CANCELADO
                : StatusPedido.CONFIRMADO;
        return new Pedido(id, clienteId, itens, novoStatus, valorTotal, tentativas, criadoEm);
    }

    public Pedido cancelar() {
        if (status == StatusPedido.PAGAMENTO_APROVADO) {
            throw new OperacaoInvalidaException("Um pedido com pagamento aprovado não pode ser cancelado.");
        }
        if (status == StatusPedido.CANCELADO) {
            throw new OperacaoInvalidaException("O pedido já está cancelado.");
        }
        return comStatus(StatusPedido.CANCELADO);
    }

    public Long id() {
        return id;
    }

    public String clienteId() {
        return clienteId;
    }

    public List<ItemPedido> itens() {
        return itens;
    }

    public StatusPedido status() {
        return status;
    }

    public BigDecimal valorTotal() {
        return valorTotal;
    }

    public int tentativasPagamento() {
        return tentativasPagamento;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    private Pedido comStatus(StatusPedido novoStatus) {
        return new Pedido(id, clienteId, itens, novoStatus, valorTotal, tentativasPagamento, criadoEm);
    }

    private Pedido comItens(List<ItemPedido> novosItens) {
        return new Pedido(id, clienteId, novosItens, status, recalcularTotal(novosItens),
                tentativasPagamento, criadoEm);
    }

    private void garantirEstado(StatusPedido esperado, String operacao) {
        if (status == StatusPedido.CANCELADO) {
            throw new OperacaoInvalidaException("Um pedido cancelado não pode ser modificado.");
        }
        if (status != esperado) {
            throw new OperacaoInvalidaException("Não é possível " + operacao
                    + ": o pedido está em " + status + " (esperado: " + esperado + ").");
        }
    }

    private int indiceDoProduto(String produtoId) {
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).produtoId().equals(produtoId)) {
                return i;
            }
        }
        return -1;
    }

    private static BigDecimal recalcularTotal(List<ItemPedido> itens) {
        return itens.stream()
                .map(ItemPedido::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
