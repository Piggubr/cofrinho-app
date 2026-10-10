-- Orcamento por categoria (Premium): um limite por mes para cada categoria, com
-- alerta em 80% e em 100%. O gasto do mes e sempre somado na hora, nunca guardado.
CREATE TABLE category_budgets (
    id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID           NOT NULL,
    category     VARCHAR(50)    NOT NULL,
    limit_amount NUMERIC(12, 2) NOT NULL,
    user_email   VARCHAR(320)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT category_budgets_limit_check CHECK (limit_amount > 0 AND limit_amount <= 1000000),
    CONSTRAINT category_budgets_household_category_key UNIQUE (household_id, category)
);
