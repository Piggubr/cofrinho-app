#!/usr/bin/env bash
# Prova que o backup volta: restaura em bancos de teste (sem tocar nos de verdade),
# confere que cada tabela tem as mesmas linhas que tinha na hora do backup e apaga os
# bancos de teste. Rode toda semana, depois do backup:
#
#   45 3 * * 0  /opt/piggu/backend/scripts/testar-restauracao.sh >> /var/log/piggu-backup.log 2>&1
#
#   testar-restauracao.sh [ARQUIVO]   (padrao: o diario mais recente em PIGGU_BACKUP_DIR)
#
# Saida 0 = backup bom. Qualquer outra = investigue antes de precisar dele.
set -euo pipefail
umask 077

AQUI=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=SCRIPTDIR/comum-backup.sh
source "$AQUI/comum-backup.sh"

DIR=${PIGGU_BACKUP_DIR:-/var/backups/piggu}
ARQUIVO=${1:-$(find "$DIR/diario" -maxdepth 1 -type f -name 'piggu-*.tar*' 2>/dev/null | sort | tail -n 1)}
[ -n "$ARQUIVO" ] || falhar "Nenhum backup em $DIR/diario."
PREFIXO=restauro_teste_

CONTAINER=$(container_do_postgres)
PASTA=$(mktemp -d)
limpar() {
  for banco in $BANCOS; do
    docker exec -e PGOPTIONS="-c client_min_messages=warning" "$CONTAINER" dropdb -U "$PG_USER" --if-exists --force "$PREFIXO$banco" || true
  done
  rm -rf "$PASTA"
}
trap limpar EXIT

abrir_backup "$ARQUIVO" "$PASTA"
"$AQUI/restaurar.sh" "$ARQUIVO" --prefixo "$PREFIXO" > /dev/null

falhas=0
for banco in $BANCOS; do
  docker exec -i "$CONTAINER" psql -U "$PG_USER" -d "$PREFIXO$banco" -AtqX -v ON_ERROR_STOP=1 \
    -c "$SQL_CONTAGEM" > "$PASTA/$banco.restaurado"
  if diff -u "$PASTA/$banco.contagem" "$PASTA/$banco.restaurado"; then
    echo "OK    $banco: $(wc -l < "$PASTA/$banco.contagem") tabelas, $(awk -F'|' '{s += $2} END {print s + 0}' "$PASTA/$banco.contagem") linhas"
  else
    echo "FALHA $banco: as linhas restauradas nao batem com as do backup (diff acima)"
    falhas=$((falhas + 1))
  fi
done

if [ -f "$PASTA/fotos.tar" ]; then
  if tar -tf "$PASTA/fotos.tar" > /dev/null; then
    echo "OK    fotos: $(tar -tf "$PASTA/fotos.tar" | grep -vc '/$') arquivos"
  else
    echo "FALHA fotos: o fotos.tar nao abre"
    falhas=$((falhas + 1))
  fi
fi

if [ "$falhas" -gt 0 ]; then
  falhar "$falhas banco(s) nao voltaram iguais de $(basename "$ARQUIVO")."
fi
echo "$(date '+%F %T') restauracao de $(basename "$ARQUIVO") testada: tudo confere"
