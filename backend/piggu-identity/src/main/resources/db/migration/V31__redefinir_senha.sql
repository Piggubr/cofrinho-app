-- Link de "esqueci minha senha" (Eduardo, 2026-10-19). Como na confirmacao do e-mail,
-- do link so fica o SHA-256 e um pedido novo troca o anterior.
ALTER TABLE users
    ADD COLUMN password_reset_hash          VARCHAR(64),
    ADD COLUMN password_reset_expires_at    TIMESTAMPTZ;

CREATE UNIQUE INDEX users_password_reset_hash_unico ON users (password_reset_hash) WHERE password_reset_hash IS NOT NULL;
