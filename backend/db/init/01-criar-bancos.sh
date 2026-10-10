#!/bin/bash
# Cada servico tem o proprio banco. Isso e o que impede um servico de ler ou
# escrever nas tabelas do outro por atalho, que e a forma mais comum de um
# conjunto de microservicos virar um monolito distribuido sem ninguem perceber.
#
# O Postgres do Docker so roda este script na primeira subida, com o volume vazio.
# Ele e idempotente (so cria o banco que falta), entao, quando um servico novo
# ganhar banco, quem ja tem o volume roda de novo a mao:
#
#   docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh
set -e

for banco in piggu_identity piggu_finance piggu_rewards piggu_lifestyle piggu_media piggu_banking; do
  # --dbname postgres: sem ele o psql procura um banco com o nome do usuario, que nao existe.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v banco="$banco" <<-'SQL'
    SELECT format('CREATE DATABASE %I', :'banco')
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'banco')\gexec
    SELECT format('GRANT ALL PRIVILEGES ON DATABASE %I TO %I', :'banco', current_user)\gexec
SQL
  echo "Banco $banco pronto"
done
