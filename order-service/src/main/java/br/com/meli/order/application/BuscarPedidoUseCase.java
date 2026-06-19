package br.com.meli.order.application;

import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.Pedido;

public class BuscarPedidoUseCase {

    private final PedidoRepository pedidoRepository;

    public BuscarPedidoUseCase(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public Pedido executar(Long pedidoId) {
        return pedidoRepository.porId(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));
    }
}
