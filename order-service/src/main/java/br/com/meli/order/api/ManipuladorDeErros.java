package br.com.meli.order.api;

import br.com.meli.order.domain.excecao.ClienteInvalidoException;
import br.com.meli.order.domain.excecao.ExcecaoDeDominio;
import br.com.meli.order.domain.excecao.ItemNaoEncontradoException;
import br.com.meli.order.domain.excecao.OperacaoInvalidaException;
import br.com.meli.order.domain.excecao.PedidoAbertoJaExisteException;
import br.com.meli.order.domain.excecao.PedidoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ProdutoIndisponivelException;
import br.com.meli.order.domain.excecao.ProdutoNaoEncontradoException;
import br.com.meli.order.domain.excecao.ServicoExternoIndisponivelException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManipuladorDeErros {

    @ExceptionHandler({PedidoNaoEncontradoException.class, ItemNaoEncontradoException.class,
            ProdutoNaoEncontradoException.class})
    public ProblemDetail naoEncontrado(ExcecaoDeDominio ex) {
        return problema(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(PedidoAbertoJaExisteException.class)
    public ProblemDetail conflito(ExcecaoDeDominio ex) {
        return problema(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler({ClienteInvalidoException.class, ProdutoIndisponivelException.class,
            OperacaoInvalidaException.class})
    public ProblemDetail naoProcessavel(ExcecaoDeDominio ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, ex);
    }

    @ExceptionHandler(ServicoExternoIndisponivelException.class)
    public ProblemDetail servicoIndisponivel(ExcecaoDeDominio ex) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflitoDeConcorrencia(OptimisticLockingFailureException ex) {
        ProblemDetail detalhe = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O pedido foi modificado por outra operação concorrente. Tente novamente.");
        detalhe.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        return detalhe;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException ex) {
        ProblemDetail detalhe = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        detalhe.setTitle(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        return detalhe;
    }

    private ProblemDetail problema(HttpStatus status, ExcecaoDeDominio ex) {
        ProblemDetail detalhe = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        detalhe.setTitle(status.getReasonPhrase());
        return detalhe;
    }
}
