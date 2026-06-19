package br.com.meli.order.infrastructure.historico;

import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.domain.evento.EventoPedido;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricoPedidoJpaAdapter implements HistoricoPedidoPort {

    private final HistoricoPedidoRepository repository;

    public HistoricoPedidoJpaAdapter(HistoricoPedidoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void registrar(EventoPedido evento) {
        repository.save(new HistoricoPedidoEntity(
                evento.pedidoId(),
                evento.tipo().name(),
                evento.statusResultante().name(),
                evento.valorTotal(),
                MDC.get("correlationId"),
                evento.ocorridoEm()));
    }
}
