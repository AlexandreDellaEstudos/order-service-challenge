package br.com.meli.order.infrastructure.idempotencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chaves_idempotencia")
public class ChaveIdempotenciaEntity {

    @Id
    private String chave;

    @Column(nullable = false)
    private int status;

    @Column(name = "corpo_resposta", columnDefinition = "text")
    private String corpoResposta;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ChaveIdempotenciaEntity() {
    }

    public ChaveIdempotenciaEntity(String chave, int status, String corpoResposta, Instant criadoEm) {
        this.chave = chave;
        this.status = status;
        this.corpoResposta = corpoResposta;
        this.criadoEm = criadoEm;
    }

    public String getChave() {
        return chave;
    }

    public int getStatus() {
        return status;
    }

    public String getCorpoResposta() {
        return corpoResposta;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
