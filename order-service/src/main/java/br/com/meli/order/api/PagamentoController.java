package br.com.meli.order.api;

import br.com.meli.order.api.dto.CallbackPagamentoRequest;
import br.com.meli.order.api.dto.IniciarPagamentoRequest;
import br.com.meli.order.api.dto.PagamentoResponse;
import br.com.meli.order.application.BuscarPedidoUseCase;
import br.com.meli.order.application.IniciarPagamentoUseCase;
import br.com.meli.order.application.ProcessarCallbackPagamentoCommand;
import br.com.meli.order.application.ProcessarCallbackPagamentoUseCase;
import br.com.meli.order.application.acl.ResultadoPagamento;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PagamentoController {

    private final IniciarPagamentoUseCase iniciarPagamento;
    private final ProcessarCallbackPagamentoUseCase processarCallback;
    private final BuscarPedidoUseCase buscarPedido;

    public PagamentoController(IniciarPagamentoUseCase iniciarPagamento,
                               ProcessarCallbackPagamentoUseCase processarCallback,
                               BuscarPedidoUseCase buscarPedido) {
        this.iniciarPagamento = iniciarPagamento;
        this.processarCallback = processarCallback;
        this.buscarPedido = buscarPedido;
    }

    @PostMapping
    public PagamentoResponse iniciar(@Valid @RequestBody IniciarPagamentoRequest request) {
        return PagamentoResponseMapper.paraResposta(iniciarPagamento.executar(request.pedidoId()));
    }

    @GetMapping("/{paymentId}")
    public PagamentoResponse status(@PathVariable Long paymentId) {
        return PagamentoResponseMapper.paraResposta(buscarPedido.executar(paymentId));
    }

    @PostMapping("/{paymentId}/callback")
    public PagamentoResponse callback(@PathVariable Long paymentId,
                                      @Valid @RequestBody CallbackPagamentoRequest request) {
        ResultadoPagamento resultado = ResultadoPagamento.valueOf(request.resultado().toUpperCase());
        return PagamentoResponseMapper.paraResposta(
                processarCallback.executar(new ProcessarCallbackPagamentoCommand(paymentId, resultado)));
    }
}
