package br.com.meli.order.api;

import br.com.meli.order.api.dto.ItemResponse;
import br.com.meli.order.api.dto.PedidoResponse;
import br.com.meli.order.domain.pedido.Pedido;

final class PedidoResponseMapper {

    private PedidoResponseMapper() {
    }

    static PedidoResponse paraResposta(Pedido pedido) {
        var itens = pedido.itens().stream()
                .map(item -> new ItemResponse(
                        item.produtoId(),
                        item.nomeProduto(),
                        item.quantidade(),
                        item.precoUnitario(),
                        item.subtotal()))
                .toList();
        return new PedidoResponse(
                pedido.id(),
                pedido.clienteId(),
                pedido.status().name(),
                pedido.valorTotal(),
                pedido.tentativasPagamento(),
                pedido.criadoEm(),
                itens);
    }
}
