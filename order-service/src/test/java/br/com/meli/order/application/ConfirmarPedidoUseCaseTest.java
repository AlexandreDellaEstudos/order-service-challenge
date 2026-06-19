package br.com.meli.order.application;

import br.com.meli.order.application.acl.ProdutoCatalogo;
import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.domain.evento.EventoPedido;
import br.com.meli.order.domain.pedido.ItemPedido;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmarPedidoUseCaseTest {

    private final PedidoRepositorioEmMemoria repositorio = new PedidoRepositorioEmMemoria();
    private final List<EventoPedido> publicados = new ArrayList<>();
    private final PublicadorDeEventos publicador = publicados::add;
    private final CatalogoPort catalogo =
            produtoId -> new ProdutoCatalogo(produtoId, "Produto", true, new BigDecimal("10.00"));
    private final HistoricoPedidoPort historico = evento -> {
    };

    @Test
    void confirmaCalculaTotalComPrecoDoCatalogoEPublicaEvento() {
        Long id = repositorio.salvar(Pedido.criar("c1")
                .adicionarItem(new ItemPedido("p1", "Produto", 2, BigDecimal.ZERO))).id();
        ConfirmarPedidoUseCase useCase = new ConfirmarPedidoUseCase(repositorio, catalogo, publicador, historico);

        Pedido confirmado = useCase.executar(id);

        assertThat(confirmado.status()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(confirmado.valorTotal()).isEqualByComparingTo("20.00");
        assertThat(publicados).hasSize(1);
    }

    @Test
    void confirmarDuasVezesEhIdempotente() {
        Long id = repositorio.salvar(Pedido.criar("c1")
                .adicionarItem(new ItemPedido("p1", "Produto", 2, BigDecimal.ZERO))).id();
        ConfirmarPedidoUseCase useCase = new ConfirmarPedidoUseCase(repositorio, catalogo, publicador, historico);
        useCase.executar(id);

        Pedido segunda = useCase.executar(id);

        assertThat(segunda.status()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(publicados).hasSize(1);
    }
}
