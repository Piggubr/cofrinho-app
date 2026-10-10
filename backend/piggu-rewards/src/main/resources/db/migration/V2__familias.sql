-- Multiusuario por familia (household). Cada registro passa a pertencer a uma familia,
-- e o Hibernate filtra toda consulta por ela (@TenantId). Os dados que ja existiam sao
-- da familia inicial, criada com este mesmo id pela V4 do identity: os bancos sao
-- separados, entao o id fixo e o que liga os dois lados.

ALTER TABLE coin_ledger ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE coin_ledger ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_coin_ledger_household ON coin_ledger (household_id, created_at DESC);

ALTER TABLE prizes ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE prizes ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_prizes_household ON prizes (household_id);

ALTER TABLE redemptions ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE redemptions ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_redemptions_household ON redemptions (household_id, created_at DESC);
