package br.com.meli.order.application;

import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.domain.evento.EventoPedido;
import br.com.meli.order.domain.evento.TipoEventoPedido;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.ItemPedido;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ConfirmarPedidoUseCase {

    private final PedidoRepository pedidoRepository;
    private final CatalogoPort catalogoPort;
    private final PublicadorDeEventos publicadorDeEventos;
    private final HistoricoPedidoPort historicoPedidoPort;

    public ConfirmarPedidoUseCase(PedidoRepository pedidoRepository,
                                  CatalogoPort catalogoPort,
                                  PublicadorDeEventos publicadorDeEventos,
                                  HistoricoPedidoPort historicoPedidoPort) {
        this.pedidoRepository = pedidoRepository;
        this.catalogoPort = catalogoPort;
        this.publicadorDeEventos = publicadorDeEventos;
        this.historicoPedidoPort = historicoPedidoPort;
    }

    public Pedido executar(Long pedidoId) {
        Pedido pedido = pedidoRepository.porId(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        if (pedido.status() == StatusPedido.CONFIRMADO) {
            return pedido;
        }

        Map<String, BigDecimal> precos = new HashMap<>();
        for (ItemPedido item : pedido.itens()) {
            precos.put(item.produtoId(), catalogoPort.consultarProduto(item.produtoId()).preco());
        }

        Pedido confirmado = pedidoRepository.salvar(pedido.reprecificar(precos).confirmar());
        EventoPedido evento = EventoPedido.de(confirmado, TipoEventoPedido.PEDIDO_CONFIRMADO);
        publicadorDeEventos.publicar(evento);
        historicoPedidoPort.registrar(evento);
        return confirmado;
    }
}
