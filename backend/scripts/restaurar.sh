#!/usr/bin/env bash
# Restaura um backup feito pelo backup.sh.
#
#   restaurar.sh ARQUIVO --confirmo [banco ...]
#       Apaga e recria os bancos (todos, ou so os listados) a partir do backup.
#       Pare os servicos antes (docker compose stop identity finance ...), deixando so
#       o postgres no ar, e suba de novo depois. As migrations do Flyway ja estao no dump.
#
#   restaurar.sh ARQUIVO --prefixo PREFIXO [banco ...]
#       Restaura em bancos novos, PREFIXObanco, sem tocar nos de verdade. E o que o
#       testar-restauracao.sh usa.
#
#   restaurar.sh ARQUIVO --confirmo --fotos VOLUME
#       Tambem volta as fotos para o volume do piggu-media (o conteudo atual sai).
#
# Backup cifrado (.tar.gpg) pede PIGGU_BACKUP_SENHA_ARQUIVO.
set -euo pipefail
umask 077

AQUI=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=SCRIPTDIR/comum-backup.sh
source "$AQUI/comum-backup.sh"

[ $# -ge 2 ] || falhar "Uso: restaurar.sh ARQUIVO (--confirmo | --prefixo PREFIXO) [banco ...]"
ARQUIVO=$1
shift
PREFIXO=""
case "$1" in
  --confirmo) shift ;;
  --prefixo)
    [ $# -ge 2 ] || falhar "--prefixo precisa de um valor."
    PREFIXO=$2
    [[ "$PREFIXO" =~ ^[a-z_][a-z0-9_]*$ ]] || falhar "Prefixo invalido: use letras minusculas, numeros e _."
    shift 2 ;;
  *) falhar "Restaurar por cima apaga os bancos atuais: passe --confirmo (ou --prefixo para um teste)." ;;
esac
VOLUME_FOTOS=""
if [ "${1:-}" = "--fotos" ]; then
  [ -z "$PREFIXO" ] || falhar "--fotos so vale com --confirmo."
  [ $# -ge 2 ] || falhar "--fotos precisa do nome do volume."
  VOLUME_FOTOS=$2
  shift 2
fi
ALVOS=${*:-$BANCOS}

CONTAINER=$(container_do_postgres)
PASTA=$(mktemp -d)
trap 'rm -rf "$PASTA"' EXIT
abrir_backup "$ARQUIVO" "$PASTA"

for banco in $ALVOS; do
  [ -f "$PASTA/$banco.dump" ] || falhar "O backup nao tem o banco $banco."
  destino="$PREFIXO$banco"
  echo "$(date '+%F %T') restaurando $banco em $destino"
  docker exec -e PGOPTIONS="-c client_min_messages=warning" "$CONTAINER" dropdb -U "$PG_USER" --if-exists --force "$destino"
  docker exec "$CONTAINER" createdb -U "$PG_USER" "$destino"
  docker exec -i "$CONTAINER" pg_restore -U "$PG_USER" -d "$destino" --no-owner --role="$PG_USER" \
    --exit-on-error < "$PASTA/$banco.dump"
done
if [ -n "$VOLUME_FOTOS" ]; then
  [ -f "$PASTA/fotos.tar" ] || falhar "O backup nao tem fotos."
  echo "$(date '+%F %T') restaurando as fotos em $VOLUME_FOTOS"
  docker run --rm -i -v "$VOLUME_FOTOS:/fotos" --entrypoint sh "$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")" \
    -c 'find /fotos -mindepth 1 -delete && tar -C /fotos -xf -' < "$PASTA/fotos.tar"
fi
echo "$(date '+%F %T') restauracao concluida"
