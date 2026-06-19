package br.com.meli.order.application;

public record RemoverItemCommand(Long pedidoId, String produtoId) {
}
