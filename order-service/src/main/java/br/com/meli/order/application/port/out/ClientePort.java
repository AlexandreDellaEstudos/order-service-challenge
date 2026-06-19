package br.com.meli.order.application.port.out;

import br.com.meli.order.application.acl.SituacaoCliente;

public interface ClientePort {

    SituacaoCliente consultarSituacao(String clienteId);
}
