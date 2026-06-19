package br.com.meli.order.domain.excecao;

public class PedidoAbertoJaExisteException extends ExcecaoDeDominio {

    public PedidoAbertoJaExisteException(String clienteId) {
        super("O cliente '" + clienteId + "' já possui um pedido aberto.");
    }
}
