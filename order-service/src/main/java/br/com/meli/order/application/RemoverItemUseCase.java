package br.com.meli.order.application;

import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.pedido.Pedido;

public class RemoverItemUseCase {

    private final PedidoRepository pedidoRepository;

    public RemoverItemUseCase(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public Pedido executar(RemoverItemCommand comando) {
        Pedido pedido = pedidoRepository.porId(comando.pedidoId())
                .orElseThrow(() -> new PedidoNaoEncontradoException(comando.pedidoId()));
        return pedidoRepository.salvar(pedido.removerItem(comando.produtoId()));
    }
}
