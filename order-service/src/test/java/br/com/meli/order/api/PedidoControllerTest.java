package br.com.meli.order.api;

import br.com.meli.order.application.AdicionarItemUseCase;
import br.com.meli.order.application.BuscarPedidoUseCase;
import br.com.meli.order.application.CancelarPedidoUseCase;
import br.com.meli.order.application.ConfirmarPedidoUseCase;
import br.com.meli.order.application.CriarPedidoUseCase;
import br.com.meli.order.application.IniciarPagamentoUseCase;
import br.com.meli.order.application.ListarPedidosPorClienteUseCase;
import br.com.meli.order.application.ProcessarCallbackPagamentoUseCase;
import br.com.meli.order.application.RemoverItemUseCase;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.infrastructure.idempotencia.ChaveIdempotenciaRepository;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CriarPedidoUseCase criarPedido;
    @MockBean
    private AdicionarItemUseCase adicionarItem;
    @MockBean
    private RemoverItemUseCase removerItem;
    @MockBean
    private ConfirmarPedidoUseCase confirmarPedido;
    @MockBean
    private CancelarPedidoUseCase cancelarPedido;
    @MockBean
    private IniciarPagamentoUseCase iniciarPagamento;
    @MockBean
    private ProcessarCallbackPagamentoUseCase processarCallback;
    @MockBean
    private BuscarPedidoUseCase buscarPedido;
    @MockBean
    private ListarPedidosPorClienteUseCase listarPorCliente;
    @MockBean
    private ChaveIdempotenciaRepository chaveIdempotenciaRepository;

    private static Pedido pedidoAberto() {
        return Pedido.reconstituir(1L, "cliente-1", List.of(), StatusPedido.ABERTO,
                BigDecimal.ZERO, 0, Instant.now());
    }

    @Test
    void criarRetorna201ComPedido() throws Exception {
        when(criarPedido.executar(any())).thenReturn(pedidoAberto());

        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.clienteId").value("cliente-1"));
    }

    @Test
    void buscarInexistenteRetorna404() throws Exception {
        when(buscarPedido.executar(any())).thenThrow(new PedidoNaoEncontradoException(99L));

        mockMvc.perform(get("/api/v1/pedidos/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void adicionarItemComQuantidadeInvalidaRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos/1/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"p1\",\"quantidade\":0}"))
                .andExpect(status().isBadRequest());
    }
}
