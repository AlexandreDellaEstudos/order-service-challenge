package br.com.meli.order.infrastructure.idempotencia;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Component
public class IdempotenciaFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";

    private final ChaveIdempotenciaRepository repository;

    public IdempotenciaFilter(ChaveIdempotenciaRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String chave = request.getHeader(HEADER);
        if (chave == null || chave.isBlank() || !ehMutacao(request)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ChaveIdempotenciaEntity> existente = repository.findById(chave);
        if (existente.isPresent()) {
            ChaveIdempotenciaEntity registro = existente.get();
            response.setStatus(registro.getStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (registro.getCorpoResposta() != null) {
                response.getWriter().write(registro.getCorpoResposta());
            }
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        if (wrapper.getStatus() >= 200 && wrapper.getStatus() < 300) {
            String corpo = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            try {
                repository.save(new ChaveIdempotenciaEntity(chave, wrapper.getStatus(), corpo, Instant.now()));
            } catch (RuntimeException ignorada) {
                // requisição concorrente com a mesma chave — registro já gravado
            }
        }
        wrapper.copyBodyToResponse();
    }

    private boolean ehMutacao(HttpServletRequest request) {
        String metodo = request.getMethod();
        return HttpMethod.POST.matches(metodo) || HttpMethod.DELETE.matches(metodo);
    }
}
