package br.com.meli.order.application;

import br.com.meli.order.application.acl.ResultadoPagamento;
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

class ProcessarCallbackPagamentoUseCaseTest {

    private final PedidoRepositorioEmMemoria repositorio = new PedidoRepositorioEmMemoria();
    private final List<EventoPedido> publicados = new ArrayList<>();
    private final PublicadorDeEventos publicador = publicados::add;
    private final HistoricoPedidoPort historico = evento -> {
    };
    private final ProcessarCallbackPagamentoUseCase useCase =
            new ProcessarCallbackPagamentoUseCase(repositorio, publicador, historico);

    private Long pedidoPendente() {
        Pedido pendente = Pedido.criar("c1")
                .adicionarItem(new ItemPedido("p1", "Produto", 1, new BigDecimal("10.00")))
                .confirmar()
                .iniciarPagamento();
        return repositorio.salvar(pendente).id();
    }

    @Test
    void callbackAprovadoMudaParaAprovado() {
        Long id = pedidoPendente();

        Pedido pedido = useCase.executar(new ProcessarCallbackPagamentoCommand(id, ResultadoPagamento.APROVADO));

        assertThat(pedido.status()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(publicados).hasSize(1);
    }

    @Test
    void callbackRejeitadoVoltaParaConfirmado() {
        Long id = pedidoPendente();

        Pedido pedido = useCase.executar(new ProcessarCallbackPagamentoCommand(id, ResultadoPagamento.REJEITADO));

        assertThat(pedido.status()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(pedido.tentativasPagamento()).isEqualTo(1);
    }

    @Test
    void callbackEmPedidoNaoPendenteEhIdempotente() {
        Long id = pedidoPendente();
        useCase.executar(new ProcessarCallbackPagamentoCommand(id, ResultadoPagamento.APROVADO));
        publicados.clear();

        Pedido pedido = useCase.executar(new ProcessarCallbackPagamentoCommand(id, ResultadoPagamento.APROVADO));

        assertThat(pedido.status()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(publicados).isEmpty();
    }
}
