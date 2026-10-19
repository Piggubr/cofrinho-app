-- Painel analitico (Grafana, backend/observabilidade): o quanto cada recurso e usado.
--
-- Mesmo combinado do V31 do identity: schema "analitico", lido so pelo usuario
-- piggu_analitico, e cada view devolve contagens. Nenhum valor em dinheiro, descricao,
-- categoria, loja, id de pessoa ou de familia sai daqui. Dias no horario de Brasilia.
CREATE SCHEMA IF NOT EXISTS analitico;

-- Lancamentos por dia e quantas familias lancaram algo (o "uso de verdade" do app).
CREATE OR REPLACE VIEW analitico.lancamentos_por_dia AS
SELECT dia,
       count(*) FILTER (WHERE tipo = 'gasto')   AS gastos,
       count(*) FILTER (WHERE tipo = 'receita') AS receitas,
       count(DISTINCT household_id)             AS familias
FROM (SELECT (created_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia, household_id, 'gasto' AS tipo
      FROM expenses
      UNION ALL
      SELECT (created_at AT TIME ZONE 'America/Sao_Paulo')::date, household_id, 'receita'
      FROM incomes) l
GROUP BY dia;

-- De onde vem cada gasto: digitado, foto do recibo, extrato importado, conta fixa...
-- A origem vem do app (texto livre de ate 30 letras): so as conhecidas aparecem; o resto
-- entra como "Outra", para nada digitado por alguem chegar ao painel.
CREATE OR REPLACE VIEW analitico.gastos_por_origem AS
SELECT (created_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
       CASE WHEN source IN ('Manual', 'Foto', 'OCR', 'GEMINI', 'Extrato', 'Conta fixa', 'Open Finance')
            THEN source ELSE 'Outra' END                 AS origem,
       count(*)                                          AS gastos
FROM expenses
GROUP BY 1, 2;

-- Recursos por mes: perguntas ao assistente, notas lidas e quantas familias usaram.
CREATE OR REPLACE VIEW analitico.recursos_por_mes AS
SELECT mes,
       sum(perguntas_ao_assistente)::bigint AS perguntas_ao_assistente,
       sum(notas_lidas)::bigint             AS notas_lidas,
       count(DISTINCT household_id) FILTER (WHERE perguntas_ao_assistente > 0) AS familias_com_assistente,
       count(DISTINCT household_id) FILTER (WHERE notas_lidas > 0)             AS familias_com_nota
FROM (SELECT reference_month AS mes, household_id, questions AS perguntas_ao_assistente, 0 AS notas_lidas
      FROM assistant_usage
      UNION ALL
      SELECT reference_month, household_id, 0, reads
      FROM receipt_usage) r
GROUP BY mes;

-- Quantas familias usam cada recurso hoje (tem ao menos um registro).
CREATE OR REPLACE VIEW analitico.familias_por_recurso AS
SELECT 'Cofrinhos por objetivo' AS recurso, count(DISTINCT household_id) AS familias FROM savings_goals
UNION ALL SELECT 'Orcamento por categoria', count(DISTINCT household_id) FROM category_budgets
UNION ALL SELECT 'Contas fixas', count(DISTINCT household_id) FROM recurring_bills
UNION ALL SELECT 'Contas e cartoes', count(DISTINCT household_id) FROM payment_accounts
UNION ALL SELECT 'Regras de categoria', count(DISTINCT household_id) FROM category_rules
UNION ALL SELECT 'Receitas', count(DISTINCT household_id) FROM incomes
UNION ALL SELECT 'Gastos divididos', count(DISTINCT household_id) FROM expense_shares;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'piggu_analitico') THEN
        GRANT USAGE ON SCHEMA analitico TO piggu_analitico;
        GRANT SELECT ON ALL TABLES IN SCHEMA analitico TO piggu_analitico;
    END IF;
END
$$;
