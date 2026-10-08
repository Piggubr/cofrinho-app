-- Multiusuario por familia (household). Cada registro passa a pertencer a uma familia,
-- e o Hibernate filtra toda consulta por ela (@TenantId). Os dados que ja existiam sao
-- da familia inicial, criada com este mesmo id pela V4 do identity: os bancos sao
-- separados, entao o id fixo e o que liga os dois lados.

ALTER TABLE bank_connections ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE bank_connections ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_bank_connections_household ON bank_connections (household_id);

ALTER TABLE bank_accounts ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE bank_accounts ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_bank_accounts_household ON bank_accounts (household_id);
