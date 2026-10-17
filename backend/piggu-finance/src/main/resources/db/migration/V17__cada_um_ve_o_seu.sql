-- Cada pessoa ve so o que lancou (o titular ve tambem o dos menores). Regras, memoria de
-- precos, metas, orcamentos, contas e importacoes passam a ser de cada pessoa: duas
-- pessoas da familia podem ter, cada uma, o seu "Nubank" ou a sua meta de outubro.
ALTER TABLE monthly_goals DROP CONSTRAINT monthly_goals_household_month_key;
ALTER TABLE monthly_goals ADD CONSTRAINT monthly_goals_household_user_month_key
    UNIQUE (household_id, user_id, reference_month);

ALTER TABLE product_memory DROP CONSTRAINT product_memory_household_key_key;
ALTER TABLE product_memory ADD CONSTRAINT product_memory_household_user_key_key
    UNIQUE (household_id, user_id, product_key);

ALTER TABLE category_budgets DROP CONSTRAINT category_budgets_household_category_key;
ALTER TABLE category_budgets ADD CONSTRAINT category_budgets_household_user_category_key
    UNIQUE (household_id, user_id, category);

ALTER TABLE category_rules DROP CONSTRAINT category_rules_household_term_key;
ALTER TABLE category_rules ADD CONSTRAINT category_rules_household_user_term_key
    UNIQUE (household_id, user_id, term);

ALTER TABLE payment_accounts DROP CONSTRAINT payment_accounts_household_name_key;
ALTER TABLE payment_accounts ADD CONSTRAINT payment_accounts_household_user_name_key
    UNIQUE (household_id, user_id, name);

DROP INDEX expenses_household_external_id_key;
CREATE UNIQUE INDEX expenses_household_user_external_id_key ON expenses (household_id, user_id, external_id)
    WHERE external_id IS NOT NULL;
