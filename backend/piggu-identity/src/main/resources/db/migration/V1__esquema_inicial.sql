-- Identidade do Piggu.
-- Substitui a aba "Usuarios" da planilha e as constantes EMAILS_AUTORIZADOS /
-- EMAILS_ADMIN que ficavam fixas no Code.gs.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Quem pode entrar. No Apps Script isso era um array no codigo: para liberar
-- alguem era preciso editar e reimplantar o script. Agora e' dado.
CREATE TABLE authorized_emails (
    email       VARCHAR(320) PRIMARY KEY,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT authorized_emails_role_check CHECK (role IN ('ADMIN', 'BEATRIZ', 'FAMILIAR'))
);

CREATE TABLE users (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(320)  NOT NULL UNIQUE,
    role            VARCHAR(20)   NOT NULL,
    nickname        VARCHAR(120),
    google_name     VARCHAR(120),
    google_picture  VARCHAR(1000),
    first_name      VARCHAR(80),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    permissions     JSONB         NOT NULL DEFAULT '{}'::jsonb,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'BEATRIZ', 'FAMILIAR'))
);

-- Sessoes longas. O Code.gs guardava cada sessao como uma propriedade do script e
-- varria TODAS as propriedades a cada login para expirar as vencidas. Aqui e' uma
-- tabela com indice: expirar vira um DELETE com WHERE.
CREATE TABLE refresh_sessions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    user_agent  VARCHAR(300),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_expires ON refresh_sessions (expires_at) WHERE revoked_at IS NULL;

-- Mesma lista que estava em EMAILS_AUTORIZADOS, com os papeis de roleInicial_().
INSERT INTO authorized_emails (email, role) VALUES
    ('pigguadm@gmail.com',                  'ADMIN'),
    ('eduardosouzapagel@gmail.com',         'ADMIN'),
    ('beatrizvieirasouzadias@gmail.com',    'BEATRIZ'),
    ('ahcabral10@gmail.com',                'FAMILIAR'),
    ('theomadeira83@gmail.com',             'FAMILIAR');
