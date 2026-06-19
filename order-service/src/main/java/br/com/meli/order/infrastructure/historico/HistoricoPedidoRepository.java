package br.com.meli.order.infrastructure.historico;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoPedidoRepository extends JpaRepository<HistoricoPedidoEntity, Long> {

    List<HistoricoPedidoEntity> findByPedidoIdOrderByOcorridoEmAsc(Long pedidoId);
}
