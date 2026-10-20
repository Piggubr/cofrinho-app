#!/bin/bash
# Cada servico tem o proprio banco e o proprio usuario do Postgres (S4). O usuario
# piggu_finance so entra no banco piggu_finance, e assim por diante: uma injecao de SQL
# num servico nao alcanca as tabelas dos outros. O usuario piggu (superusuario) fica so
# para administrar: backup, restauracao e este script.
#
# O Postgres do Docker so roda este script na primeira subida, com o volume vazio.
# Ele e idempotente: cria o que falta, acerta senhas, donos e permissoes, e passa para
# o usuario do servico as tabelas criadas antes pelo piggu. Rode de novo a mao quando um
# servico ganhar banco, ao trocar uma senha DB_PASSWORD_* e na primeira atualizacao de
# uma instalacao que ainda usa so o piggu:
#
#   docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh
#
# Senhas: DB_PASSWORD_IDENTITY, DB_PASSWORD_FINANCE, ... no ambiente do container; sem
# elas, vale a do superusuario (POSTGRES_PASSWORD), o que so serve em desenvolvimento.
set -e

for servico in identity finance rewards lifestyle media banking; do
  banco=piggu_$servico
  variavel=DB_PASSWORD_${servico^^}
  senha=${!variavel:-$POSTGRES_PASSWORD}

  # --dbname postgres: sem ele o psql procura um banco com o nome do usuario, que nao existe.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v banco="$banco" -v senha="$senha" <<-'SQL'
    SELECT format('CREATE ROLE %I LOGIN', :'banco')
    WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'banco')\gexec
    SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'banco', :'senha')\gexec
    SELECT format('CREATE DATABASE %I OWNER %I', :'banco', :'banco')
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'banco')\gexec
    SELECT format('ALTER DATABASE %I OWNER TO %I', :'banco', :'banco')\gexec
    -- Por padrao todo usuario conecta em todo banco; aqui so o dono (e o superusuario).
    SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'banco')\gexec
    SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'banco', :'banco')\gexec
SQL

  # Instalacao antiga: as tabelas foram criadas pelo piggu. Passa tudo para o dono do banco,
  # menos o que e de extensao (pgcrypto) e as sequencias presas a uma coluna, que mudam
  # de dono junto com a tabela.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$banco" -v dono="$banco" <<-'SQL'
    SELECT format('ALTER SCHEMA %I OWNER TO %I', n.nspname, :'dono')
    FROM pg_namespace n
    WHERE n.nspname NOT IN ('public', 'information_schema') AND n.nspname NOT LIKE 'pg\_%'
      AND n.nspowner <> (SELECT oid FROM pg_roles WHERE rolname = :'dono')\gexec
    SELECT format('ALTER %s %I.%I OWNER TO %I',
                  CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW'
                                 WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
                  n.nspname, c.relname, :'dono')
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('r', 'p', 'S', 'v', 'm')
      AND n.nspname NOT IN ('information_schema') AND n.nspname NOT LIKE 'pg\_%'
      AND c.relowner <> (SELECT oid FROM pg_roles WHERE rolname = :'dono')
      AND NOT EXISTS (SELECT FROM pg_depend d
                      WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                        AND d.deptype IN ('a', 'i', 'e'))\gexec
SQL
  echo "Banco e usuario $banco prontos"
done

# Leitura do painel analitico (Grafana, backend/observabilidade). O piggu_analitico so
# entra no identity e no financeiro, e la so le o schema "analitico": views com contagens
# (migrations V32 do identity e V18 do financeiro), sem e-mail, valor ou id de ninguem.
# Somente leitura e com prazo por consulta, para um painel aberto nao pesar no banco.
senha=${DB_PASSWORD_ANALITICO:-$POSTGRES_PASSWORD}
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v senha="$senha" <<-'SQL'
  SELECT 'CREATE ROLE piggu_analitico LOGIN'
  WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'piggu_analitico')\gexec
  SELECT format('ALTER ROLE piggu_analitico WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'senha')\gexec
  ALTER ROLE piggu_analitico SET default_transaction_read_only = on;
  ALTER ROLE piggu_analitico SET statement_timeout = '15s';
SQL
for servico in identity finance; do
  banco=piggu_$servico
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v banco="$banco" <<-'SQL'
    SELECT format('GRANT CONNECT ON DATABASE %I TO piggu_analitico', :'banco')\gexec
SQL
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$banco" -v dono="$banco" <<-'SQL'
    SELECT format('CREATE SCHEMA IF NOT EXISTS analitico AUTHORIZATION %I', :'dono')\gexec
    GRANT USAGE ON SCHEMA analitico TO piggu_analitico;
    GRANT SELECT ON ALL TABLES IN SCHEMA analitico TO piggu_analitico;
    SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA analitico GRANT SELECT ON TABLES TO piggu_analitico',
                  :'dono')\gexec
SQL
done
echo "Usuario piggu_analitico pronto (le so o schema analitico)"
