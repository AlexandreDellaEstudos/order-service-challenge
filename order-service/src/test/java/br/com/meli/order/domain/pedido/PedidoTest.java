package br.com.meli.order.domain.pedido;

import br.com.meli.order.domain.excecao.ItemNaoEncontradoException;
import br.com.meli.order.domain.excecao.OperacaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTest {

    private static ItemPedido item(String produtoId, int quantidade, String preco) {
        return new ItemPedido(produtoId, "Produto " + produtoId, quantidade, new BigDecimal(preco));
    }

    private static Pedido pedidoConfirmado() {
        return Pedido.criar("cliente-1")
                .adicionarItem(item("p1", 2, "10.00"))
                .confirmar();
    }

    private static Pedido pedidoPendente() {
        return pedidoConfirmado().iniciarPagamento();
    }

    @Test
    void criarPedidoNasceAbertoVazioComDataDeCriacao() {
        Pedido pedido = Pedido.criar("cliente-1");

        assertThat(pedido.status()).isEqualTo(StatusPedido.ABERTO);
        assertThat(pedido.itens()).isEmpty();
        assertThat(pedido.valorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pedido.tentativasPagamento()).isZero();
        assertThat(pedido.clienteId()).isEqualTo("cliente-1");
        assertThat(pedido.criadoEm()).isNotNull();
    }

    @Test
    void criarPedidoSemClienteFalha() {
        assertThatThrownBy(() -> Pedido.criar("  "))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void adicionarItemNovoIncluiNaLista() {
        Pedido pedido = Pedido.criar("cliente-1").adicionarItem(item("p1", 1, "10.00"));

        assertThat(pedido.itens()).hasSize(1);
        assertThat(pedido.itens().get(0).produtoId()).isEqualTo("p1");
        assertThat(pedido.itens().get(0).quantidade()).isEqualTo(1);
    }

    @Test
    void adicionarMesmoProdutoIncrementaQuantidadeSemDuplicar() {
        Pedido pedido = Pedido.criar("cliente-1")
                .adicionarItem(item("p1", 1, "10.00"))
                .adicionarItem(item("p1", 2, "10.00"));

        assertThat(pedido.itens()).hasSize(1);
        assertThat(pedido.itens().get(0).quantidade()).isEqualTo(3);
    }

    @Test
    void adicionarItemComQuantidadeInvalidaFalha() {
        Pedido pedido = Pedido.criar("cliente-1");

        assertThatThrownBy(() -> pedido.adicionarItem(item("p1", 0, "10.00")))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void adicionarItemEmPedidoNaoAbertoFalha() {
        Pedido confirmado = pedidoConfirmado();

        assertThatThrownBy(() -> confirmado.adicionarItem(item("p2", 1, "5.00")))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void removerItemExistenteOTiraDaLista() {
        Pedido pedido = Pedido.criar("cliente-1")
                .adicionarItem(item("p1", 1, "10.00"))
                .removerItem("p1");

        assertThat(pedido.itens()).isEmpty();
    }

    @Test
    void removerItemInexistenteFalha() {
        Pedido pedido = Pedido.criar("cliente-1").adicionarItem(item("p1", 1, "10.00"));

        assertThatThrownBy(() -> pedido.removerItem("p2"))
                .isInstanceOf(ItemNaoEncontradoException.class);
    }

    @Test
    void reprecificarAtualizaPrecosERecalculaTotal() {
        Pedido pedido = Pedido.criar("cliente-1")
                .adicionarItem(item("p1", 2, "0.00"))
                .adicionarItem(item("p2", 1, "0.00"))
                .reprecificar(Map.of("p1", new BigDecimal("10.00"), "p2", new BigDecimal("5.50")));

        assertThat(pedido.itens().get(0).precoUnitario()).isEqualByComparingTo("10.00");
        assertThat(pedido.valorTotal()).isEqualByComparingTo("25.50");
    }

    @Test
    void reprecificarForaDeAbertoFalha() {
        Pedido confirmado = pedidoConfirmado();

        assertThatThrownBy(() -> confirmado.reprecificar(Map.of("p1", new BigDecimal("1.00"))))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void confirmarCalculaTotalEMudaParaConfirmado() {
        Pedido pedido = Pedido.criar("cliente-1")
                .adicionarItem(item("p1", 2, "10.00"))
                .adicionarItem(item("p2", 1, "5.50"))
                .confirmar();

        assertThat(pedido.status()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(pedido.valorTotal()).isEqualByComparingTo("25.50");
    }

    @Test
    void confirmarSemItensFalha() {
        Pedido pedido = Pedido.criar("cliente-1");

        assertThatThrownBy(pedido::confirmar)
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void confirmarPedidoJaConfirmadoFalha() {
        Pedido confirmado = pedidoConfirmado();

        assertThatThrownBy(confirmado::confirmar)
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void iniciarPagamentoMudaParaPendente() {
        Pedido pendente = pedidoConfirmado().iniciarPagamento();

        assertThat(pendente.status()).isEqualTo(StatusPedido.PAGAMENTO_PENDENTE);
    }

    @Test
    void iniciarPagamentoForaDeConfirmadoFalha() {
        Pedido aberto = Pedido.criar("cliente-1");

        assertThatThrownBy(aberto::iniciarPagamento)
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void aprovarPagamentoMudaParaAprovado() {
        Pedido aprovado = pedidoPendente().aprovarPagamento();

        assertThat(aprovado.status()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
    }

    @Test
    void primeiraRejeicaoVoltaParaConfirmadoEContaTentativa() {
        Pedido rejeitado = pedidoPendente().rejeitarPagamento();

        assertThat(rejeitado.status()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(rejeitado.tentativasPagamento()).isEqualTo(1);
    }

    @Test
    void terceiraRejeicaoCancelaAutomaticamente() {
        Pedido pedido = pedidoConfirmado()
                .iniciarPagamento().rejeitarPagamento()
                .iniciarPagamento().rejeitarPagamento()
                .iniciarPagamento().rejeitarPagamento();

        assertThat(pedido.status()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.tentativasPagamento()).isEqualTo(3);
    }

    @Test
    void rejeitarPagamentoForaDePendenteFalha() {
        Pedido confirmado = pedidoConfirmado();

        assertThatThrownBy(confirmado::rejeitarPagamento)
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void cancelarPedidoAbertoMudaParaCancelado() {
        Pedido cancelado = Pedido.criar("cliente-1").cancelar();

        assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void cancelarPedidoConfirmadoMudaParaCancelado() {
        Pedido cancelado = pedidoConfirmado().cancelar();

        assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void cancelarPedidoPendenteMudaParaCancelado() {
        Pedido cancelado = pedidoPendente().cancelar();

        assertThat(cancelado.status()).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    void cancelarPedidoComPagamentoAprovadoFalha() {
        Pedido aprovado = pedidoPendente().aprovarPagamento();

        assertThatThrownBy(aprovado::cancelar)
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    void modificarPedidoCanceladoFalha() {
        Pedido cancelado = Pedido.criar("cliente-1").cancelar();

        assertThatThrownBy(() -> cancelado.adicionarItem(item("p1", 1, "10.00")))
                .isInstanceOf(OperacaoInvalidaException.class);
    }
}
