-- Multiusuario por familia (household). Cada registro passa a pertencer a uma familia,
-- e o Hibernate filtra toda consulta por ela (@TenantId). Os dados que ja existiam sao
-- da familia inicial, criada com este mesmo id pela V4 do identity: os bancos sao
-- separados, entao o id fixo e o que liga os dois lados.

ALTER TABLE places ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE places ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_places_household ON places (household_id, visit_date DESC);

ALTER TABLE custom_place_tags ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE custom_place_tags ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_custom_place_tags_household ON custom_place_tags (household_id);

ALTER TABLE custom_place_tags DROP CONSTRAINT custom_place_tags_name_key;
ALTER TABLE custom_place_tags ADD CONSTRAINT custom_place_tags_household_name_key UNIQUE (household_id, name);

ALTER TABLE movies ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE movies ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_movies_household ON movies (household_id);

-- O mesmo filme pode estar na lista de familias diferentes.
DROP INDEX idx_movies_tmdb;
CREATE UNIQUE INDEX idx_movies_household_tmdb ON movies (household_id, tmdb_id) WHERE tmdb_id <> '';

ALTER TABLE shopping_items ADD COLUMN household_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE shopping_items ALTER COLUMN household_id DROP DEFAULT;
CREATE INDEX idx_shopping_items_household ON shopping_items (household_id, list_name, purchased);
