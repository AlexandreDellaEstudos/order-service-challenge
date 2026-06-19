package br.com.meli.order.domain.excecao;

public class PedidoNaoEncontradoException extends ExcecaoDeDominio {

    public PedidoNaoEncontradoException(Long pedidoId) {
        super("Pedido '" + pedidoId + "' não encontrado.");
    }
}
