package br.com.meli.order.domain.evento;

import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

import java.math.BigDecimal;
import java.time.Instant;

public record EventoPedido(
        Long pedidoId,
        String clienteId,
        TipoEventoPedido tipo,
        StatusPedido statusResultante,
        BigDecimal valorTotal,
        Instant ocorridoEm
) {

    public static EventoPedido de(Pedido pedido, TipoEventoPedido tipo) {
        return new EventoPedido(
                pedido.id(),
                pedido.clienteId(),
                tipo,
                pedido.status(),
                pedido.valorTotal(),
                Instant.now());
    }
}
