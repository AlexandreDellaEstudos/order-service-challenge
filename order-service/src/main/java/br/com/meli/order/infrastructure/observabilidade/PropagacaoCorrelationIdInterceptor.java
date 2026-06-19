package br.com.meli.order.infrastructure.observabilidade;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class PropagacaoCorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().add(HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
