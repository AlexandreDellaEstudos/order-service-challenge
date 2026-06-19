package br.com.meli.order.infrastructure.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class ItemPedidoEmbeddable {

    @Column(name = "produto_id", nullable = false)
    private String produtoId;

    @Column(name = "nome_produto")
    private String nomeProduto;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "preco_unitario", nullable = false)
    private BigDecimal precoUnitario;

    protected ItemPedidoEmbeddable() {
    }

    public ItemPedidoEmbeddable(String produtoId, String nomeProduto, int quantidade, BigDecimal precoUnitario) {
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }

    public String getProdutoId() {
        return produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }
}
