package br.com.meli.order.domain.pedido;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ItemPedidoTest {

    @Test
    void subtotalMultiplicaPrecoPelaQuantidade() {
        ItemPedido item = new ItemPedido("p1", "Produto", 3, new BigDecimal("10.00"));

        assertThat(item.subtotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void comQuantidadeAtualizaApenasAQuantidade() {
        ItemPedido item = new ItemPedido("p1", "Produto", 1, new BigDecimal("10.00")).comQuantidade(5);

        assertThat(item.quantidade()).isEqualTo(5);
        assertThat(item.produtoId()).isEqualTo("p1");
        assertThat(item.nomeProduto()).isEqualTo("Produto");
        assertThat(item.precoUnitario()).isEqualByComparingTo("10.00");
    }

    @Test
    void comPrecoAtualizaApenasOPreco() {
        ItemPedido item = new ItemPedido("p1", "Produto", 2, BigDecimal.ZERO).comPreco(new BigDecimal("7.50"));

        assertThat(item.precoUnitario()).isEqualByComparingTo("7.50");
        assertThat(item.quantidade()).isEqualTo(2);
    }

    @Test
    void precoNuloViraZero() {
        ItemPedido item = new ItemPedido("p1", "Produto", 1, null);

        assertThat(item.precoUnitario()).isEqualByComparingTo("0");
    }
}
