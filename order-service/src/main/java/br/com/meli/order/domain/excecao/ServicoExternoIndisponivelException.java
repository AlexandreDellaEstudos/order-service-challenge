package br.com.meli.order.domain.excecao;

public class ServicoExternoIndisponivelException extends ExcecaoDeDominio {

    public ServicoExternoIndisponivelException(String servico) {
        super("Serviço externo '" + servico + "' indisponível.");
    }
}
