-- Painel analitico (D-170): funil do Premium, receita estimada e plataformas.
--
-- Mesmo combinado do V32: schema "analitico", lido so pelo usuario piggu_analitico, e cada
-- view devolve contagens. Nenhum e-mail, nome, documento, id ou valor em dinheiro sai daqui;
-- o preco dos planos fica no painel do Grafana. Dias e meses no horario de Brasilia.

-- Funil de agora: uma linha so.
CREATE OR REPLACE VIEW ${esquema_analitico}.funil AS
SELECT (SELECT count(*) FROM users)                                                       AS contas,
       (SELECT count(*) FROM users WHERE email_verified)                                  AS contas_confirmadas,
       (SELECT count(*) FROM households)                                                  AS familias,
       (SELECT count(DISTINCT household_id) FROM users WHERE trial_used_at IS NOT NULL)   AS familias_com_teste,
       (SELECT count(*) FROM households WHERE first_charge_at IS NOT NULL)                AS familias_que_ja_pagaram,
       (SELECT count(*) FROM households
        WHERE first_charge_at IS NOT NULL AND premium_until > now())                      AS familias_pagando,
       (SELECT count(*) FROM households
        WHERE first_charge_at IS NOT NULL AND (premium_until IS NULL OR premium_until <= now())) AS familias_que_sairam;

-- Conversao por mes de cadastro: das contas criadas no mes, quantas confirmaram o e-mail,
-- fizeram o teste gratis e estao numa familia que ja pagou.
CREATE OR REPLACE VIEW ${esquema_analitico}.conversao_por_mes AS
SELECT date_trunc('month', u.created_at AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       count(*)                                           AS contas,
       count(*) FILTER (WHERE u.email_verified)           AS confirmaram,
       count(*) FILTER (WHERE u.trial_used_at IS NOT NULL) AS fizeram_teste,
       count(*) FILTER (WHERE h.first_charge_at IS NOT NULL) AS em_familia_pagante
FROM users u
JOIN households h ON h.id = u.household_id
GROUP BY 1;

-- Assinaturas pagas que acabaram (cancelou ou nao renovou), pelo dia em que venceram.
CREATE OR REPLACE VIEW ${esquema_analitico}.assinaturas_encerradas_por_dia AS
SELECT (premium_until AT TIME ZONE 'America/Sao_Paulo')::date AS dia,
       count(*)                                               AS familias
FROM households
WHERE first_charge_at IS NOT NULL AND premium_until <= now()
GROUP BY 1;

-- Familias pagando agora, por loja, plano e periodo (base da receita estimada no painel).
-- shortcut: o periodo e deduzido do vencimento (mais de 45 dias a frente = anual), porque a
-- familia nao guarda o periodo; troque pela coluna se um dia ela existir.
CREATE OR REPLACE VIEW ${esquema_analitico}.pagantes_por_plano AS
SELECT plan_source                                                                  AS loja,
       plan_kind                                                                    AS plano,
       CASE WHEN premium_until > now() + interval '45 days' THEN 'ANUAL' ELSE 'MENSAL' END AS periodo,
       count(*)                                                                     AS familias
FROM households
WHERE first_charge_at IS NOT NULL AND premium_until > now() AND plan_source IS NOT NULL
GROUP BY 1, 2, 3;

-- Sessoes abertas por plataforma, pelo User-Agent guardado na sessao. O app Android nativo
-- manda "Piggu-Android/<versao>"; o iOS nativo, o padrao do URLSession ("Piggu/<build>
-- CFNetwork/..."); o app em Capacitor vai pela WebView ("; wv)" no Android, iPhone sem
-- "Safari" no iOS). So o rotulo sai daqui, nunca o texto do User-Agent.
CREATE OR REPLACE VIEW ${esquema_analitico}.sessoes_por_plataforma AS
SELECT plataforma, count(DISTINCT user_id) AS pessoas, count(*) AS sessoes
FROM (SELECT user_id,
             CASE
                 WHEN user_agent LIKE 'Piggu-Android/%'                           THEN 'App Android (nativo)'
                 WHEN user_agent LIKE 'Piggu%CFNetwork%'                          THEN 'App iOS (nativo)'
                 WHEN user_agent LIKE '%; wv)%'                                   THEN 'App Android (Capacitor)'
                 WHEN user_agent ~ '(iPhone|iPad)' AND user_agent NOT LIKE '%Safari%' THEN 'App iOS (Capacitor)'
                 WHEN user_agent ~ '(iPhone|iPad|Android|Mobile)'                 THEN 'Site no celular'
                 WHEN coalesce(user_agent, '') = ''                               THEN 'Desconhecido'
                 ELSE 'Site no computador'
             END AS plataforma
      FROM refresh_sessions
      WHERE revoked_at IS NULL AND expires_at > now()) s
GROUP BY plataforma;

-- Versao dos apps nativos nas sessoes abertas. A versao so passa se for numero
-- ("2.0.0", "5"): qualquer outro texto no User-Agent vira "outra".
CREATE OR REPLACE VIEW ${esquema_analitico}.versoes_do_app AS
SELECT plataforma, coalesce(versao, 'outra') AS versao, count(DISTINCT user_id) AS pessoas
FROM (SELECT user_id,
             CASE WHEN user_agent LIKE 'Piggu-Android/%' THEN 'Android' ELSE 'iOS' END AS plataforma,
             substring(user_agent FROM '^Piggu[A-Za-z-]*/([0-9][0-9.]{0,15})(?: |$)')    AS versao
      FROM refresh_sessions
      WHERE revoked_at IS NULL AND expires_at > now()
        AND (user_agent LIKE 'Piggu-Android/%' OR user_agent LIKE 'Piggu%CFNetwork%')) s
GROUP BY 1, 2;

-- Aparelhos que recebem push, por plataforma.
CREATE OR REPLACE VIEW ${esquema_analitico}.aparelhos_com_push AS
SELECT platform AS plataforma, count(*) AS aparelhos, count(DISTINCT user_id) AS pessoas
FROM push_devices
GROUP BY 1;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'piggu_analitico') THEN
        GRANT USAGE ON SCHEMA ${esquema_analitico} TO piggu_analitico;
        GRANT SELECT ON ALL TABLES IN SCHEMA ${esquema_analitico} TO piggu_analitico;
    END IF;
END
$$;
