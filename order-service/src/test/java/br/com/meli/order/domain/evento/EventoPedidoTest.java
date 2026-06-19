package br.com.meli.order.domain.evento;

import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventoPedidoTest {

    @Test
    void deCriaEventoComOsDadosDoPedido() {
        Pedido pedido = Pedido.reconstituir(7L, "cliente-9", List.of(), StatusPedido.CONFIRMADO,
                new BigDecimal("30.00"), 1, Instant.now());

        EventoPedido evento = EventoPedido.de(pedido, TipoEventoPedido.PEDIDO_CONFIRMADO);

        assertThat(evento.pedidoId()).isEqualTo(7L);
        assertThat(evento.clienteId()).isEqualTo("cliente-9");
        assertThat(evento.tipo()).isEqualTo(TipoEventoPedido.PEDIDO_CONFIRMADO);
        assertThat(evento.statusResultante()).isEqualTo(StatusPedido.CONFIRMADO);
        assertThat(evento.valorTotal()).isEqualByComparingTo("30.00");
        assertThat(evento.ocorridoEm()).isNotNull();
    }
}
