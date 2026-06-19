package br.com.meli.order.api.dto;

import jakarta.validation.constraints.NotNull;

public record IniciarPagamentoRequest(
        @NotNull Long pedidoId
) {
}
