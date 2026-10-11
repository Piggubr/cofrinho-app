-- Multiusuario por familia (household). Cada registro passa a pertencer a uma familia,
-- e o Hibernate filtra toda consulta por ela (@TenantId). Os dados que ja existiam sao
-- da familia inicial, criada com este mesmo id pela V4 do identity: os bancos sao
-- separados, entao o id fixo e o que liga os dois lados.

ALTER TABLE assets ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE assets ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_assets_household ON assets (household_id);

ALTER TABLE feed_photos ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE feed_photos ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_feed_photos_household ON feed_photos (household_id, month_key DESC);
