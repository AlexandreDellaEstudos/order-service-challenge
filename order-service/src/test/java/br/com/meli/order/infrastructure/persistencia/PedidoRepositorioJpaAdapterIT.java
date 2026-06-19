package br.com.meli.order.infrastructure.persistencia;

import br.com.meli.order.domain.pedido.ItemPedido;
import br.com.meli.order.domain.pedido.Pedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PedidoRepositorioJpaAdapter.class)
@Testcontainers
class PedidoRepositorioJpaAdapterIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PedidoRepositorioJpaAdapter adapter;

    @Test
    void salvaERecuperaPedidoComItens() {
        Pedido pedido = Pedido.criar("cliente-1")
                .adicionarItem(new ItemPedido("p1", "Produto X", 2, new BigDecimal("10.00")));

        Pedido salvo = adapter.salvar(pedido);
        assertThat(salvo.id()).isNotNull();

        Pedido recuperado = adapter.porId(salvo.id()).orElseThrow();
        assertThat(recuperado.clienteId()).isEqualTo("cliente-1");
        assertThat(recuperado.itens()).hasSize(1);
        assertThat(recuperado.itens().get(0).produtoId()).isEqualTo("p1");
        assertThat(recuperado.valorTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void existeAbertoParaClienteRefleteOsPedidosSalvos() {
        adapter.salvar(Pedido.criar("cliente-2"));

        assertThat(adapter.existeAbertoParaCliente("cliente-2")).isTrue();
        assertThat(adapter.existeAbertoParaCliente("cliente-sem-pedido")).isFalse();
    }
}
