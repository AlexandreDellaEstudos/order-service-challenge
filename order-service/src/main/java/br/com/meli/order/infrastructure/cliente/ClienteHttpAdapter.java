package br.com.meli.order.infrastructure.cliente;

import br.com.meli.order.application.acl.SituacaoCliente;
import br.com.meli.order.application.port.out.ClientePort;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import br.com.meli.order.infrastructure.observabilidade.PropagacaoCorrelationIdInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class ClienteHttpAdapter implements ClientePort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String clienteBaseUrl;

    public ClienteHttpAdapter(@Value("${services.cliente.url}") String clienteBaseUrl) {
        this.clienteBaseUrl = clienteBaseUrl;
        this.restTemplate.getInterceptors().add(new PropagacaoCorrelationIdInterceptor());
    }

    @Override
    public SituacaoCliente consultarSituacao(String clienteId) {
        String url = clienteBaseUrl + "/customers/" + clienteId;
        try {
            restTemplate.getForEntity(url, String.class);
            return SituacaoCliente.ATIVO;
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            return SituacaoCliente.BLOQUEADO;
        } catch (HttpClientErrorException.NotFound e) {
            return SituacaoCliente.NAO_ENCONTRADO;
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ServicoExternoIndisponivelException("cliente");
        }
    }
}
