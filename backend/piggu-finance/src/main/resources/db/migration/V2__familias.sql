-- Multiusuario por familia (household). Cada registro passa a pertencer a uma familia,
-- e o Hibernate filtra toda consulta por ela (@TenantId). Os dados que ja existiam sao
-- da familia inicial, criada com este mesmo id pela V4 do identity: os bancos sao
-- separados, entao o id fixo e o que liga os dois lados.

ALTER TABLE expenses ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE expenses ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_expenses_household ON expenses (household_id, expense_date DESC);

ALTER TABLE monthly_goals ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE monthly_goals ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_monthly_goals_household ON monthly_goals (household_id);

-- A chave era so reference_month; agora repete entre familias.
ALTER TABLE monthly_goals ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE monthly_goals DROP CONSTRAINT monthly_goals_pkey;
ALTER TABLE monthly_goals ADD PRIMARY KEY (id);
ALTER TABLE monthly_goals ADD CONSTRAINT monthly_goals_household_month_key UNIQUE (household_id, reference_month);

ALTER TABLE piggy_deposits ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE piggy_deposits ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_piggy_deposits_household ON piggy_deposits (household_id);

ALTER TABLE notes ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE notes ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_notes_household ON notes (household_id);

ALTER TABLE product_memory ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE product_memory ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_product_memory_household ON product_memory (household_id, purchases DESC);

-- A chave era so product_key; agora repete entre familias.
ALTER TABLE product_memory ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE product_memory DROP CONSTRAINT product_memory_pkey;
ALTER TABLE product_memory ADD PRIMARY KEY (id);
ALTER TABLE product_memory ADD CONSTRAINT product_memory_household_key_key UNIQUE (household_id, product_key);

ALTER TABLE custom_categories ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE custom_categories ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_custom_categories_household ON custom_categories (household_id);

-- Nome de categoria unico por familia, nao mais na instalacao inteira.
ALTER TABLE custom_categories DROP CONSTRAINT custom_categories_name_key;
ALTER TABLE custom_categories ADD CONSTRAINT custom_categories_household_name_key UNIQUE (household_id, name);

-- app_settings continua da instalacao: guarda so o ultimo cambio conhecido.
