package br.com.meli.order.infrastructure.persistencia;

import br.com.meli.order.domain.pedido.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoJpaRepository extends JpaRepository<PedidoEntity, Long> {

    boolean existsByClienteIdAndStatus(String clienteId, StatusPedido status);

    List<PedidoEntity> findByClienteId(String clienteId);
}
