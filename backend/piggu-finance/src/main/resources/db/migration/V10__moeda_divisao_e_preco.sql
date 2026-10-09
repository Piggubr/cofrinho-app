-- Gasto em outra moeda: o valor gravado ja vem convertido; guardamos o original para conferencia.
ALTER TABLE expenses
    ADD COLUMN original_currency VARCHAR(3),
    ADD COLUMN original_amount   NUMERIC(12, 2),
    ADD CONSTRAINT expenses_original_check CHECK (
        (original_currency IS NULL AND original_amount IS NULL)
        OR (original_currency IS NOT NULL AND original_amount >= 0));

-- Divisao de um gasto entre pessoas da familia: a parte de cada uma. Quem pagou e o autor do gasto.
CREATE TABLE expense_shares (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID           NOT NULL,
    expense_id   UUID           NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    member_email VARCHAR(320)   NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount >= 0)
);

CREATE INDEX expense_shares_expense_idx ON expense_shares (expense_id);
