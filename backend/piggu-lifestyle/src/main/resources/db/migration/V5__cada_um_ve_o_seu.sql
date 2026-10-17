-- Cada pessoa tem a propria lista de filmes: o mesmo filme pode estar na de duas pessoas.
DROP INDEX idx_movies_household_tmdb;
CREATE UNIQUE INDEX idx_movies_household_user_tmdb ON movies (household_id, user_id, tmdb_id) WHERE tmdb_id <> '';
