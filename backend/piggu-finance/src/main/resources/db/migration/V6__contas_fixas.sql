-- Contas fixas que se repetem todo mes (aluguel, luz, internet). "Marcar como paga"
-- lanca o gasto do mes; com o lancamento automatico ligado, um job diario faz isso
-- sozinho no dia do vencimento.
CREATE TABLE recurring_bills (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID           NOT NULL,
    description     VARCHAR(200)   NOT NULL,
    category        VARCHAR(50)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    due_day         SMALLINT       NOT NULL,
    auto_launch     BOOLEAN        NOT NULL DEFAULT FALSE,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    -- Ultimo mes (AAAA-MM) com a conta lancada; o mes so e lancado uma vez.
    last_paid_month VARCHAR(7),
    user_email      VARCHAR(320)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT recurring_bills_amount_check CHECK (amount > 0 AND amount <= 1000000),
    CONSTRAINT recurring_bills_due_day_check CHECK (due_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_recurring_bills_household ON recurring_bills (household_id);
CREATE INDEX idx_recurring_bills_auto ON recurring_bills (due_day) WHERE auto_launch AND active;
