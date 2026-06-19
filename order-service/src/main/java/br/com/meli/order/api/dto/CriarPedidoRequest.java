package br.com.meli.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CriarPedidoRequest(
        @NotBlank String clienteId
) {
}
