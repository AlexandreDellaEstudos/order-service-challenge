package br.com.meli.order.application;

import br.com.meli.order.application.acl.SituacaoCliente;
import br.com.meli.order.application.port.out.ClientePort;
import br.com.meli.order.domain.excecao.ClienteInvalidoException;
import br.com.meli.order.domain.excecao.PedidoAbertoJaExisteException;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriarPedidoUseCaseTest {

    private final PedidoRepositorioEmMemoria repositorio = new PedidoRepositorioEmMemoria();

    @Test
    void clienteAtivoCriaPedidoAberto() {
        CriarPedidoUseCase useCase = new CriarPedidoUseCase(cliente(SituacaoCliente.ATIVO), repositorio);

        Pedido pedido = useCase.executar(new CriarPedidoCommand("cliente-1"));

        assertThat(pedido.id()).isNotNull();
        assertThat(pedido.status()).isEqualTo(StatusPedido.ABERTO);
        assertThat(pedido.clienteId()).isEqualTo("cliente-1");
    }

    @Test
    void clienteBloqueadoFalha() {
        CriarPedidoUseCase useCase = new CriarPedidoUseCase(cliente(SituacaoCliente.BLOQUEADO), repositorio);

        assertThatThrownBy(() -> useCase.executar(new CriarPedidoCommand("cliente-1")))
                .isInstanceOf(ClienteInvalidoException.class);
    }

    @Test
    void clienteNaoEncontradoFalha() {
        CriarPedidoUseCase useCase = new CriarPedidoUseCase(cliente(SituacaoCliente.NAO_ENCONTRADO), repositorio);

        assertThatThrownBy(() -> useCase.executar(new CriarPedidoCommand("cliente-1")))
                .isInstanceOf(ClienteInvalidoException.class);
    }

    @Test
    void segundoPedidoAbertoParaMesmoClienteFalha() {
        CriarPedidoUseCase useCase = new CriarPedidoUseCase(cliente(SituacaoCliente.ATIVO), repositorio);
        useCase.executar(new CriarPedidoCommand("cliente-1"));

        assertThatThrownBy(() -> useCase.executar(new CriarPedidoCommand("cliente-1")))
                .isInstanceOf(PedidoAbertoJaExisteException.class);
    }

    private static ClientePort cliente(SituacaoCliente situacao) {
        return clienteId -> situacao;
    }
}
