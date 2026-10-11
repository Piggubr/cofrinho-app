-- Dados de exemplo das moedas e premios. Rodado por scripts/dados-de-exemplo.sh.
\set ON_ERROR_STOP on
BEGIN;

INSERT INTO prizes (household_id, name, description, price, active, updated_by_id) VALUES
  (:'familia'::uuid, 'Sorvete no fim de semana', 'Escolhe o sabor.', 30, true, :'pessoa'::uuid),
  (:'familia'::uuid, 'Noite do filme', 'A pessoa escolhe o filme e a pipoca.', 50, true, :'pessoa'::uuid),
  (:'familia'::uuid, 'Passeio no parque', 'Com piquenique.', 80, true, :'pessoa'::uuid);

INSERT INTO coin_ledger (household_id, amount, reason, subject_user, actor_id, created_at) VALUES
  (:'familia'::uuid, 40, 'Arrumou o quarto a semana toda', 'USUARIA', :'pessoa'::uuid, now() - interval '9 days'),
  (:'familia'::uuid, 25, 'Ajudou no mercado', 'USUARIA', :'pessoa'::uuid, now() - interval '5 days'),
  (:'familia'::uuid, -30, 'Resgate: Sorvete no fim de semana', 'USUARIA', :'pessoa'::uuid, now() - interval '2 days'),
  (:'familia'::uuid, 20, 'Leu um livro', 'USUARIA', :'pessoa'::uuid, now() - interval '1 day');

INSERT INTO redemptions (household_id, prize_id, prize_name, price, ledger_id, user_id, created_at)
SELECT :'familia'::uuid, p.id, p.name, p.price,
       (SELECT id FROM coin_ledger WHERE household_id = :'familia'::uuid AND amount = -30 LIMIT 1),
       :'pessoa'::uuid, now() - interval '2 days'
FROM prizes p WHERE p.household_id = :'familia'::uuid AND p.price = 30;

COMMIT;
