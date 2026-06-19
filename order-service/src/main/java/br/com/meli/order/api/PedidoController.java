package br.com.meli.order.api;

import br.com.meli.order.api.dto.AdicionarItemRequest;
import br.com.meli.order.api.dto.CallbackPagamentoRequest;
import br.com.meli.order.api.dto.CriarPedidoRequest;
import br.com.meli.order.api.dto.PagamentoResponse;
import br.com.meli.order.api.dto.PedidoResponse;
import br.com.meli.order.application.AdicionarItemCommand;
import br.com.meli.order.application.AdicionarItemUseCase;
import br.com.meli.order.application.BuscarPedidoUseCase;
import br.com.meli.order.application.CancelarPedidoUseCase;
import br.com.meli.order.application.ConfirmarPedidoUseCase;
import br.com.meli.order.application.CriarPedidoCommand;
import br.com.meli.order.application.CriarPedidoUseCase;
import br.com.meli.order.application.IniciarPagamentoUseCase;
import br.com.meli.order.application.ListarPedidosPorClienteUseCase;
import br.com.meli.order.application.ProcessarCallbackPagamentoCommand;
import br.com.meli.order.application.ProcessarCallbackPagamentoUseCase;
import br.com.meli.order.application.RemoverItemCommand;
import br.com.meli.order.application.RemoverItemUseCase;
import br.com.meli.order.application.acl.ResultadoPagamento;
import br.com.meli.order.domain.pedido.Pedido;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoController {

    private final CriarPedidoUseCase criarPedido;
    private final AdicionarItemUseCase adicionarItem;
    private final RemoverItemUseCase removerItem;
    private final ConfirmarPedidoUseCase confirmarPedido;
    private final CancelarPedidoUseCase cancelarPedido;
    private final IniciarPagamentoUseCase iniciarPagamento;
    private final ProcessarCallbackPagamentoUseCase processarCallback;
    private final BuscarPedidoUseCase buscarPedido;
    private final ListarPedidosPorClienteUseCase listarPorCliente;

    public PedidoController(CriarPedidoUseCase criarPedido,
                            AdicionarItemUseCase adicionarItem,
                            RemoverItemUseCase removerItem,
                            ConfirmarPedidoUseCase confirmarPedido,
                            CancelarPedidoUseCase cancelarPedido,
                            IniciarPagamentoUseCase iniciarPagamento,
                            ProcessarCallbackPagamentoUseCase processarCallback,
                            BuscarPedidoUseCase buscarPedido,
                            ListarPedidosPorClienteUseCase listarPorCliente) {
        this.criarPedido = criarPedido;
        this.adicionarItem = adicionarItem;
        this.removerItem = removerItem;
        this.confirmarPedido = confirmarPedido;
        this.cancelarPedido = cancelarPedido;
        this.iniciarPagamento = iniciarPagamento;
        this.processarCallback = processarCallback;
        this.buscarPedido = buscarPedido;
        this.listarPorCliente = listarPorCliente;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody CriarPedidoRequest request,
                                                UriComponentsBuilder uriBuilder) {
        Pedido pedido = criarPedido.executar(new CriarPedidoCommand(request.clienteId()));
        URI location = uriBuilder.path("/api/v1/pedidos/{id}").buildAndExpand(pedido.id()).toUri();
        return ResponseEntity.created(location).body(PedidoResponseMapper.paraResposta(pedido));
    }

    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable Long id) {
        return PedidoResponseMapper.paraResposta(buscarPedido.executar(id));
    }

    @GetMapping
    public List<PedidoResponse> listar(@RequestParam String clienteId) {
        return listarPorCliente.executar(clienteId).stream()
                .map(PedidoResponseMapper::paraResposta)
                .toList();
    }

    @PostMapping("/{id}/itens")
    public PedidoResponse adicionarItem(@PathVariable Long id,
                                        @Valid @RequestBody AdicionarItemRequest request) {
        Pedido pedido = adicionarItem.executar(
                new AdicionarItemCommand(id, request.produtoId(), request.quantidade()));
        return PedidoResponseMapper.paraResposta(pedido);
    }

    @DeleteMapping("/{id}/itens/{produtoId}")
    public PedidoResponse removerItem(@PathVariable Long id, @PathVariable String produtoId) {
        return PedidoResponseMapper.paraResposta(
                removerItem.executar(new RemoverItemCommand(id, produtoId)));
    }

    @PostMapping("/{id}/confirmacao")
    public PedidoResponse confirmar(@PathVariable Long id) {
        return PedidoResponseMapper.paraResposta(confirmarPedido.executar(id));
    }

    @PostMapping("/{id}/pagamento")
    public PedidoResponse iniciarPagamento(@PathVariable Long id) {
        return PedidoResponseMapper.paraResposta(iniciarPagamento.executar(id));
    }

    @GetMapping("/{id}/pagamento")
    public PagamentoResponse statusPagamento(@PathVariable Long id) {
        return PagamentoResponseMapper.paraResposta(buscarPedido.executar(id));
    }

    @PostMapping("/{id}/pagamento/callback")
    public PedidoResponse callbackPagamento(@PathVariable Long id,
                                            @Valid @RequestBody CallbackPagamentoRequest request) {
        ResultadoPagamento resultado = ResultadoPagamento.valueOf(request.resultado().toUpperCase());
        return PedidoResponseMapper.paraResposta(
                processarCallback.executar(new ProcessarCallbackPagamentoCommand(id, resultado)));
    }

    @DeleteMapping("/{id}")
    public PedidoResponse cancelar(@PathVariable Long id) {
        return PedidoResponseMapper.paraResposta(cancelarPedido.executar(id));
    }
}
