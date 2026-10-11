#!/usr/bin/env bash
# Teste do 01-criar-bancos.sh (S4), rodado pelo CI. Confere, num Postgres descartavel:
#   - cada servico entra no proprio banco, cria tabela e a extensao pgcrypto;
#   - nenhum servico entra no banco de outro;
#   - numa instalacao antiga, com tabelas do piggu, rodar o script de novo passa as
#     tabelas (e as sequencias) para o usuario do servico, que segue lendo e gravando.
set -euo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
NOME=piggu-teste-usuarios-$$
SERVICOS="identity finance rewards lifestyle media banking"

limpar() { docker rm -f "$NOME" > /dev/null 2>&1 || true; }
trap limpar EXIT

passo() { echo; echo "== $*"; }
falhar() { echo "FALHOU: $*" >&2; exit 1; }
# Conecta pela rede, com senha, como o servico faria. Pelo endereco do container e nao
# por 127.0.0.1: a imagem do Postgres confia em conexoes locais sem pedir senha.
como() {
  local usuario=$1 senha=$2 banco=$3 comando=$4
  docker exec -e PGPASSWORD="$senha" "$NOME" sh -c 'psql -h "$(hostname -i)" "$@"' psql \
    -U "$usuario" -d "$banco" -AtqX -v ON_ERROR_STOP=1 -c "$comando"
}
admin() { docker exec -i "$NOME" psql -U piggu -d "$1" -AtqX -v ON_ERROR_STOP=1 -c "$2"; }

passo "Postgres descartavel com o init do projeto e uma senha por servico"
senhas=()
for servico in $SERVICOS; do senhas+=(-e "DB_PASSWORD_${servico^^}=senha-$servico"); done
docker run -d --name "$NOME" -e POSTGRES_USER=piggu -e POSTGRES_PASSWORD=piggu -e POSTGRES_DB=postgres \
  "${senhas[@]}" -v "$AQUI/../db/init:/docker-entrypoint-initdb.d:ro" postgres:16-alpine > /dev/null
for _ in $(seq 1 90); do
  docker ps -q --filter "name=^$NOME$" | grep -q . || { docker logs "$NOME"; falhar "o Postgres caiu na subida"; }
  if admin postgres "SELECT 1 FROM pg_database WHERE datname = 'piggu_banking'" 2>/dev/null | grep -q 1 \
     && docker exec "$NOME" pg_isready -U piggu -h 127.0.0.1 > /dev/null 2>&1; then
    break
  fi
  sleep 1
done

passo "Cada servico usa o proprio banco, como o Flyway faria"
for servico in $SERVICOS; do
  como "piggu_$servico" "senha-$servico" "piggu_$servico" \
    "CREATE EXTENSION IF NOT EXISTS pgcrypto;
     CREATE TABLE teste (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), n BIGINT GENERATED ALWAYS AS IDENTITY);
     INSERT INTO teste DEFAULT VALUES; DROP TABLE teste;" > /dev/null
done

passo "Nenhum servico entra no banco de outro"
if como piggu_finance senha-finance piggu_identity "SELECT 1" 2> /tmp/erro-$$; then
  falhar "piggu_finance entrou no banco piggu_identity"
fi
grep -q "permission denied for database" /tmp/erro-$$ || { cat /tmp/erro-$$; falhar "erro inesperado"; }
rm -f /tmp/erro-$$
if como piggu_media senha-finance piggu_media "SELECT 1" 2> /dev/null; then
  falhar "a senha de um servico abriu o banco de outro"
fi
[ "$(admin postgres "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'piggu\_%' AND (rolsuper OR rolcreatedb OR rolcreaterole)")" = 0 ] \
  || falhar "usuario de servico com poder de administrador"

passo "Instalacao antiga: tabelas do piggu passam para o servico"
admin piggu_finance "CREATE TABLE gastos (id BIGSERIAL PRIMARY KEY, valor NUMERIC);
                     CREATE TABLE eventos (id BIGINT GENERATED ALWAYS AS IDENTITY, acao TEXT);
                     CREATE TABLE flyway_schema_history (versao TEXT);
                     CREATE SCHEMA legado; CREATE TABLE legado.notas (texto TEXT);
                     CREATE VIEW resumo AS SELECT count(*) AS total FROM gastos;
                     INSERT INTO gastos (valor) VALUES (10), (20);"
if como piggu_finance senha-finance piggu_finance "SELECT count(*) FROM gastos" 2> /dev/null; then
  falhar "sem rodar o script de novo, o servico ja leria a tabela do piggu"
fi
docker exec "$NOME" bash /docker-entrypoint-initdb.d/01-criar-bancos.sh > /dev/null
[ "$(admin piggu_finance "SELECT string_agg(DISTINCT pg_get_userbyid(relowner), ',') FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname IN ('public', 'legado') AND relkind IN ('r', 'S', 'v')")" = piggu_finance ] \
  || falhar "sobrou objeto de outro dono no piggu_finance"
como piggu_finance senha-finance piggu_finance \
  "INSERT INTO gastos (valor) VALUES (30); INSERT INTO eventos (acao) VALUES ('CRIOU');
   INSERT INTO legado.notas VALUES ('ok'); SELECT total FROM resumo;" | grep -qx 3 \
  || falhar "o servico nao grava nas tabelas que eram do piggu"
como piggu_finance senha-finance piggu_finance "SELECT encode(digest('a', 'sha256'), 'hex')" > /dev/null

passo "Trocar a senha e rodar de novo vale na hora"
docker exec -e DB_PASSWORD_REWARDS=senha-nova "$NOME" bash /docker-entrypoint-initdb.d/01-criar-bancos.sh > /dev/null
como piggu_rewards senha-nova piggu_rewards "SELECT 1" > /dev/null
if como piggu_rewards senha-rewards piggu_rewards "SELECT 1" 2> /dev/null; then
  falhar "a senha antiga ainda entra"
fi

passo "O de-para de e-mail para id chega a cada banco, com o servico como dono"
admin piggu_identity "CREATE TABLE users (id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL);
                      INSERT INTO users VALUES (gen_random_uuid(), 'Ana@Piggu.test'), (gen_random_uuid(), 'beto@piggu.test');"
PIGGU_PG_CONTAINER=$NOME "$AQUI/migrar-emails-para-ids.sh" > /dev/null
PIGGU_PG_CONTAINER=$NOME "$AQUI/migrar-emails-para-ids.sh" > /dev/null
for servico in finance lifestyle media banking; do
  como "piggu_$servico" "senha-$servico" "piggu_$servico" "SELECT email FROM de_para_usuarios ORDER BY 1" \
    | tr '\n' ' ' | grep -qx "ana@piggu.test beto@piggu.test " || falhar "de-para errado em piggu_$servico"
  como "piggu_$servico" "senha-$servico" "piggu_$servico" "DROP TABLE de_para_usuarios" > /dev/null
done
como piggu_rewards senha-nova piggu_rewards "DROP TABLE de_para_usuarios" > /dev/null

echo
echo "OK: um usuario por servico, isolado, a atualizacao de uma instalacao antiga e o de-para funcionam."
