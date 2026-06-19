package br.com.meli.order.application.port.out;

import java.math.BigDecimal;

public interface PagamentoGatewayPort {

    void iniciarCobranca(Long pedidoId, BigDecimal valor);
}
