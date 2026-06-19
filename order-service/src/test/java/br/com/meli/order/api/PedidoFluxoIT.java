package br.com.meli.order.api;

import br.com.meli.order.application.port.out.PublicadorDeEventos;
import br.com.meli.order.support.WireMockContainerBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers
class PedidoFluxoIT extends WireMockContainerBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private PublicadorDeEventos publicadorDeEventos;

    @DynamicPropertySource
    static void servicosExternos(DynamicPropertyRegistry registry) {
        registry.add("services.cliente.url", WireMockContainerBase::wireMockUrl);
        registry.add("services.catalogo.url", WireMockContainerBase::wireMockUrl);
        registry.add("services.pagamento.url", WireMockContainerBase::wireMockUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void criaAdicionaEConfirmaDePontaAPonta() throws Exception {
        String corpoCriacao = mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andReturn().getResponse().getContentAsString();

        int pedidoId = JsonPath.read(corpoCriacao, "$.id");

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"p1\",\"quantidade\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].produtoId").value("p1"));

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/confirmacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.valorTotal").value(20.00));

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value("cliente-1"));
    }

    @Test
    void fluxoDePagamentoAprovado() throws Exception {
        String corpoCriacao = mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-pay\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int pedidoId = JsonPath.read(corpoCriacao, "$.id");

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/itens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"produtoId\":\"p1\",\"quantidade\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/confirmacao"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\":" + pedidoId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPagamento").value("PENDENTE"));

        mockMvc.perform(post("/api/v1/payments/" + pedidoId + "/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"APROVADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPagamento").value("APROVADO"));

        mockMvc.perform(get("/api/v1/payments/" + pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPagamento").value("APROVADO"));
    }

    @Test
    void clienteBloqueadoNaoCriaPedido() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"bloqueado\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
