package br.com.meli.order.domain.pedido;

import java.math.BigDecimal;

public final class ItemPedido {

    private final String produtoId;
    private final String nomeProduto;
    private final int quantidade;
    private final BigDecimal precoUnitario;

    public ItemPedido(String produtoId, String nomeProduto, int quantidade, BigDecimal precoUnitario) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario != null ? precoUnitario : BigDecimal.ZERO;
    }

    public ItemPedido comQuantidade(int novaQuantidade) {
        return new ItemPedido(produtoId, nomeProduto, novaQuantidade, precoUnitario);
    }

    public ItemPedido comPreco(BigDecimal novoPreco) {
        return new ItemPedido(produtoId, nomeProduto, quantidade, novoPreco);
    }

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    public String produtoId() {
        return produtoId;
    }

    public String nomeProduto() {
        return nomeProduto;
    }

    public int quantidade() {
        return quantidade;
    }

    public BigDecimal precoUnitario() {
        return precoUnitario;
    }
}
