-- Recorrencia: a conta fixa pode ser paga com um cartao ou uma conta, e o mesmo
-- lancamento automatico serve para a receita que entra todo mes (o salario).
ALTER TABLE recurring_bills
    ADD COLUMN account_id UUID REFERENCES payment_accounts (id) ON DELETE SET NULL,
    ADD COLUMN kind       VARCHAR(10) NOT NULL DEFAULT 'GASTO',
    ADD CONSTRAINT recurring_bills_kind_check CHECK (kind IN ('GASTO', 'RECEITA')),
    DROP CONSTRAINT recurring_bills_amount_check,
    ADD CONSTRAINT recurring_bills_amount_check CHECK (amount > 0 AND amount <= 10000000);

-- Dinheiro que alguem deve para a pessoa. So vira receita quando ela marca que recebeu.
CREATE TABLE receivables (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID           NOT NULL,
    user_id      UUID           NOT NULL,
    debtor       VARCHAR(80)    NOT NULL,
    description  VARCHAR(200)   NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    due_date     DATE           NOT NULL,
    income_id    UUID           REFERENCES incomes (id) ON DELETE SET NULL,
    received_at  DATE,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT receivables_amount_check CHECK (amount > 0 AND amount <= 10000000)
);

CREATE INDEX idx_receivables_household ON receivables (household_id, user_id, due_date);
