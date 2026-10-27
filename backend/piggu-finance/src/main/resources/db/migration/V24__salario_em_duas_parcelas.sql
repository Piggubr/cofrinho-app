-- Receita recorrente em duas parcelas (D-087), como o salario pago no dia 20 e no dia 5:
-- a segunda tem dia, valor e o ultimo mes lancado proprios. Sem segunda parcela, tudo nulo.
ALTER TABLE recurring_bills
    ADD COLUMN second_due_day    SMALLINT,
    ADD COLUMN second_amount     NUMERIC(12, 2),
    ADD COLUMN second_paid_month VARCHAR(7),
    ADD CONSTRAINT recurring_bills_second_check CHECK (
        (second_due_day IS NULL AND second_amount IS NULL)
        OR (second_due_day BETWEEN 1 AND 31 AND second_amount > 0 AND second_amount <= 10000000));
