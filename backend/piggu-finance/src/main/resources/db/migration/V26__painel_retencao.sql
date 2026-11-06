-- Painel analitico (D-170): retencao das familias por semana.
--
-- Mesmo combinado do V18: so contagens no schema "analitico". "Ativa" e a familia que lancou
-- um gasto ou uma receita na semana; gasto que entrou sozinho (Open Finance, conta fixa) nao
-- conta, porque nao mostra ninguem usando o app. Semanas comecam na segunda, no horario de
-- Brasilia.
CREATE OR REPLACE VIEW ${esquema_analitico}.familias_ativas_por_semana AS
SELECT semana, count(DISTINCT household_id) AS familias
FROM (SELECT date_trunc('week', created_at AT TIME ZONE 'America/Sao_Paulo')::date AS semana, household_id
      FROM expenses
      WHERE source IS DISTINCT FROM 'Open Finance' AND source IS DISTINCT FROM 'Conta fixa'
      UNION ALL
      SELECT date_trunc('week', created_at AT TIME ZONE 'America/Sao_Paulo')::date, household_id
      FROM incomes) a
GROUP BY semana;

-- Coorte: familias pela semana do primeiro lancamento e quantas voltaram a lancar 1, 2, 4 e
-- 8 semanas depois. Semana que ainda nao terminou fica nula, nao zero.
CREATE OR REPLACE VIEW ${esquema_analitico}.retencao_por_semana AS
WITH atividade AS (
    SELECT DISTINCT date_trunc('week', created_at AT TIME ZONE 'America/Sao_Paulo')::date AS semana, household_id
    FROM expenses
    WHERE source IS DISTINCT FROM 'Open Finance' AND source IS DISTINCT FROM 'Conta fixa'
    UNION
    SELECT date_trunc('week', created_at AT TIME ZONE 'America/Sao_Paulo')::date, household_id
    FROM incomes),
inicio AS (
    SELECT household_id, min(semana) AS coorte FROM atividade GROUP BY household_id)
SELECT i.coorte                                                                           AS semana,
       count(DISTINCT i.household_id)                                                     AS familias,
       CASE WHEN i.coorte + 14 <= (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN count(DISTINCT a.household_id) FILTER (WHERE a.semana = i.coorte + 7)  END AS voltaram_semana_1,
       CASE WHEN i.coorte + 21 <= (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN count(DISTINCT a.household_id) FILTER (WHERE a.semana = i.coorte + 14) END AS voltaram_semana_2,
       CASE WHEN i.coorte + 35 <= (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN count(DISTINCT a.household_id) FILTER (WHERE a.semana = i.coorte + 28) END AS voltaram_semana_4,
       CASE WHEN i.coorte + 63 <= (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN count(DISTINCT a.household_id) FILTER (WHERE a.semana = i.coorte + 56) END AS voltaram_semana_8
FROM inicio i
JOIN atividade a ON a.household_id = i.household_id
GROUP BY i.coorte;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'piggu_analitico') THEN
        GRANT USAGE ON SCHEMA ${esquema_analitico} TO piggu_analitico;
        GRANT SELECT ON ALL TABLES IN SCHEMA ${esquema_analitico} TO piggu_analitico;
    END IF;
END
$$;
