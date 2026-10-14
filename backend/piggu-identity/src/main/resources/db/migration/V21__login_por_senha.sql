-- Entrar com e-mail e senha, com o Google como opcao (Eduardo, 2026-10-13).
-- password_hash e o BCrypt da senha; nulo = a conta so entra pelo Google.
-- email_verified diz se o e-mail ja foi provado: o Google prova, o cadastro por senha
-- ainda nao. Enquanto falso, a conta nao vira ADMIN pela lista nem aceita convite de
-- familia, e o primeiro login Google nesse e-mail apaga a senha (quem cadastrou
-- antes nao fica com a conta do dono do e-mail).
ALTER TABLE users
    ADD COLUMN password_hash         VARCHAR(100),
    ADD COLUMN email_verified        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN password_failures     INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN password_locked_until TIMESTAMPTZ;

-- Ate aqui toda conta nasceu de um login Google, que so entra com e-mail verificado.
UPDATE users SET email_verified = TRUE;
