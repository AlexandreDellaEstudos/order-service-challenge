package br.com.meli.order.application;

import br.com.meli.order.application.acl.ProdutoCatalogo;
import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ProdutoIndisponivelException;
import br.com.meli.order.domain.pedido.Pedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdicionarItemUseCaseTest {

    private final PedidoRepositorioEmMemoria repositorio = new PedidoRepositorioEmMemoria();

    private Long criarPedidoAberto() {
        return repositorio.salvar(Pedido.criar("cliente-1")).id();
    }

    private static CatalogoPort catalogoComProduto(boolean disponivel) {
        return produtoId -> new ProdutoCatalogo(produtoId, "Produto X", disponivel, new BigDecimal("10.00"));
    }

    @Test
    void adicionaItemSemPrecoNaAdicao() {
        Long pedidoId = criarPedidoAberto();
        AdicionarItemUseCase useCase = new AdicionarItemUseCase(repositorio, catalogoComProduto(true));

        Pedido pedido = useCase.executar(new AdicionarItemCommand(pedidoId, "p1", 2));

        assertThat(pedido.itens()).hasSize(1);
        assertThat(pedido.itens().get(0).produtoId()).isEqualTo("p1");
        assertThat(pedido.itens().get(0).quantidade()).isEqualTo(2);
        assertThat(pedido.itens().get(0).precoUnitario()).isEqualByComparingTo("0");
    }

    @Test
    void produtoIndisponivelFalha() {
        Long pedidoId = criarPedidoAberto();
        AdicionarItemUseCase useCase = new AdicionarItemUseCase(repositorio, catalogoComProduto(false));

        assertThatThrownBy(() -> useCase.executar(new AdicionarItemCommand(pedidoId, "p1", 1)))
                .isInstanceOf(ProdutoIndisponivelException.class);
    }

    @Test
    void pedidoInexistenteFalha() {
        AdicionarItemUseCase useCase = new AdicionarItemUseCase(repositorio, catalogoComProduto(true));

        assertThatThrownBy(() -> useCase.executar(new AdicionarItemCommand(999L, "p1", 1)))
                .isInstanceOf(PedidoNaoEncontradoException.class);
    }
}
