package br.com.meli.order.infrastructure.persistencia;

import br.com.meli.order.domain.pedido.StatusPedido;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "tentativas_pagamento", nullable = false)
    private int tentativasPagamento;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Version
    private Long versao;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "itens_pedido", joinColumns = @JoinColumn(name = "pedido_id"))
    private List<ItemPedidoEmbeddable> itens = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public int getTentativasPagamento() {
        return tentativasPagamento;
    }

    public void setTentativasPagamento(int tentativasPagamento) {
        this.tentativasPagamento = tentativasPagamento;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    public List<ItemPedidoEmbeddable> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedidoEmbeddable> itens) {
        this.itens = itens;
    }
}
