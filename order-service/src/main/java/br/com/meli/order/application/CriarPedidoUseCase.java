package br.com.meli.order.application;

import br.com.meli.order.application.acl.SituacaoCliente;
import br.com.meli.order.application.port.out.ClientePort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.domain.excecao.ClienteInvalidoException;
import br.com.meli.order.domain.excecao.PedidoAbertoJaExisteException;
import br.com.meli.order.domain.pedido.Pedido;

public class CriarPedidoUseCase {

    private final ClientePort clientePort;
    private final PedidoRepository pedidoRepository;

    public CriarPedidoUseCase(ClientePort clientePort, PedidoRepository pedidoRepository) {
        this.clientePort = clientePort;
        this.pedidoRepository = pedidoRepository;
    }

    public Pedido executar(CriarPedidoCommand comando) {
        SituacaoCliente situacao = clientePort.consultarSituacao(comando.clienteId());
        if (situacao != SituacaoCliente.ATIVO) {
            throw new ClienteInvalidoException(comando.clienteId(), motivo(situacao));
        }
        if (pedidoRepository.existeAbertoParaCliente(comando.clienteId())) {
            throw new PedidoAbertoJaExisteException(comando.clienteId());
        }
        return pedidoRepository.salvar(Pedido.criar(comando.clienteId()));
    }

    private static String motivo(SituacaoCliente situacao) {
        return switch (situacao) {
            case BLOQUEADO -> "cliente bloqueado";
            case NAO_ENCONTRADO -> "cliente não encontrado";
            default -> "cliente inativo";
        };
    }
}
