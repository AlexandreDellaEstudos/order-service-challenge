package br.com.meli.order.api;

import br.com.meli.order.api.dto.PagamentoResponse;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;

final class PagamentoResponseMapper {

    private PagamentoResponseMapper() {
    }

    static PagamentoResponse paraResposta(Pedido pedido) {
        return new PagamentoResponse(pedido.id(), statusPagamento(pedido.status()),
                pedido.tentativasPagamento(), pedido.valorTotal());
    }

    private static String statusPagamento(StatusPedido status) {
        return switch (status) {
            case PAGAMENTO_PENDENTE -> "PENDENTE";
            case PAGAMENTO_APROVADO -> "APROVADO";
            case CANCELADO -> "CANCELADO";
            default -> "NAO_INICIADO";
        };
    }
}
