package br.com.meli.order.api.dto;

import java.math.BigDecimal;

public record ItemResponse(
        String produtoId,
        String nomeProduto,
        int quantidade,
        BigDecimal precoUnitario,
        BigDecimal subtotal
) {
}
