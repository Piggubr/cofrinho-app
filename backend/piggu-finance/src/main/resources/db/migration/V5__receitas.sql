-- Receitas da familia (salario, extra, reembolso...). Com elas o mes fecha a conta:
-- receitas menos gastos e a sobra, e sobra sobre receitas e a taxa de poupanca.
CREATE TABLE incomes (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID           NOT NULL,
    income_date  DATE           NOT NULL,
    description  VARCHAR(200)   NOT NULL,
    category     VARCHAR(50)    NOT NULL DEFAULT 'Outros',
    amount       NUMERIC(12, 2) NOT NULL,
    user_email   VARCHAR(320)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT incomes_amount_check CHECK (amount > 0 AND amount <= 10000000)
);

CREATE INDEX idx_incomes_household ON incomes (household_id, income_date DESC);
