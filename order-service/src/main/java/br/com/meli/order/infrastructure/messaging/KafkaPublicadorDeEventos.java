package br.com.meli.order.infrastructure.messaging;

import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.domain.evento.EventoPedido;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KafkaPublicadorDeEventos implements PublicadorDeEventos {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topico;

    public KafkaPublicadorDeEventos(KafkaTemplate<String, Object> kafkaTemplate,
                                    @Value("${app.kafka.topico-pedidos}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    @Override
    public void publicar(EventoPedido evento) {
        ProducerRecord<String, Object> registro =
                new ProducerRecord<>(topico, String.valueOf(evento.pedidoId()), evento);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            registro.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(registro);
    }
}
