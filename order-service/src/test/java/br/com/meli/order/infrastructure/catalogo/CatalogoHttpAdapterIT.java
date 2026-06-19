package br.com.meli.order.infrastructure.catalogo;

import br.com.meli.order.application.acl.ProdutoCatalogo;
import br.com.meli.order.domain.excecao.ProdutoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import br.com.meli.order.support.WireMockContainerBase;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CatalogoHttpAdapterIT extends WireMockContainerBase {

    private CatalogoHttpAdapter adapter() {
        return new CatalogoHttpAdapter(wireMockUrl());
    }

    @Test
    void produtoDisponivelRetornaNomeEPreco() {
        ProdutoCatalogo produto = adapter().consultarProduto("p1");

        assertThat(produto.disponivel()).isTrue();
        assertThat(produto.nome()).isEqualTo("Produto Exemplo");
        assertThat(produto.preco()).isEqualByComparingTo("10.00");
    }

    @Test
    void produtoIndisponivelRetornaNaoDisponivel() {
        assertThat(adapter().consultarProduto("indisponivel").disponivel()).isFalse();
    }

    @Test
    void produtoInexistenteLancaExcecao() {
        assertThatThrownBy(() -> adapter().consultarProduto("inexistente"))
                .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    @Test
    void servicoInstavelLancaExcecao() {
        assertThatThrownBy(() -> adapter().consultarProduto("instavel"))
                .isInstanceOf(ServicoExternoIndisponivelException.class);
    }
}
