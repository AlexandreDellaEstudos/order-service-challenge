package br.com.meli.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdicionarItemRequest(
        @NotBlank String produtoId,
        @Positive int quantidade
) {
}
