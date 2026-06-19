package br.com.meli.order.domain.excecao;

public class ClienteInvalidoException extends ExcecaoDeDominio {

    public ClienteInvalidoException(String clienteId, String motivo) {
        super("Cliente '" + clienteId + "' inválido: " + motivo + ".");
    }
}
