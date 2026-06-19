package br.com.meli.order.application;

import br.com.meli.order.application.port.out.PagamentoGatewayPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

public class IniciarPagamentoUseCase {

    private final PedidoRepository pedidoRepository;
    private final PagamentoGatewayPort pagamentoGatewayPort;

    public IniciarPagamentoUseCase(PedidoRepository pedidoRepository, PagamentoGatewayPort pagamentoGatewayPort) {
        this.pedidoRepository = pedidoRepository;
        this.pagamentoGatewayPort = pagamentoGatewayPort;
    }

    public Pedido executar(Long pedidoId) {
        Pedido pedido = pedidoRepository.porId(pedidoId)
                .orElseThrow(() -> new PedidoNaoEncontradoException(pedidoId));

        if (pedido.status() == StatusPedido.PAGAMENTO_PENDENTE) {
            return pedido;
        }

        Pedido pendente = pedido.iniciarPagamento();
        pagamentoGatewayPort.iniciarCobranca(pendente.id(), pendente.valorTotal());
        return pedidoRepository.salvar(pendente);
    }
}
