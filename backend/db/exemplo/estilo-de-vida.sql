-- Dados de exemplo de lugares, filmes e compras. Rodado por scripts/dados-de-exemplo.sh.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO places (household_id, name, category, location, rating, comment, visit_date, tags, amount, user_id) VALUES
  (:'familia'::uuid, 'Cantina da praça', 'Restaurante', 'Centro', 5, 'Massa caseira, voltar com a família.',
   current_date - 12, ARRAY['Favorito', 'Voltaria'], 186.00, :'pessoa'::uuid),
  (:'familia'::uuid, 'Parque da cidade', 'Passeio', 'Zona norte', 4, 'Bom para o domingo de manhã.',
   current_date - 30, ARRAY['Voltaria'], 0, :'pessoa'::uuid);

INSERT INTO movies (household_id, tmdb_id, title, year, poster, tmdb_rating, synopsis, watched, ratings, user_id) VALUES
  (:'familia'::uuid, '', 'Filme da sexta', '2024', '', 0, 'Entrada manual de exemplo.', false, '{}'::jsonb, :'pessoa'::uuid),
  (:'familia'::uuid, '', 'Clássico de domingo', '1999', '', 0, 'Já vimos e gostamos.', true,
   jsonb_build_object(:'pessoa'::text, 5), :'pessoa'::uuid);

INSERT INTO shopping_items (household_id, item, quantity, list_name, purchased, user_id) VALUES
  (:'familia'::uuid, 'Café', '2 pacotes', 'Compras', false, :'pessoa'::uuid),
  (:'familia'::uuid, 'Leite', '6 caixas', 'Compras', false, :'pessoa'::uuid),
  (:'familia'::uuid, 'Detergente', '1', 'Compras', true, :'pessoa'::uuid),
  (:'familia'::uuid, 'Fone sem fio', '1', 'Desejos', false, :'pessoa'::uuid);

COMMIT;
