-- Open Finance via Pluggy: bancos conectados por cada usuario e as contas deles.
-- Credencial do banco nunca chega aqui: o usuario a digita no widget da Pluggy.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Um "item" da Pluggy: a ligacao de um usuario com uma instituicao.
CREATE TABLE bank_connections (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    pluggy_item_id VARCHAR(100)  NOT NULL UNIQUE,
    user_email     VARCHAR(320)  NOT NULL,
    institution    VARCHAR(200)  NOT NULL DEFAULT '',
    status         VARCHAR(50)   NOT NULL DEFAULT '',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    synced_at      TIMESTAMPTZ
);

CREATE INDEX idx_bank_connections_user ON bank_connections (user_email);

-- Foto das contas na ultima sincronizacao. O saldo vem pronto da instituicao;
-- diferente do cofrinho, aqui nao ha historico para somar.
CREATE TABLE bank_accounts (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id     UUID           NOT NULL REFERENCES bank_connections (id) ON DELETE CASCADE,
    pluggy_account_id VARCHAR(100)   NOT NULL UNIQUE,
    name              VARCHAR(200)   NOT NULL DEFAULT '',
    type              VARCHAR(50)    NOT NULL DEFAULT '',
    number            VARCHAR(50)    NOT NULL DEFAULT '',
    balance           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'BRL',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_bank_accounts_connection ON bank_accounts (connection_id);
