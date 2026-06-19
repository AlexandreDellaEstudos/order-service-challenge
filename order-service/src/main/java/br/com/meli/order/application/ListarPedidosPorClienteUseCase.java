package br.com.meli.order.application;

import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.pedido.Pedido;

import java.util.List;

public class ListarPedidosPorClienteUseCase {

    private final PedidoRepository pedidoRepository;

    public ListarPedidosPorClienteUseCase(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<Pedido> executar(String clienteId) {
        return pedidoRepository.listarPorCliente(clienteId);
    }
}
