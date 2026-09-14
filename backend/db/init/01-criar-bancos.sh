#!/bin/bash
# Cada servico tem o proprio banco. Isso e o que impede um servico de ler ou
# escrever nas tabelas do outro por atalho, que e a forma mais comum de um
# conjunto de microservicos virar um monolito distribuido sem ninguem perceber.
set -e

for banco in piggu_identity piggu_finance piggu_rewards piggu_lifestyle piggu_media; do
  echo "Criando banco $banco"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    CREATE DATABASE $banco;
    GRANT ALL PRIVILEGES ON DATABASE $banco TO $POSTGRES_USER;
SQL
done
