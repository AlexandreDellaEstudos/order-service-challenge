package br.com.meli.order.infrastructure.historico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "historico_pedido")
public class HistoricoPedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "status_resultante", nullable = false)
    private String statusResultante;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    protected HistoricoPedidoEntity() {
    }

    public HistoricoPedidoEntity(Long pedidoId, String tipoEvento, String statusResultante,
                                 BigDecimal valorTotal, String correlationId, Instant ocorridoEm) {
        this.pedidoId = pedidoId;
        this.tipoEvento = tipoEvento;
        this.statusResultante = statusResultante;
        this.valorTotal = valorTotal;
        this.correlationId = correlationId;
        this.ocorridoEm = ocorridoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getStatusResultante() {
        return statusResultante;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
