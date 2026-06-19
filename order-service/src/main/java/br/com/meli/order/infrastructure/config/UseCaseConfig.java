package br.com.meli.order.infrastructure.config;

import br.com.meli.order.application.AdicionarItemUseCase;
import br.com.meli.order.application.BuscarPedidoUseCase;
import br.com.meli.order.application.CancelarPedidoUseCase;
import br.com.meli.order.application.ConfirmarPedidoUseCase;
import br.com.meli.order.application.CriarPedidoUseCase;
import br.com.meli.order.application.IniciarPagamentoUseCase;
import br.com.meli.order.application.ListarPedidosPorClienteUseCase;
import br.com.meli.order.application.ProcessarCallbackPagamentoUseCase;
import br.com.meli.order.application.RemoverItemUseCase;
import br.com.meli.order.application.port.out.CatalogoPort;
import br.com.meli.order.application.port.out.ClientePort;
import br.com.meli.order.application.port.out.HistoricoPedidoPort;
import br.com.meli.order.application.port.out.PagamentoGatewayPort;
import br.com.meli.order.application.port.out.PedidoRepository;
import br.com.meli.order.application.port.out.PublicadorDeEventos;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CriarPedidoUseCase criarPedidoUseCase(ClientePort clientePort, PedidoRepository pedidoRepository) {
        return new CriarPedidoUseCase(clientePort, pedidoRepository);
    }

    @Bean
    public AdicionarItemUseCase adicionarItemUseCase(PedidoRepository pedidoRepository, CatalogoPort catalogoPort) {
        return new AdicionarItemUseCase(pedidoRepository, catalogoPort);
    }

    @Bean
    public RemoverItemUseCase removerItemUseCase(PedidoRepository pedidoRepository) {
        return new RemoverItemUseCase(pedidoRepository);
    }

    @Bean
    public ConfirmarPedidoUseCase confirmarPedidoUseCase(PedidoRepository pedidoRepository,
                                                         CatalogoPort catalogoPort,
                                                         PublicadorDeEventos publicadorDeEventos,
                                                         HistoricoPedidoPort historicoPedidoPort) {
        return new ConfirmarPedidoUseCase(pedidoRepository, catalogoPort, publicadorDeEventos, historicoPedidoPort);
    }

    @Bean
    public CancelarPedidoUseCase cancelarPedidoUseCase(PedidoRepository pedidoRepository,
                                                       PublicadorDeEventos publicadorDeEventos,
                                                       HistoricoPedidoPort historicoPedidoPort) {
        return new CancelarPedidoUseCase(pedidoRepository, publicadorDeEventos, historicoPedidoPort);
    }

    @Bean
    public IniciarPagamentoUseCase iniciarPagamentoUseCase(PedidoRepository pedidoRepository,
                                                           PagamentoGatewayPort pagamentoGatewayPort) {
        return new IniciarPagamentoUseCase(pedidoRepository, pagamentoGatewayPort);
    }

    @Bean
    public ProcessarCallbackPagamentoUseCase processarCallbackPagamentoUseCase(PedidoRepository pedidoRepository,
                                                                               PublicadorDeEventos publicadorDeEventos,
                                                                               HistoricoPedidoPort historicoPedidoPort) {
        return new ProcessarCallbackPagamentoUseCase(pedidoRepository, publicadorDeEventos, historicoPedidoPort);
    }

    @Bean
    public BuscarPedidoUseCase buscarPedidoUseCase(PedidoRepository pedidoRepository) {
        return new BuscarPedidoUseCase(pedidoRepository);
    }

    @Bean
    public ListarPedidosPorClienteUseCase listarPedidosPorClienteUseCase(PedidoRepository pedidoRepository) {
        return new ListarPedidosPorClienteUseCase(pedidoRepository);
    }
}
