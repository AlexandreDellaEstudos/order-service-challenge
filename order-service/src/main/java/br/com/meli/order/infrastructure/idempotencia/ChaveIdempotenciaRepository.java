package br.com.meli.order.infrastructure.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotenciaEntity, String> {
}
