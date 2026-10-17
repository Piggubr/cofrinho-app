-- Contas e cartoes da familia. No cartao, o fechamento e o vencimento definem a fatura:
-- os gastos entre o dia seguinte ao fechamento anterior e o fechamento do mes.
CREATE TABLE payment_accounts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id UUID         NOT NULL,
    name         VARCHAR(60)  NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    closing_day  SMALLINT,
    due_day      SMALLINT,
    user_email   VARCHAR(320) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT payment_accounts_kind_check CHECK (
        (kind = 'CONTA' AND closing_day IS NULL AND due_day IS NULL)
        OR (kind = 'CARTAO' AND closing_day BETWEEN 1 AND 31 AND due_day BETWEEN 1 AND 31)),
    CONSTRAINT payment_accounts_household_name_key UNIQUE (household_id, name)
);

-- Gasto pago com a conta ou o cartao; parcelado vira uma linha por parcela, uma por mes.
ALTER TABLE expenses
    ADD COLUMN account_id         UUID REFERENCES payment_accounts (id) ON DELETE SET NULL,
    ADD COLUMN installment_number SMALLINT,
    ADD COLUMN installment_count  SMALLINT,
    ADD CONSTRAINT expenses_installment_check CHECK (
        (installment_number IS NULL AND installment_count IS NULL)
        OR (installment_count BETWEEN 2 AND 48 AND installment_number BETWEEN 1 AND installment_count));

CREATE INDEX expenses_account_date_idx ON expenses (account_id, expense_date) WHERE account_id IS NOT NULL;
