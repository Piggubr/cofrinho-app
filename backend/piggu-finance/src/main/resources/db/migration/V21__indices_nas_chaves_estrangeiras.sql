-- Indices que faltavam (DATA_MODEL, "Coisas que achei", itens 7 e 8). Sem eles, apagar o
-- registro apontado varre a tabela que aponta para ele.
CREATE INDEX idx_notes_expense ON notes (expense_id);
CREATE INDEX idx_receivables_income ON receivables (income_id);
CREATE INDEX idx_recurring_bills_account ON recurring_bills (account_id);

-- Toda consulta de gastos filtra por "dividido com quem eu vejo"
-- (expense_id IN (SELECT expense_id FROM expense_shares WHERE member_id = ANY(...))).
-- Com expense_id junto, a subconsulta e respondida so pelo indice.
CREATE INDEX idx_expense_shares_member ON expense_shares (member_id, expense_id);
