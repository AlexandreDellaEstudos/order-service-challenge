package br.com.meli.order.domain.excecao;

public abstract class ExcecaoDeDominio extends RuntimeException {

    protected ExcecaoDeDominio(String mensagem) {
        super(mensagem);
    }
}
