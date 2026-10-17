-- Aceite dos termos de uso e do aviso de privacidade, gravado no ato de criar a conta,
-- com a versao do texto. Contas antigas ficam sem registro (entraram pela lista fechada).
ALTER TABLE users
    ADD COLUMN terms_version     VARCHAR(20),
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;
