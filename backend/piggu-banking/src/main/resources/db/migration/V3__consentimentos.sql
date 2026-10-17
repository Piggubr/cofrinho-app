-- Consentimento gravado no proprio ato (LGPD art. 8): com a versao do aviso que a
-- pessoa viu e quando. E a prova que o art. 8 par. 2 pede ao controlador.
CREATE TABLE consents (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id   UUID         NOT NULL,
    user_email     VARCHAR(320) NOT NULL,
    -- GEMINI (foto do recibo lida por IA) ou PLUGGY (conexao com banco).
    purpose        VARCHAR(40)  NOT NULL,
    notice_version VARCHAR(20)  NOT NULL,
    accepted_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_consents_pessoa ON consents (household_id, user_email, purpose);
