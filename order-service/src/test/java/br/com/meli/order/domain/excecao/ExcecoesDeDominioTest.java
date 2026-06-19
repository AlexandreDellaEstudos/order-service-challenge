package br.com.meli.order.domain.excecao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcecoesDeDominioTest {

    @Test
    void mensagensContextualizamOErro() {
        assertThat(new ItemNaoEncontradoException("p1").getMessage()).contains("p1");
        assertThat(new PedidoNaoEncontradoException(5L).getMessage()).contains("5");
        assertThat(new ClienteInvalidoException("c1", "cliente bloqueado").getMessage())
                .contains("c1").contains("cliente bloqueado");
        assertThat(new PedidoAbertoJaExisteException("c2").getMessage()).contains("c2");
        assertThat(new ProdutoIndisponivelException("p2").getMessage()).contains("p2");
        assertThat(new ProdutoNaoEncontradoException("p3").getMessage()).contains("p3");
        assertThat(new ServicoExternoIndisponivelException("catalogo").getMessage()).contains("catalogo");
        assertThat(new OperacaoInvalidaException("erro xyz").getMessage()).isEqualTo("erro xyz");
    }
}
