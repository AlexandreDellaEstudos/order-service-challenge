package br.com.meli.order.application;

import br.com.meli.order.application.acl.ResultadoPagamento;
import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.domain.evento.EventoPedido;
import br.com.meli.order.domain.evento.TipoEventoPedido;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

public class ProcessarCallbackPagamentoUseCase {

    private final PedidoRepository pedidoRepository;
    private final PublicadorDeEventos publicadorDeEventos;
    private final HistoricoPedidoPort historicoPedidoPort;

    public ProcessarCallbackPagamentoUseCase(PedidoRepository pedidoRepository,
                                             PublicadorDeEventos publicadorDeEventos,
                                             HistoricoPedidoPort historicoPedidoPort) {
        this.pedidoRepository = pedidoRepository;
        this.publicadorDeEventos = publicadorDeEventos;
        this.historicoPedidoPort = historicoPedidoPort;
    }

    public Pedido executar(ProcessarCallbackPagamentoCommand comando) {
        Pedido pedido = pedidoRepository.porId(comando.pedidoId())
                .orElseThrow(() -> new PedidoNaoEncontradoException(comando.pedidoId()));

        if (pedido.status() != StatusPedido.PAGAMENTO_PENDENTE) {
            return pedido;
        }

        Pedido atualizado;
        TipoEventoPedido tipo;
        if (comando.resultado() == ResultadoPagamento.APROVADO) {
            atualizado = pedido.aprovarPagamento();
            tipo = TipoEventoPedido.PAGAMENTO_APROVADO;
        } else {
            atualizado = pedido.rejeitarPagamento();
            tipo = atualizado.status() == StatusPedido.CANCELADO
                    ? TipoEventoPedido.PEDIDO_CANCELADO
                    : TipoEventoPedido.PAGAMENTO_REJEITADO;
        }

        Pedido salvo = pedidoRepository.salvar(atualizado);
        EventoPedido evento = EventoPedido.de(salvo, tipo);
        publicadorDeEventos.publicar(evento);
        historicoPedidoPort.registrar(evento);
        return salvo;
    }
}
