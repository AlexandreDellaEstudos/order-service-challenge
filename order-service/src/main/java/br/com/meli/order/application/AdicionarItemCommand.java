package br.com.meli.order.application;

public record AdicionarItemCommand(Long pedidoId, String produtoId, int quantidade) {
}
