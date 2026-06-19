package br.com.meli.order.application;

import br.com.meli.order.application.acl.ProdutoCatalogo;
import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ProdutoIndisponivelException;
import br.com.meli.order.domain.pedido.ItemPedido;
import br.com.meli.order.domain.pedido.Pedido;

import java.math.BigDecimal;

public class AdicionarItemUseCase {

    private final PedidoRepository pedidoRepository;
    private final CatalogoPort catalogoPort;

    public AdicionarItemUseCase(PedidoRepository pedidoRepository, CatalogoPort catalogoPort) {
        this.pedidoRepository = pedidoRepository;
        this.catalogoPort = catalogoPort;
    }

    public Pedido executar(AdicionarItemCommand comando) {
        ProdutoCatalogo produto = catalogoPort.consultarProduto(comando.produtoId());
        if (!produto.disponivel()) {
            throw new ProdutoIndisponivelException(comando.produtoId());
        }
        Pedido pedido = pedidoRepository.porId(comando.pedidoId())
                .orElseThrow(() -> new PedidoNaoEncontradoException(comando.pedidoId()));
        ItemPedido item = new ItemPedido(produto.produtoId(), produto.nome(), comando.quantidade(), BigDecimal.ZERO);
        return pedidoRepository.salvar(pedido.adicionarItem(item));
    }
}
