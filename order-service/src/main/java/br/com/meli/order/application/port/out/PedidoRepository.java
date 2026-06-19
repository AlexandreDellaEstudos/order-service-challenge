package br.com.meli.order.application.port.out;

import br.com.meli.order.domain.pedido.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository {

    Pedido salvar(Pedido pedido);

    Optional<Pedido> porId(Long id);

    List<Pedido> listarPorCliente(String clienteId);

    boolean existeAbertoParaCliente(String clienteId);
}
