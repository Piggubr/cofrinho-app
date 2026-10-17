-- O refresh agora vai num cookie HttpOnly. "Continuar conectado" decide se o cookie
-- sobrevive a fechar o navegador; a escolha fica na sessao para a renovacao repetir.
ALTER TABLE refresh_sessions ADD COLUMN remember BOOLEAN NOT NULL DEFAULT TRUE;
