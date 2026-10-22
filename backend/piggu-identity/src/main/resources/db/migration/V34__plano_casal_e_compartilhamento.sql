-- Plano casal (Eduardo, 2026-10-22): R$ 29,90/mes, Premium para o titular e o parceiro.
ALTER TABLE households DROP CONSTRAINT households_plan_kind_check;
ALTER TABLE households ADD CONSTRAINT households_plan_kind_check
    CHECK (plan_kind IN ('INDIVIDUAL', 'CASAL', 'FAMILIA'));

-- Cada um ve o seu e compartilha o que quiser. Ver tudo do parceiro passa a depender da
-- escolha de quem lanca: shows_all_to guarda PARA QUEM a pessoa mostra tudo, entao trocar
-- de parceiro nunca herda a permissao do anterior. shares_by_default e o valor inicial do
-- "Compartilhado" nos lancamentos novos.
ALTER TABLE users
    ADD COLUMN shows_all_to      UUID,
    ADD COLUMN shares_by_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Quem ja e casal hoje continua vendo tudo um do outro: nada some da tela no dia da mudanca.
UPDATE users u SET shows_all_to = (
    SELECT p.id FROM users p WHERE p.household_id = u.household_id AND p.role = 'PARCEIRO'
    ORDER BY p.created_at LIMIT 1)
WHERE u.role IN ('TITULAR', 'ADMIN');

UPDATE users u SET shows_all_to = (
    SELECT t.id FROM users t WHERE t.household_id = u.household_id AND t.role IN ('TITULAR', 'ADMIN')
    ORDER BY CASE t.role WHEN 'TITULAR' THEN 0 ELSE 1 END, t.created_at LIMIT 1)
WHERE u.role = 'PARCEIRO';
