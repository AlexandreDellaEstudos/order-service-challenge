package br.com.meli.order.infrastructure.cliente;

import br.com.meli.order.application.acl.SituacaoCliente;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import br.com.meli.order.support.WireMockContainerBase;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ClienteHttpAdapterIT extends WireMockContainerBase {

    private ClienteHttpAdapter adapter() {
        return new ClienteHttpAdapter(wireMockUrl());
    }

    @Test
    void clienteAtivoRetornaAtivo() {
        assertThat(adapter().consultarSituacao("cliente-1")).isEqualTo(SituacaoCliente.ATIVO);
    }

    @Test
    void clienteBloqueadoRetornaBloqueado() {
        assertThat(adapter().consultarSituacao("bloqueado")).isEqualTo(SituacaoCliente.BLOQUEADO);
    }

    @Test
    void clienteInexistenteRetornaNaoEncontrado() {
        assertThat(adapter().consultarSituacao("inexistente")).isEqualTo(SituacaoCliente.NAO_ENCONTRADO);
    }

    @Test
    void servicoInstavelLancaExcecao() {
        assertThatThrownBy(() -> adapter().consultarSituacao("instavel"))
                .isInstanceOf(ServicoExternoIndisponivelException.class);
    }
}
