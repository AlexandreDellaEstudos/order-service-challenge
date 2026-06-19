CREATE TABLE chaves_idempotencia (
    chave          VARCHAR(255) PRIMARY KEY,
    status         INTEGER      NOT NULL,
    corpo_resposta TEXT,
    criado_em      TIMESTAMP WITH TIME ZONE NOT NULL
);
