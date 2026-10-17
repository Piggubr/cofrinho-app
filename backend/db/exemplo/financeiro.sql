-- Dados de exemplo do financeiro para uma familia de desenvolvimento.
-- Rodado por scripts/dados-de-exemplo.sh, que passa :familia e :pessoa. Datas relativas
-- a hoje: tres meses de gastos e receitas, para o painel e os relatorios terem o que mostrar.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE meses ON COMMIT DROP AS
SELECT n AS atras, (date_trunc('month', current_date) - make_interval(months => n))::date AS inicio
FROM generate_series(0, 2) AS n;

-- Um dia do mes que nunca passa de hoje nem do fim do mes.
CREATE FUNCTION pg_temp.dia(inicio date, d int) RETURNS date LANGUAGE sql AS $$
  SELECT LEAST(current_date, (inicio + make_interval(days => d - 1))::date,
               (inicio + interval '1 month' - interval '1 day')::date)
$$;

INSERT INTO payment_accounts (household_id, name, kind, closing_day, due_day, user_id) VALUES
  (:'familia'::uuid, 'Conta corrente', 'CONTA', NULL, NULL, :'pessoa'::uuid),
  (:'familia'::uuid, 'Cartão de crédito', 'CARTAO', 3, 10, :'pessoa'::uuid);

INSERT INTO incomes (household_id, income_date, description, category, amount, user_id)
SELECT :'familia'::uuid, pg_temp.dia(inicio, 5), 'Salário', 'Salário', 6200, :'pessoa'::uuid FROM meses
UNION ALL
SELECT :'familia'::uuid, pg_temp.dia(inicio, 18), 'Freela de design', 'Extra', 850, :'pessoa'::uuid FROM meses WHERE atras = 1;

INSERT INTO expenses (household_id, expense_date, receipt_id, merchant, item, category, amount, kind, source,
                      account_id, user_id)
SELECT :'familia'::uuid, pg_temp.dia(m.inicio, g.dia), gen_random_uuid(), g.loja, g.item, g.categoria,
       round(g.valor * (1 + 0.08 * m.atras)::numeric, 2), g.tipo, 'Manual',
       CASE WHEN g.cartao THEN (SELECT id FROM payment_accounts WHERE household_id = :'familia'::uuid AND kind = 'CARTAO') END,
       :'pessoa'::uuid
FROM meses m
CROSS JOIN (VALUES
  (1,  'Imobiliária',      'Aluguel',            'Aluguel',        1800.00, 'Fixo',     false),
  (3,  'Supermercado',     'Compra do mês',      'Alimentação',     742.35, 'Variavel', true),
  (4,  'Posto',            'Gasolina',           'Transporte',      220.00, 'Variavel', true),
  (6,  'Padaria',          'Pão e café',         'Alimentação',      38.90, 'Variavel', false),
  (8,  'Farmácia',         'Remédios',           'Farmácia/Saúde',  126.40, 'Variavel', true),
  (9,  'Streaming',        'Assinatura de vídeo','Assinaturas',      55.90, 'Fixo',     true),
  (12, 'Restaurante',      'Jantar de sábado',   'Lazer',           189.00, 'Variavel', true),
  (14, 'Supermercado',     'Reposição',          'Alimentação',     213.70, 'Variavel', true),
  (16, 'Aplicativo',       'Corridas',           'Transporte',       64.20, 'Variavel', true),
  (20, 'Cinema',           'Ingressos',          'Lazer',            72.00, 'Variavel', true),
  (22, 'Loja de presentes','Aniversário da tia', 'Gastos Extras',   150.00, 'Variavel', true),
  (25, 'Feira',            'Frutas e verduras',  'Alimentação',      58.30, 'Variavel', false)
) AS g(dia, loja, item, categoria, valor, tipo, cartao)
-- No mes atual, so o que ja aconteceu.
WHERE m.atras > 0 OR g.dia <= extract(day FROM current_date);

INSERT INTO category_budgets (household_id, category, limit_amount, user_id) VALUES
  (:'familia'::uuid, 'Alimentação', 1100, :'pessoa'::uuid),
  (:'familia'::uuid, 'Lazer', 300, :'pessoa'::uuid),
  (:'familia'::uuid, 'Transporte', 400, :'pessoa'::uuid);

INSERT INTO monthly_goals (household_id, reference_month, limit_amount, user_id)
SELECT :'familia'::uuid, to_char(inicio, 'YYYY-MM'), 4200, :'pessoa'::uuid FROM meses;

INSERT INTO recurring_bills (household_id, description, category, amount, due_day, auto_launch, active, user_id) VALUES
  (:'familia'::uuid, 'Luz', 'Outros', 180, 15, false, true, :'pessoa'::uuid),
  (:'familia'::uuid, 'Internet', 'Assinaturas', 119.90, 20, false, true, :'pessoa'::uuid),
  (:'familia'::uuid, 'Condomínio', 'Aluguel', 520, 8, false, true, :'pessoa'::uuid);

INSERT INTO savings_goals (id, household_id, name, target_amount, deadline, user_id) VALUES
  (gen_random_uuid(), :'familia'::uuid, 'Viagem de férias', 6000, (date_trunc('month', current_date) + interval '8 months')::date, :'pessoa'::uuid);

INSERT INTO piggy_deposits (household_id, deposit_date, amount, user_id, goal_id)
SELECT :'familia'::uuid, pg_temp.dia(inicio, 6), 400, :'pessoa'::uuid,
       (SELECT id FROM savings_goals WHERE household_id = :'familia'::uuid LIMIT 1)
FROM meses
UNION ALL
SELECT :'familia'::uuid, pg_temp.dia(inicio, 6), 150, :'pessoa'::uuid, NULL FROM meses WHERE atras = 2;

INSERT INTO notes (household_id, title, body, user_id) VALUES
  (:'familia'::uuid, 'Revisar seguro do carro', 'Comparar três cotações antes de renovar.', :'pessoa'::uuid);

COMMIT;
