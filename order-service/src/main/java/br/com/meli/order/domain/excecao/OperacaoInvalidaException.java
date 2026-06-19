package br.com.meli.order.domain.excecao;

public class OperacaoInvalidaException extends ExcecaoDeDominio {

    public OperacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
