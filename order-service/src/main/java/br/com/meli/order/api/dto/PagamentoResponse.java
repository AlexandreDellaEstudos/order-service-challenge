package br.com.meli.order.api.dto;

import java.math.BigDecimal;

public record PagamentoResponse(
        Long pedidoId,
        String statusPagamento,
        int tentativas,
        BigDecimal valor
) {
}
