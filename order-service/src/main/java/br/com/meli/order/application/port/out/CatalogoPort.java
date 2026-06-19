package br.com.meli.order.application.port.out;

import br.com.meli.order.application.acl.ProdutoCatalogo;

public interface CatalogoPort {

    ProdutoCatalogo consultarProduto(String produtoId);
}
