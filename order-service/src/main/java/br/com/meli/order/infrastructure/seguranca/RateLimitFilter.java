package br.com.meli.order.infrastructure.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int requisicoesPorMinuto;
    private final Map<String, Janela> janelasPorCliente = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.requisicoes-por-minuto:120}") int requisicoesPorMinuto) {
        this.requisicoesPorMinuto = requisicoesPorMinuto;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cliente = request.getRemoteAddr();
        long minutoAtual = System.currentTimeMillis() / 60_000;
        Janela janela = janelasPorCliente.compute(cliente,
                (chave, atual) -> atual == null || atual.minuto != minutoAtual ? new Janela(minutoAtual) : atual);

        if (janela.contador.incrementAndGet() > requisicoesPorMinuto) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Limite de requisições excedido.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class Janela {
        private final long minuto;
        private final AtomicInteger contador = new AtomicInteger(0);

        private Janela(long minuto) {
            this.minuto = minuto;
        }
    }
}
