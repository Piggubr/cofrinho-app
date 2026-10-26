#!/usr/bin/env bash
# Esquema final dos seis bancos do Piggu, do jeito que fica depois de todas as migracoes.
# E de onde sai o docs/DATA_MODEL.md: rode de novo quando entrar migracao nova e confira
# o documento.
#
# Sobe um Postgres descartavel no Docker (o mesmo postgres:16-alpine do Compose), cria um
# banco por servico, aplica as migracoes de backend/<servico>/src/main/resources/db/migration
# com o Flyway (imagem flyway/flyway) e grava o pg_dump --schema-only de cada um.
# Nao toca no banco de desenvolvimento nem no de producao: o container e apagado no fim.
#
# Uso (da raiz do repositorio):
#   bash scripts/esquema-do-banco.sh            grava em /tmp/piggu-esquema-do-banco/
#   bash scripts/esquema-do-banco.sh /tmp/saida grava em /tmp/saida/
#
# Saida: <servico>.sql (o dump) e ultimas-migracoes.txt (a ultima versao aplicada em cada
# servico, a linha do topo do DATA_MODEL.md). Precisa so do Docker.
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/.." && pwd)
SAIDA=${1:-${TMPDIR:-/tmp}/piggu-esquema-do-banco}
CONTAINER=piggu-esquema-$$
POSTGRES_IMAGEM=${POSTGRES_IMAGEM:-postgres:16-alpine}
FLYWAY_IMAGEM=${FLYWAY_IMAGEM:-flyway/flyway:11}

mkdir -p "$SAIDA"
trap 'docker rm -f "$CONTAINER" >/dev/null 2>&1 || true' EXIT

docker run -d --name "$CONTAINER" -e POSTGRES_USER=piggu -e POSTGRES_PASSWORD=piggu \
  -e POSTGRES_DB=postgres "$POSTGRES_IMAGEM" >/dev/null
for _ in $(seq 1 60); do
  # pg_isready responde antes do fim do init; o SELECT so passa com o banco aceitando conexao.
  docker exec "$CONTAINER" psql -U piggu -d postgres -tAc 'SELECT 1' >/dev/null 2>&1 && break
  sleep 1
done

: > "$SAIDA/ultimas-migracoes.txt"
for servico in identity finance rewards lifestyle media banking; do
  banco=piggu_$servico
  docker exec "$CONTAINER" psql -q -v ON_ERROR_STOP=1 -U piggu -d postgres -c "CREATE DATABASE $banco"

  # --network container: o Flyway enxerga o Postgres em localhost, sem porta aberta na maquina.
  # O placeholder e o mesmo do application.yml do identity e do finance (painel analitico).
  docker run --rm --network "container:$CONTAINER" \
    -v "$RAIZ/backend/piggu-$servico/src/main/resources/db/migration:/flyway/sql:ro" \
    "$FLYWAY_IMAGEM" -url="jdbc:postgresql://localhost:5432/$banco" -user=piggu -password=piggu \
    -locations=filesystem:/flyway/sql -placeholders.esquema_analitico=analitico \
    -outputType=json migrate >/dev/null

  docker exec "$CONTAINER" pg_dump -U piggu -d "$banco" --schema-only --no-owner --no-privileges \
    --exclude-table=flyway_schema_history > "$SAIDA/$servico.sql"

  ultima=$(docker exec "$CONTAINER" psql -U piggu -d "$banco" -tAc \
    "SELECT 'V' || version || ' (' || description || ')' FROM flyway_schema_history
     WHERE success ORDER BY installed_rank DESC LIMIT 1")
  total=$(docker exec "$CONTAINER" psql -U piggu -d "$banco" -tAc \
    "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL")
  echo "$servico: $total migracoes, ate $ultima" | tee -a "$SAIDA/ultimas-migracoes.txt"
done

echo "Esquema gravado em $SAIDA"
