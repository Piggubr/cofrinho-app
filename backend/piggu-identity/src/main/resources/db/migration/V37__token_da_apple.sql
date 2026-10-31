-- Entrar com Apple: o refresh token que a Apple da em troca do codigo do login, para revogar
-- o acesso quando a conta e excluida (exigencia da Apple). O Bundle ID que o emitiu vai junto.
ALTER TABLE users ADD COLUMN apple_refresh_token TEXT;
ALTER TABLE users ADD COLUMN apple_client_id VARCHAR(200);
