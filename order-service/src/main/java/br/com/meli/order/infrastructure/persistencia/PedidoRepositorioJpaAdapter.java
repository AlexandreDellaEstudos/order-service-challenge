package br.com.meli.order.infrastructure.persistencia;

import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.pedido.ItemPedido;
import br.com.meli.order.domain.pedido.Pedido;
import br.com.meli.order.domain.pedido.StatusPedido;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class PedidoRepositorioJpaAdapter implements PedidoRepository {

    private final PedidoJpaRepository jpaRepository;

    public PedidoRepositorioJpaAdapter(PedidoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Pedido salvar(Pedido pedido) {
        PedidoEntity entidade = pedido.id() == null
                ? new PedidoEntity()
                : jpaRepository.findById(pedido.id()).orElseGet(PedidoEntity::new);
        aplicar(pedido, entidade);
        return paraDominio(jpaRepository.save(entidade));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pedido> porId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pedido> listarPorCliente(String clienteId) {
        return jpaRepository.findByClienteId(clienteId).stream()
                .map(this::paraDominio)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeAbertoParaCliente(String clienteId) {
        return jpaRepository.existsByClienteIdAndStatus(clienteId, StatusPedido.ABERTO);
    }

    private void aplicar(Pedido pedido, PedidoEntity entidade) {
        entidade.setClienteId(pedido.clienteId());
        entidade.setStatus(pedido.status());
        entidade.setValorTotal(pedido.valorTotal());
        entidade.setTentativasPagamento(pedido.tentativasPagamento());
        entidade.setCriadoEm(pedido.criadoEm());
        List<ItemPedidoEmbeddable> itens = pedido.itens().stream()
                .map(item -> new ItemPedidoEmbeddable(
                        item.produtoId(), item.nomeProduto(), item.quantidade(), item.precoUnitario()))
                .toList();
        entidade.getItens().clear();
        entidade.getItens().addAll(itens);
    }

    private Pedido paraDominio(PedidoEntity entidade) {
        List<ItemPedido> itens = entidade.getItens().stream()
                .map(item -> new ItemPedido(
                        item.getProdutoId(), item.getNomeProduto(), item.getQuantidade(), item.getPrecoUnitario()))
                .toList();
        return Pedido.reconstituir(
                entidade.getId(),
                entidade.getClienteId(),
                itens,
                entidade.getStatus(),
                entidade.getValorTotal(),
                entidade.getTentativasPagamento(),
                entidade.getCriadoEm());
    }
}
