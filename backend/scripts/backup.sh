#!/usr/bin/env bash
# Backup diario dos seis bancos do Piggu (item S1/DB1). Rode na VPS, pelo cron:
#
#   15 3 * * *  /opt/piggu/backend/scripts/backup.sh >> /var/log/piggu-backup.log 2>&1
#
# O que faz, em ordem:
#   1. pg_dump de cada banco (formato custom, ja comprimido), dos papeis do Postgres e
#      as fotos guardadas no disco pelo piggu-media (fotos.tar).
#      A contagem de linhas de cada tabela sai na mesma foto do dump, para o
#      testar-restauracao.sh conferir depois que nada se perdeu.
#   2. Confere que cada dump abre (pg_restore --list) e grava o SHA256SUMS.
#   3. Junta tudo em piggu-AAAA-MM-DD.tar, cifrado com GPG quando ha senha.
#   4. Guarda 7 diarios e 4 semanais (o do domingo), apagando os mais velhos.
#   5. Copia para fora da VPS com o rclone, so se o arquivo estiver cifrado.
#
# Variaveis (todas opcionais; os padroes servem para a VPS com docker compose):
#   PIGGU_BACKUP_DIR            pasta dos backups              (/var/backups/piggu)
#   PIGGU_BACKUP_DIARIOS        quantos diarios guardar         (7)
#   PIGGU_BACKUP_SEMANAIS       quantos semanais guardar        (4)
#   PIGGU_BACKUP_DIA_SEMANAL    dia do semanal, 1=seg ... 7=dom (7)
#   PIGGU_BACKUP_SENHA_ARQUIVO  arquivo com a senha do GPG; sem ele o backup fica
#                               sem cifra e NAO sai da VPS
#   PIGGU_BACKUP_DESTINO        remoto do rclone para a copia fora da VPS, por exemplo
#                               b2-piggu:piggu-backups (Backblaze B2) ou r2:piggu-backups
#   PIGGU_PG_CONTAINER          container do Postgres (padrao: o do docker compose)
#   PIGGU_PG_USER               usuario do Postgres             (piggu)
#   PIGGU_BANCOS                bancos, separados por espaco    (os seis do Piggu)
#   PIGGU_BACKUP_VOLUME_FOTOS   volume das fotos do piggu-media (padrao: o *piggu-fotos
#                               do docker compose; "nenhum" deixa as fotos de fora)
set -euo pipefail
umask 077

AQUI=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=SCRIPTDIR/comum-backup.sh
source "$AQUI/comum-backup.sh"

DIR=${PIGGU_BACKUP_DIR:-/var/backups/piggu}
DIARIOS=${PIGGU_BACKUP_DIARIOS:-7}
SEMANAIS=${PIGGU_BACKUP_SEMANAIS:-4}
DIA_SEMANAL=${PIGGU_BACKUP_DIA_SEMANAL:-7}
SENHA_ARQUIVO=${PIGGU_BACKUP_SENHA_ARQUIVO:-}
DESTINO=${PIGGU_BACKUP_DESTINO:-}

if [ -n "$DESTINO" ] && [ -z "$SENHA_ARQUIVO" ]; then
  falhar "PIGGU_BACKUP_DESTINO exige PIGGU_BACKUP_SENHA_ARQUIVO: dado das familias nao sai da VPS sem cifra."
fi
if [ -n "$SENHA_ARQUIVO" ] && [ ! -s "$SENHA_ARQUIVO" ]; then
  falhar "Arquivo de senha vazio ou inexistente: $SENHA_ARQUIVO"
fi

CONTAINER=$(container_do_postgres)
HOJE=$(date +%F)
NOME="piggu-$HOJE"
mkdir -p "$DIR/diario" "$DIR/semanal"
PARCIAL=$(mktemp -d "$DIR/.parcial-XXXXXX")
trap 'rm -rf "$PARCIAL"' EXIT

# Dump e contagem na mesma foto do banco: uma sessao abre uma transacao REPEATABLE
# READ, exporta o snapshot, conta as linhas e segura a transacao aberta enquanto o
# pg_dump usa o mesmo snapshot. Sem isso, um gasto lancado no meio do caminho faria
# a contagem e o dump discordarem.
dump_do_banco() {
  local banco=$1 snapshot linha entrada
  coproc SESSAO { docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$banco" -AtqX -v ON_ERROR_STOP=1; }
  echo "BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT pg_export_snapshot();" >&"${SESSAO[1]}"
  read -r snapshot <&"${SESSAO[0]}"
  echo "$SQL_CONTAGEM SELECT '#fim';" >&"${SESSAO[1]}"
  : > "$PARCIAL/$banco.contagem"
  while read -r linha <&"${SESSAO[0]}" && [ "$linha" != "#fim" ]; do
    echo "$linha" >> "$PARCIAL/$banco.contagem"
  done
  docker exec "$CONTAINER" pg_dump -U "$PG_USER" -d "$banco" -Fc --snapshot="$snapshot" > "$PARCIAL/$banco.dump"
  echo "COMMIT;" >&"${SESSAO[1]}"
  entrada=${SESSAO[1]}
  exec {entrada}>&-
  wait "$SESSAO_PID"
  # Um dump truncado so aparece na hora de restaurar; aqui ele ja falha.
  docker exec -i "$CONTAINER" pg_restore --list < "$PARCIAL/$banco.dump" > /dev/null
}

for banco in $BANCOS; do
  echo "$(date '+%F %T') dump de $banco"
  dump_do_banco "$banco"
done
docker exec "$CONTAINER" pg_dumpall -U "$PG_USER" --globals-only > "$PARCIAL/globais.sql"

# Fotos: lidas por um container de passagem com a mesma imagem do Postgres (ja baixada).
VOLUME_FOTOS=${PIGGU_BACKUP_VOLUME_FOTOS:-$(docker volume ls -q | grep -E '(^|_)piggu-fotos$' | head -n 1 || true)}
if [ -n "$VOLUME_FOTOS" ] && [ "$VOLUME_FOTOS" != "nenhum" ]; then
  echo "$(date '+%F %T') fotos do volume $VOLUME_FOTOS"
  docker run --rm -v "$VOLUME_FOTOS:/fotos:ro" --entrypoint tar "$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")" \
    -C /fotos -cf - . > "$PARCIAL/fotos.tar"
else
  echo "$(date '+%F %T') sem volume de fotos; so os bancos"
fi
(
  cd "$PARCIAL"
  conferir=(*.dump *.contagem globais.sql)
  [ ! -f fotos.tar ] || conferir+=(fotos.tar)
  sha256sum -- "${conferir[@]}" > SHA256SUMS
)

tar -C "$PARCIAL" -cf "$PARCIAL/$NOME.tar" --exclude "$NOME.tar" .
ARQUIVO="$NOME.tar"
if [ -n "$SENHA_ARQUIVO" ]; then
  gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-file "$SENHA_ARQUIVO" \
    --symmetric --cipher-algo AES256 -o "$PARCIAL/$NOME.tar.gpg" "$PARCIAL/$NOME.tar"
  ARQUIVO="$NOME.tar.gpg"
fi
mv "$PARCIAL/$ARQUIVO" "$DIR/diario/$ARQUIVO"
echo "$(date '+%F %T') backup em $DIR/diario/$ARQUIVO ($(du -h "$DIR/diario/$ARQUIVO" | cut -f1))"

if [ "$(date +%u)" = "$DIA_SEMANAL" ]; then
  cp "$DIR/diario/$ARQUIVO" "$DIR/semanal/$ARQUIVO"
fi

# Rotacao: os nomes levam a data, entao a ordem alfabetica e a cronologica.
rotacionar() {
  local pasta=$1 manter=$2
  find "$pasta" -maxdepth 1 -type f -name 'piggu-*.tar*' | sort -r | tail -n +"$((manter + 1))" | xargs -r rm -f --
}
rotacionar "$DIR/diario" "$DIARIOS"
rotacionar "$DIR/semanal" "$SEMANAIS"

if [ -n "$DESTINO" ]; then
  command -v rclone > /dev/null || falhar "rclone nao instalado; a copia fora da VPS nao foi feita."
  rclone copy "$DIR/diario" "$DESTINO/diario" --include 'piggu-*.tar.gpg'
  rclone copy "$DIR/semanal" "$DESTINO/semanal" --include 'piggu-*.tar.gpg'
  # Nada de "rclone sync": se o disco da VPS voltar vazio, sync apagaria a copia de fora.
  rclone delete "$DESTINO/diario" --min-age "$((DIARIOS + 1))d" --include 'piggu-*.tar.gpg'
  rclone delete "$DESTINO/semanal" --min-age "$((SEMANAIS * 7 + 1))d" --include 'piggu-*.tar.gpg'
  echo "$(date '+%F %T') copia enviada para $DESTINO"
fi
