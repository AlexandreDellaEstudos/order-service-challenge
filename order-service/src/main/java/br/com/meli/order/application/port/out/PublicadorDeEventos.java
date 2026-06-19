package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.evento.EventoPedido;

public interface PublicadorDeEventos {

    void publicar(EventoPedido evento);
}
