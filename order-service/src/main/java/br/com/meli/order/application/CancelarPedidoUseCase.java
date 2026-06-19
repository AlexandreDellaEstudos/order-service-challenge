package br.com.meli.order.application;

import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.domain.evento.EventoPedido;
import br.com.meli.order.domain.evento.TipoEventoPedido;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.Pedido;

public class CancelarPedidoUseCase {

    private final PedidoRepository pedidoRepository;
    private final PublicadorDeEventos publicadorDeEventos;
    private final HistoricoPedidoPort historicoPedidoPort;

    public CancelarPedidoUseCase(PedidoRepository pedidoRepository,
                                 PublicadorDeEventos publicadorDeEventos,
                                 HistoricoPedidoPort historicoPedidoPort) {
        this.pedidoRepository = pedidoRepository;
        this.publicadorDeEventos = publicadorDeEventos;
        this.historicoPedidoPort = historicoPedidoPort;
    }

    public Pedido executar(Long pedidoId) {
        Pedido pedido = pedidoRepository.porId(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));
        Pedido cancelado = pedidoRepository.salvar(pedido.cancelar());
        EventoPedido evento = EventoPedido.de(cancelado, TipoEventoPedido.PEDIDO_CANCELADO);
        publicadorDeEventos.publicar(evento);
        historicoPedidoPort.registrar(evento);
        return cancelado;
    }
}
