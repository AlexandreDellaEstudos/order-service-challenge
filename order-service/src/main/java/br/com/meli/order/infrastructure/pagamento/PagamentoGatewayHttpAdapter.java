package br.com.meli.order.infrastructure.pagamento;

import br.com.meli.order.application.port.out.PagamentoGatewayPort;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import br.com.meli.order.infrastructure.observabilidade.PropagacaoCorrelationIdInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class PagamentoGatewayHttpAdapter implements PagamentoGatewayPort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String gatewayBaseUrl;

    public PagamentoGatewayHttpAdapter(@Value("${services.pagamento.url}") String gatewayBaseUrl) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.restTemplate.getInterceptors().add(new PropagacaoCorrelationIdInterceptor());
    }

    @Override
    public void iniciarCobranca(Long pedidoId, BigDecimal valor) {
        String url = gatewayBaseUrl + "/payments";
        try {
            restTemplate.postForEntity(url, new CobrancaRequest(pedidoId, valor), Void.class);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ServicoExternoIndisponivelException("pagamento");
        }
    }

    record CobrancaRequest(Long orderId, BigDecimal amount) {
    }
}
