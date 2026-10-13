-- Telefone (E.164) pedido no cadastro por senha e o link de confirmacao do e-mail
-- (Eduardo, 2026-10-13). Do link so fica o SHA-256; um pedido novo troca o anterior.
ALTER TABLE users
    ADD COLUMN phone                    VARCHAR(16),
    ADD COLUMN email_token_hash         VARCHAR(64),
    ADD COLUMN email_token_expires_at   TIMESTAMPTZ;

CREATE UNIQUE INDEX users_email_token_hash_unico ON users (email_token_hash) WHERE email_token_hash IS NOT NULL;
