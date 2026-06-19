package br.com.meli.order.domain.excecao;

public class ItemNaoEncontradoException extends ExcecaoDeDominio {

    public ItemNaoEncontradoException(String produtoId) {
        super("Item do produto '" + produtoId + "' não encontrado no pedido.");
    }
}
