#!/usr/bin/env bash
# Prepara a troca do e-mail pelo id da pessoa (S3) numa instalacao que JA TEM DADOS.
#
# Os servicos marcavam quem lancou pelo e-mail; agora marcam pelo id da conta, que so o
# identity conhece. Este script copia o de-para (id e e-mail de cada conta) do banco do
# identity para uma tabela de_para_usuarios em cada um dos outros bancos. Na subida da
# versao nova, a migration de cada servico usa essa tabela para trocar o e-mail pelo id
# e depois a apaga.
#
# Quando rodar: com o Postgres de pe e os servicos ainda na versao antiga (ou parados),
# antes de subir a versao nova. Instalacao nova, sem dados, nao precisa: as migrations
# so trocam o tipo das colunas vazias. Rodar duas vezes nao faz mal.
#
# Uso: backend/scripts/migrar-emails-para-ids.sh
#   PIGGU_PG_CONTAINER  container do Postgres (padrao: o do docker compose)
#   PIGGU_PG_USER       superusuario (padrao: piggu)
set -euo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=SCRIPTDIR/comum-backup.sh
source "$AQUI/comum-backup.sh"

CONTAINER=$(container_do_postgres)
psql_em() { docker exec -i -e PGOPTIONS="-c client_min_messages=warning" "$CONTAINER" psql -U "$PG_USER" -d "$1" -AtqX -v ON_ERROR_STOP=1 "${@:2}"; }

contas=$(psql_em piggu_identity -c "SELECT count(*) FROM users")
echo "Contas no identity: $contas"

for banco in $BANCOS; do
  [ "$banco" = piggu_identity ] && continue
  psql_em "$banco" -c "DROP TABLE IF EXISTS de_para_usuarios;
                       CREATE TABLE de_para_usuarios (id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL UNIQUE);"
  psql_em piggu_identity -c "COPY (SELECT id, lower(email) FROM users) TO STDOUT" \
    | psql_em "$banco" -c "COPY de_para_usuarios FROM STDIN"
  # Com um usuario por servico (01-criar-bancos.sh), a migration roda como ele e precisa
  # poder apagar a tabela no fim.
  psql_em "$banco" -c "DO \$\$ BEGIN
      IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_database()) THEN
        EXECUTE format('ALTER TABLE de_para_usuarios OWNER TO %I', current_database());
      END IF;
    END \$\$;"
  echo "$banco: de_para_usuarios com $(psql_em "$banco" -c "SELECT count(*) FROM de_para_usuarios") contas"
done

echo "Pronto. Suba a versao nova: cada servico troca o e-mail pelo id e apaga o de-para."
