-- Painel analitico (Grafana, backend/observabilidade): contagens do uso do app.
--
-- Tudo fica no schema "analitico", e o usuario piggu_analitico (criado pelo
-- db/init/01-criar-bancos.sh) so le este schema. Cada view devolve contagens: nenhuma
-- traz e-mail, nome, documento, id de conta ou de familia. Os dias seguem o horario de
-- Brasilia. Uma view nova aqui ja nasce legivel pelo painel (ALTER DEFAULT PRIVILEGES
-- no script do banco).
-- ${esquema_analitico} vale "analitico" (spring.flyway.placeholders no application.yml).
CREATE SCHEMA IF NOT EXISTS ${esquema_analitico};

-- Contas criadas por dia (as excluidas saem da conta junto com a pessoa).
CREATE OR REPLACE VIEW ${esquema_analitico}.contas_novas_por_dia AS
SELECT (created_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
       count(*)                                           AS contas
FROM users
GROUP BY 1;

-- Testes gratis do Premium comecados por dia.
CREATE OR REPLACE VIEW ${esquema_analitico}.testes_gratis_por_dia AS
SELECT (trial_used_at AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
       count(*)                                               AS testes
FROM users
WHERE trial_used_at IS NOT NULL
GROUP BY 1;

-- Familias que passaram a ser Premium, por dia (inicio da assinatura atual).
CREATE OR REPLACE VIEW ${esquema_analitico}.assinaturas_por_dia AS
SELECT (premium_since AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
       count(*)                                               AS assinaturas
FROM households
WHERE premium_since IS NOT NULL
GROUP BY 1;

-- Retrato de agora: uma linha so.
CREATE OR REPLACE VIEW ${esquema_analitico}.resumo AS
SELECT (SELECT count(*) FROM users)                                                   AS contas,
       (SELECT count(*) FROM users WHERE active)                                      AS contas_ativas,
       (SELECT count(*) FROM users WHERE last_login_at >= now() - interval '1 day')   AS pessoas_no_dia,
       (SELECT count(*) FROM users WHERE last_login_at >= now() - interval '7 days')  AS pessoas_na_semana,
       (SELECT count(*) FROM users WHERE last_login_at >= now() - interval '30 days') AS pessoas_no_mes,
       (SELECT count(*) FROM households)                                              AS familias,
       (SELECT count(*) FROM households WHERE premium_until > now())                  AS familias_premium,
       (SELECT count(*) FROM households WHERE premium_until > now() AND first_charge_at IS NOT NULL) AS familias_pagantes,
       (SELECT count(*) FROM users WHERE password_hash IS NOT NULL)                    AS contas_com_senha,
       (SELECT count(*) FROM users WHERE NOT email_verified)                           AS contas_nao_confirmadas;

-- Premium ativo por origem e por plano. Sem origem: teste gratis ou cortesia.
CREATE OR REPLACE VIEW ${esquema_analitico}.premium_por_origem AS
SELECT coalesce(plan_source, 'TESTE_OU_CORTESIA') AS origem,
       plan_kind                                  AS plano,
       count(*)                                   AS familias
FROM households
WHERE premium_until > now()
GROUP BY 1, 2;

-- Tamanho das familias: quantas tem 1 pessoa, 2, 3...
CREATE OR REPLACE VIEW ${esquema_analitico}.familias_por_tamanho AS
SELECT pessoas, count(*) AS familias
FROM (SELECT household_id, count(*) AS pessoas FROM users GROUP BY household_id) f
GROUP BY pessoas;

-- Leitura do painel. Sem o usuario (desenvolvimento, testes) nao ha o que conceder; o
-- 01-criar-bancos.sh concede de novo quando cria o usuario depois desta migration.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'piggu_analitico') THEN
        GRANT USAGE ON SCHEMA ${esquema_analitico} TO piggu_analitico;
        GRANT SELECT ON ALL TABLES IN SCHEMA ${esquema_analitico} TO piggu_analitico;
    END IF;
END
$$;
