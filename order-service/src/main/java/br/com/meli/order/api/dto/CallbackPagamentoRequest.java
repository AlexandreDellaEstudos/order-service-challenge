package br.com.meli.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CallbackPagamentoRequest(
        @NotBlank String resultado
) {
}
