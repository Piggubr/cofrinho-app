#!/usr/bin/env bash
# Teste ponta a ponta dos scripts de backup, rodado pelo CI. Sobe um Postgres
# descartavel com o mesmo init do docker compose, grava dados, faz o backup cifrado,
# confere a rotacao, testa a restauracao e restaura por cima depois de "perder" dados.
set -euo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
TMP=$(mktemp -d)
NOME=piggu-teste-backup-$$
export PIGGU_PG_CONTAINER=$NOME
export PIGGU_BACKUP_DIR=$TMP/backups
export PIGGU_BACKUP_SENHA_ARQUIVO=$TMP/senha
export PIGGU_BACKUP_VOLUME_FOTOS=$NOME-fotos
openssl rand -base64 32 > "$PIGGU_BACKUP_SENHA_ARQUIVO"

limpar() {
  docker rm -f "$NOME" > /dev/null 2>&1 || true
  docker volume rm "$PIGGU_BACKUP_VOLUME_FOTOS" > /dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap limpar EXIT

passo() { echo; echo "== $*"; }
sql() { docker exec -i "$NOME" psql -U piggu -d "$1" -AtqX -v ON_ERROR_STOP=1 -c "$2"; }

passo "Postgres descartavel com o init do projeto"
docker run -d --name "$NOME" -e POSTGRES_USER=piggu -e POSTGRES_PASSWORD=piggu -e POSTGRES_DB=postgres \
  -v "$AQUI/../db/init:/docker-entrypoint-initdb.d:ro" postgres:16-alpine > /dev/null
for _ in $(seq 1 90); do
  docker ps -q --filter "name=^$NOME$" | grep -q . || { docker logs "$NOME"; echo "o Postgres caiu na subida"; exit 1; }
  # O init roda com o servidor so no socket; espera o banco do ultimo servico existir.
  if sql postgres "SELECT 1 FROM pg_database WHERE datname = 'piggu_banking'" 2>/dev/null | grep -q 1 \
     && docker exec "$NOME" pg_isready -U piggu -h 127.0.0.1 > /dev/null 2>&1; then
    break
  fi
  sleep 1
done

passo "Dados de exemplo em cada banco"
for banco in piggu_identity piggu_finance piggu_rewards piggu_lifestyle piggu_media piggu_banking; do
  sql "$banco" "CREATE TABLE registros (id serial PRIMARY KEY, texto text NOT NULL);
                INSERT INTO registros (texto) SELECT 'linha ' || g FROM generate_series(1, 25) g;"
done
sql piggu_finance "CREATE SCHEMA legado; CREATE TABLE legado.gastos (valor numeric); INSERT INTO legado.gastos VALUES (10), (20);"

docker run --rm -v "$PIGGU_BACKUP_VOLUME_FOTOS:/fotos" --entrypoint sh postgres:16-alpine \
  -c 'mkdir -p /fotos/familia-1 && echo foto > /fotos/familia-1/praia.jpg'

# curl de mentira: anota cada ping do monitor de fora em vez de chamar a rede.
mkdir -p "$TMP/bin"
cat > "$TMP/bin/curl" << FIM
#!/usr/bin/env bash
echo "\${@: -1}" >> "$TMP/pings"
FIM
chmod +x "$TMP/bin/curl"
pings() { cat "$TMP/pings" 2> /dev/null; rm -f "$TMP/pings"; }

passo "Destino fora da VPS sem senha e recusado (e o monitor fica sabendo)"
if PATH="$TMP/bin:$PATH" PIGGU_BACKUP_AVISO_URL=https://monitor.exemplo/b \
   PIGGU_BACKUP_SENHA_ARQUIVO="" PIGGU_BACKUP_DESTINO=/tmp/qualquer "$AQUI/backup.sh" 2> "$TMP/erro"; then
  echo "o backup sem cifra deveria ter sido recusado"; exit 1
fi
grep -q "exige PIGGU_BACKUP_SENHA_ARQUIVO" "$TMP/erro"
test "$(pings)" = "https://monitor.exemplo/b/start
https://monitor.exemplo/b/1"

passo "Backup cifrado, com rotacao dos diarios"
mkdir -p "$PIGGU_BACKUP_DIR/diario"
for dia in 01 02 03 04 05 06 07 08; do touch "$PIGGU_BACKUP_DIR/diario/piggu-2020-01-$dia.tar.gpg"; done
DESTINO_LOCAL=""
if command -v rclone > /dev/null; then
  DESTINO_LOCAL=$TMP/fora-da-vps
  export PIGGU_BACKUP_DESTINO=$DESTINO_LOCAL
fi
PATH="$TMP/bin:$PATH" PIGGU_BACKUP_AVISO_URL=https://monitor.exemplo/b "$AQUI/backup.sh"
test "$(pings)" = "https://monitor.exemplo/b/start
https://monitor.exemplo/b/0"
HOJE=$(date +%F)
test -f "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg"
test "$(find "$PIGGU_BACKUP_DIR/diario" -name 'piggu-*' | wc -l)" -eq 7
test ! -e "$PIGGU_BACKUP_DIR/diario/piggu-2020-01-01.tar.gpg"
test ! -e "$PIGGU_BACKUP_DIR/diario/piggu-2020-01-02.tar.gpg"
if ! gpg --batch --pinentry-mode loopback --passphrase "senha-errada" --decrypt \
     "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg" > /dev/null 2>&1; then
  echo "cifrado: sem a senha certa nao abre"
else
  echo "o arquivo abriu com a senha errada"; exit 1
fi
if [ -n "$DESTINO_LOCAL" ]; then
  test -f "$DESTINO_LOCAL/diario/piggu-$HOJE.tar.gpg"
  echo "copia fora da VPS feita pelo rclone"
fi

passo "Restauracao testada em bancos a parte"
sql piggu_finance "INSERT INTO registros (texto) VALUES ('depois do backup')"
PATH="$TMP/bin:$PATH" PIGGU_RESTAURACAO_AVISO_URL=https://monitor.exemplo/r "$AQUI/testar-restauracao.sh"
test "$(pings)" = "https://monitor.exemplo/r/start
https://monitor.exemplo/r/0"
test -z "$(sql postgres "SELECT datname FROM pg_database WHERE datname LIKE 'restauro_teste_%'")"

passo "Restauracao por cima exige confirmacao"
if "$AQUI/restaurar.sh" "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg" piggu_finance 2> /dev/null; then
  echo "restaurou sem --confirmo"; exit 1
fi

passo "Perde dados e restaura por cima"
sql piggu_finance "DELETE FROM registros; DROP SCHEMA legado CASCADE;"
"$AQUI/restaurar.sh" "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg" --confirmo piggu_finance
test "$(sql piggu_finance "SELECT count(*) FROM registros")" -eq 25
test "$(sql piggu_finance "SELECT sum(valor) FROM legado.gastos")" -eq 30
test "$(sql piggu_identity "SELECT count(*) FROM registros")" -eq 25
# Volta com o dono certo: o usuario do servico, nao o superusuario que restaurou.
test "$(sql piggu_finance "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE relname = 'registros'")" = piggu_finance
test "$(sql postgres "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'piggu_finance'")" = piggu_finance

passo "Fotos perdidas voltam"
docker run --rm -v "$PIGGU_BACKUP_VOLUME_FOTOS:/fotos" --entrypoint sh postgres:16-alpine -c 'rm -rf /fotos/* && echo nova > /fotos/intrusa.jpg'
"$AQUI/restaurar.sh" "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg" --confirmo --fotos "$PIGGU_BACKUP_VOLUME_FOTOS" piggu_media
test "$(docker run --rm -v "$PIGGU_BACKUP_VOLUME_FOTOS:/fotos:ro" --entrypoint sh postgres:16-alpine -c 'cat /fotos/familia-1/praia.jpg; ls /fotos')" = "foto
familia-1"

passo "Backup adulterado e recusado"
cp "$PIGGU_BACKUP_DIR/diario/piggu-$HOJE.tar.gpg" "$TMP/adulterado.tar.gpg"
printf 'x' | dd of="$TMP/adulterado.tar.gpg" bs=1 seek=200 conv=notrunc 2> /dev/null
if PATH="$TMP/bin:$PATH" PIGGU_RESTAURACAO_AVISO_URL=https://monitor.exemplo/r \
   "$AQUI/testar-restauracao.sh" "$TMP/adulterado.tar.gpg" > /dev/null 2>&1; then
  echo "aceitou um backup adulterado"; exit 1
fi
# Qualquer codigo diferente de 0 conta como falha para o monitor.
pings > "$TMP/ultimos"
test "$(head -n 1 "$TMP/ultimos")" = https://monitor.exemplo/r/start
grep -qE '^https://monitor.exemplo/r/[1-9][0-9]*$' "$TMP/ultimos"

echo
echo "Backup e restauracao: tudo certo."
