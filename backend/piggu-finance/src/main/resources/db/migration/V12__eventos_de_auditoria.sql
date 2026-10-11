-- Trilha de auditoria (S2): quem mudou o que nos dados da familia, e quando.
-- Resumo curto de antes e depois, nunca o registro inteiro. Retencao no TrilhaDeAuditoria.
CREATE TABLE eventos_de_auditoria (
    -- Sequencial: desempata eventos da mesma hora e mostra a ordem em que aconteceram.
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id UUID         NOT NULL,
    autor_email  VARCHAR(320) NOT NULL,
    acao         VARCHAR(20)  NOT NULL,
    entidade     VARCHAR(40)  NOT NULL,
    entidade_id  VARCHAR(64),
    antes        VARCHAR(300),
    depois       VARCHAR(300),
    criado_em    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX eventos_de_auditoria_familia ON eventos_de_auditoria (household_id, id DESC);
CREATE INDEX eventos_de_auditoria_criado_em ON eventos_de_auditoria (criado_em);
