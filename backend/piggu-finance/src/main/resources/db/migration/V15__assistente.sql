-- Perguntas ao assistente de IA por pessoa e mes, para o limite mensal do Premium.
-- So a contagem: a conversa nao e guardada no servidor.
CREATE TABLE assistant_usage (
    household_id    UUID       NOT NULL,
    user_id         UUID       NOT NULL,
    reference_month VARCHAR(7) NOT NULL,
    questions       INTEGER    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, reference_month),
    CONSTRAINT assistant_usage_month_check CHECK (reference_month ~ '^\d{4}-\d{2}$')
);

CREATE INDEX idx_assistant_usage_household ON assistant_usage (household_id);
