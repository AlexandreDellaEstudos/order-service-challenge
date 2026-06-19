package br.com.meli.order.domain.excecao;

public class ProdutoIndisponivelException extends ExcecaoDeDominio {

    public ProdutoIndisponivelException(String produtoId) {
        super("Produto '" + produtoId + "' indisponível no catálogo.");
    }
}
