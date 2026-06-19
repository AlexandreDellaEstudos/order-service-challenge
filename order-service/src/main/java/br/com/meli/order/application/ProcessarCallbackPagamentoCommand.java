package br.com.meli.order.application;

import br.com.meli.order.application.acl.ResultadoPagamento;

public record ProcessarCallbackPagamentoCommand(Long pedidoId, ResultadoPagamento resultado) {
}
