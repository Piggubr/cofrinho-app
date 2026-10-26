-- Com sinal fraco a resposta se perde e o app manda de novo o gasto que o servidor ja
-- gravou. O app manda um id por envio (idDoEnvio); o mesmo id na mesma familia nao lanca
-- de novo e devolve os gastos da primeira vez.
CREATE TABLE expense_submissions (
    household_id UUID        NOT NULL,
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    expense_ids  UUID[]      NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, id)
);
