package br.com.meli.order.application;

import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class PedidoRepositorioEmMemoria implements PedidoRepository {

    private final List<Pedido> salvos = new ArrayList<>();
    private long sequencia = 0;

    @Override
    public Pedido salvar(Pedido pedido) {
        Pedido comId = pedido.id() == null ? pedido.comId(++sequencia) : pedido;
        salvos.removeIf(p -> p.id().equals(comId.id()));
        salvos.add(comId);
        return comId;
    }

    @Override
    public Optional<Pedido> porId(Long id) {
        return salvos.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public List<Pedido> listarPorCliente(String clienteId) {
        return salvos.stream().filter(p -> p.clienteId().equals(clienteId)).toList();
    }

    @Override
    public boolean existeAbertoParaCliente(String clienteId) {
        return salvos.stream()
                .anyMatch(p -> p.clienteId().equals(clienteId) && p.status() == StatusPedido.ABERTO);
    }
}
