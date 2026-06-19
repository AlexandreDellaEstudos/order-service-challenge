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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SegurancaIdempotenciaIT extends WireMockContainerBase {

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

    private static SimpleGrantedAuthority escrita() {
        return new SimpleGrantedAuthority("SCOPE_orders:write");
    }

    private static SimpleGrantedAuthority leitura() {
        return new SimpleGrantedAuthority("SCOPE_orders:read");
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void comEscopoCriaPedido() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .with(jwt().authorities(escrita()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-escopo\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void mesmaIdempotencyKeyNaoDuplicaPedido() throws Exception {
        String primeira = mockMvc.perform(post("/api/v1/pedidos")
                        .with(jwt().authorities(escrita()))
                        .header("Idempotency-Key", "chave-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-idem\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String segunda = mockMvc.perform(post("/api/v1/pedidos")
                        .with(jwt().authorities(escrita()))
                        .header("Idempotency-Key", "chave-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":\"cliente-idem\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idPrimeira = JsonPath.read(primeira, "$.id");
        int idSegunda = JsonPath.read(segunda, "$.id");
        org.assertj.core.api.Assertions.assertThat(idSegunda).isEqualTo(idPrimeira);

        mockMvc.perform(get("/api/v1/pedidos").param("clienteId", "cliente-idem")
                        .with(jwt().authorities(leitura())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
