package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.evento.EventoPedido;

public interface HistoricoPedidoPort {

    void registrar(EventoPedido evento);
}
