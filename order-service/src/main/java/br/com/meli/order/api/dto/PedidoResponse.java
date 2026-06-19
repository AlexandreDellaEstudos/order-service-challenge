package br.com.meli.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(
        Long id,
        String clienteId,
        String status,
        BigDecimal valorTotal,
        int tentativasPagamento,
        Instant criadoEm,
        List<ItemResponse> itens
) {
}
