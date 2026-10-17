-- O financeiro marca quem lancou pelo id da conta, nao mais pelo e-mail: o e-mail
-- fica so no identity, e trocar de e-mail nao separa a pessoa do que ela lancou.

-- Troca a coluna de e-mail pela de id da pessoa. Instalacao nova: as tabelas estao
-- vazias e so muda o tipo. Instalacao com dados: o backend/scripts/migrar-emails-para-ids.sh
-- copia antes a tabela de_para_usuarios (id e e-mail das contas do identity) para
-- este banco. Linha cujo e-mail nao esta no de-para para a migracao com erro: dado
-- nao perde o dono em silencio. O autor anonimizado ('conta-excluida') vira nulo e o
-- 'sistema' (job agendado) vira o id zero, o TrilhaDeAuditoria.SISTEMA.
CREATE OR REPLACE FUNCTION pg_temp.trocar_email_por_id(tabela text, de text, para text) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    faltando bigint;
BEGIN
    EXECUTE format('ALTER TABLE %I ADD COLUMN %I UUID', tabela, para);
    IF to_regclass('de_para_usuarios') IS NOT NULL THEN
        EXECUTE format('UPDATE %I t SET %I = d.id FROM de_para_usuarios d WHERE lower(t.%I) = d.email',
                       tabela, para, de);
    END IF;
    EXECUTE format('UPDATE %I SET %I = ''00000000-0000-0000-0000-000000000000'' WHERE lower(%I) = ''sistema''',
                   tabela, para, de);
    EXECUTE format('SELECT count(*) FROM %I WHERE %I IS NULL AND lower(%I) NOT IN (''conta-excluida'', ''sistema'')',
                   tabela, para, de) INTO faltando;
    IF faltando > 0 THEN
        RAISE EXCEPTION '% linha(s) de % sem conta para o e-mail. Rode backend/scripts/migrar-emails-para-ids.sh antes de subir esta versao.',
            faltando, tabela;
    END IF;
    EXECUTE format('ALTER TABLE %I DROP COLUMN %I', tabela, de);
END
$$;

SELECT pg_temp.trocar_email_por_id('expenses', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('incomes', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('recurring_bills', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('category_budgets', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('category_rules', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('payment_accounts', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('piggy_deposits', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('monthly_goals', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('product_memory', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('notes', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('consents', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('expense_shares', 'member_email', 'member_id');
SELECT pg_temp.trocar_email_por_id('custom_categories', 'created_by', 'created_by_id');
SELECT pg_temp.trocar_email_por_id('eventos_de_auditoria', 'autor_email', 'autor_id');

CREATE INDEX idx_piggy_deposits_user ON piggy_deposits (user_id);
CREATE INDEX idx_consents_pessoa ON consents (household_id, user_id, purpose);

DROP FUNCTION pg_temp.trocar_email_por_id(text, text, text);
DROP TABLE IF EXISTS de_para_usuarios;
