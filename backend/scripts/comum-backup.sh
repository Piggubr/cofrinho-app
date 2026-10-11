# shellcheck shell=bash disable=SC2034
# Partes comuns de backup.sh, restaurar.sh e testar-restauracao.sh (nao rode direto).

PG_USER=${PIGGU_PG_USER:-piggu}
BANCOS=${PIGGU_BANCOS:-"piggu_identity piggu_finance piggu_rewards piggu_lifestyle piggu_media piggu_banking"}

# Linhas de cada tabela, "esquema.tabela|linhas", na ordem do nome. O mesmo SQL roda
# na hora do backup e depois da restauracao, entao as duas saidas tem de ser iguais.
SQL_CONTAGEM="SELECT table_schema || '.' || table_name || '|' ||
  (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
    false, true, '')))[1]::text
FROM information_schema.tables
WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY 1;"

falhar() {
  echo "ERRO: $*" >&2
  exit 1
}

container_do_postgres() {
  if [ -n "${PIGGU_PG_CONTAINER:-}" ]; then
    echo "$PIGGU_PG_CONTAINER"
    return
  fi
  local id
  id=$(docker compose -f "$(dirname "${BASH_SOURCE[0]}")/../docker-compose.yml" ps -q postgres 2>/dev/null || true)
  [ -n "$id" ] || falhar "Postgres do docker compose nao esta rodando (ou defina PIGGU_PG_CONTAINER)."
  echo "$id"
}

# Abre o arquivo do backup (cifrado ou nao) numa pasta e confere o SHA256SUMS.
abrir_backup() {
  local arquivo=$1 pasta=$2
  [ -f "$arquivo" ] || falhar "Backup nao encontrado: $arquivo"
  case "$arquivo" in
    *.tar.gpg)
      [ -n "${PIGGU_BACKUP_SENHA_ARQUIVO:-}" ] || falhar "Backup cifrado: defina PIGGU_BACKUP_SENHA_ARQUIVO."
      gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$PIGGU_BACKUP_SENHA_ARQUIVO" \
        --decrypt "$arquivo" | tar -C "$pasta" -xf - ;;
    *.tar) tar -C "$pasta" -xf "$arquivo" ;;
    *) falhar "Formato desconhecido (esperado .tar ou .tar.gpg): $arquivo" ;;
  esac
  (cd "$pasta" && sha256sum --quiet -c SHA256SUMS) || falhar "SHA256SUMS nao confere: o backup esta corrompido."
}
