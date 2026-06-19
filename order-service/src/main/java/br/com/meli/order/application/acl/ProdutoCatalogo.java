package br.com.meli.order.application.acl;

import java.math.BigDecimal;

public record ProdutoCatalogo(
        String produtoId,
        String nome,
        boolean disponivel,
        BigDecimal preco
) {
}
