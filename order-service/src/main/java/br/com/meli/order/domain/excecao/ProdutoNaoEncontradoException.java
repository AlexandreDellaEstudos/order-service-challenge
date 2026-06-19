package br.com.meli.order.domain.excecao;

public class ProdutoNaoEncontradoException extends ExcecaoDeDominio {

    public ProdutoNaoEncontradoException(String produtoId) {
        super("Produto '" + produtoId + "' não encontrado no catálogo.");
    }
}
