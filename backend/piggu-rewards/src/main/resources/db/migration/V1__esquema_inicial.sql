-- Fofocoins, premios e resgates. Substitui as abas Fofocoins, Fofopremios e Resgates.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Livro-razao das Fofocoins: o saldo nunca e guardado, e sempre a soma das linhas.
-- Era assim na planilha e continua sendo, porque um saldo materializado pode
-- divergir do historico e nao ha como saber qual dos dois esta certo.
CREATE TABLE coin_ledger (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    amount       INTEGER      NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    subject_user VARCHAR(320) NOT NULL DEFAULT 'USUARIA',
    actor_email  VARCHAR(320) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT coin_ledger_amount_check CHECK (amount <> 0 AND abs(amount) <= 1000000)
);

CREATE INDEX idx_coin_ledger_created ON coin_ledger (created_at DESC);

CREATE TABLE prizes (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    price       INTEGER      NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by  VARCHAR(320) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT prizes_price_check CHECK (price > 0 AND price <= 10000000)
);

CREATE TABLE redemptions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prize_id    UUID         NOT NULL REFERENCES prizes (id),
    prize_name  VARCHAR(100) NOT NULL,
    price       INTEGER      NOT NULL,
    user_email  VARCHAR(320) NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'Resgatado',
    ledger_id   UUID         REFERENCES coin_ledger (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_redemptions_user ON redemptions (user_email);
CREATE INDEX idx_redemptions_created ON redemptions (created_at DESC);
