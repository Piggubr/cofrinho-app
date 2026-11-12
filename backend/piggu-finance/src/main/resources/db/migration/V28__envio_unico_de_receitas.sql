-- Como nos gastos (V22): o app manda um id por envio da receita (idDoEnvio); o mesmo id na
-- mesma familia nao lanca de novo e devolve a receita da primeira vez (D-192).
CREATE TABLE income_submissions (
    household_id UUID        NOT NULL,
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    income_id    UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, id)
);
