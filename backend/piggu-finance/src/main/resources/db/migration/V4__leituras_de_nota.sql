-- Leituras de nota fiscal por foto, por familia e mes. No gratuito a leitura propria
-- (OCR, a foto nao sai do servidor) vale ate um limite por mes; acima dele, e a
-- reserva pela IA, e Premium.
CREATE TABLE receipt_usage (
    household_id    UUID       NOT NULL,
    reference_month VARCHAR(7) NOT NULL,
    reads           INTEGER    NOT NULL DEFAULT 0,
    PRIMARY KEY (household_id, reference_month),
    CONSTRAINT receipt_usage_month_check CHECK (reference_month ~ '^\d{4}-\d{2}$')
);
