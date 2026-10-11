-- Quem lancou passa a ser o id da conta, nao mais o e-mail: o e-mail fica so no
-- identity, e trocar de e-mail nao separa a pessoa do que ela lancou.
-- A nota de cada pessoa no filme tambem: o JSON {"e-mail": nota} vira {"id": nota}.

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

SELECT pg_temp.trocar_email_por_id('places', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('custom_place_tags', 'created_by', 'created_by_id');
SELECT pg_temp.trocar_email_por_id('movies', 'user_email', 'user_id');
SELECT pg_temp.trocar_email_por_id('shopping_items', 'user_email', 'user_id');

DO $$
DECLARE
    faltando bigint;
BEGIN
    IF to_regclass('de_para_usuarios') IS NOT NULL THEN
        EXECUTE $q$
            UPDATE movies m SET ratings = (
                SELECT coalesce(jsonb_object_agg(coalesce(d.id::text, n.key), n.value), '{}'::jsonb)
                FROM jsonb_each(m.ratings) n
                LEFT JOIN de_para_usuarios d ON d.email = lower(n.key))
            WHERE m.ratings <> '{}'::jsonb
        $q$;
    END IF;
    SELECT count(*) INTO faltando FROM movies m, jsonb_object_keys(m.ratings) chave
    WHERE chave !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
    IF faltando > 0 THEN
        RAISE EXCEPTION '% nota(s) de filme sem conta para o e-mail. Rode backend/scripts/migrar-emails-para-ids.sh antes de subir esta versao.',
            faltando;
    END IF;
END
$$;

DROP FUNCTION pg_temp.trocar_email_por_id(text, text, text);
DROP TABLE IF EXISTS de_para_usuarios;
