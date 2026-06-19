package br.com.meli.order.infrastructure.catalogo;

import br.com.meli.order.application.acl.ProdutoCatalogo;
import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.domain.excecao.ProdutoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import br.com.meli.order.infrastructure.observabilidade.PropagacaoCorrelationIdInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class CatalogoHttpAdapter implements CatalogoPort {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String catalogoBaseUrl;

    public CatalogoHttpAdapter(@Value("${services.catalogo.url}") String catalogoBaseUrl) {
        this.catalogoBaseUrl = catalogoBaseUrl;
        this.restTemplate.getInterceptors().add(new PropagacaoCorrelationIdInterceptor());
    }

    @Override
    public ProdutoCatalogo consultarProduto(String produtoId) {
        String url = catalogoBaseUrl + "/products/" + produtoId;
        try {
            RespostaProduto resposta = restTemplate.getForObject(url, RespostaProduto.class);
            boolean disponivel = resposta == null || resposta.available() == null || resposta.available();
            String nome = resposta != null ? resposta.name() : null;
            BigDecimal preco = resposta != null && resposta.price() != null ? resposta.price() : BigDecimal.ZERO;
            return new ProdutoCatalogo(produtoId, nome, disponivel, preco);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            return new ProdutoCatalogo(produtoId, null, false, BigDecimal.ZERO);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProdutoNaoEncontradoException(produtoId);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new ServicoExternoIndisponivelException("catalogo");
        }
    }

    record RespostaProduto(String name, BigDecimal price, Boolean available) {
    }
}
