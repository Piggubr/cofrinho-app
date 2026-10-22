-- Cada um ve o seu e compartilha o que quiser (plano casal, Eduardo 2026-10-22). O
-- compartilhado aparece para todos da familia; so quem lancou desfaz o compartilhamento.
ALTER TABLE expenses         ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE incomes          ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recurring_bills  ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE savings_goals    ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;
-- Conta ou cartao do casal: o que for lancado nele ja nasce compartilhado.
ALTER TABLE payment_accounts ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;

-- O painel "Do casal" soma so o compartilhado do mes.
CREATE INDEX idx_expenses_shared ON expenses (household_id, expense_date) WHERE shared;
