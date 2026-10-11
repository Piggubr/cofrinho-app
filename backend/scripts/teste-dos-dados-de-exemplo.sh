#!/usr/bin/env bash
# Teste do dados-de-exemplo.sh (DB3), rodado pelo CI. Num Postgres descartavel com as
# migracoes de cada servico, confere que os exemplos entram na familia certa, respeitam
# as restricoes do banco, nao rodam duas vezes e recusam o ambiente de producao.
set -euo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
NOME=piggu-teste-exemplos-$$

limpar() { docker rm -f "$NOME" > /dev/null 2>&1 || true; }
trap limpar EXIT

passo() { echo; echo "== $*"; }
falhar() { echo "FALHOU: $*" >&2; exit 1; }
sql() { docker exec -i "$NOME" psql -U piggu -d "$1" -AtqX -v ON_ERROR_STOP=1 -c "$2"; }

passo "Postgres descartavel com as migracoes dos servicos"
docker run -d --name "$NOME" -e POSTGRES_USER=piggu -e POSTGRES_PASSWORD=piggu -e POSTGRES_DB=postgres \
  -v "$AQUI/../db/init:/docker-entrypoint-initdb.d:ro" postgres:16-alpine > /dev/null
for _ in $(seq 1 90); do
  if sql postgres "SELECT 1 FROM pg_database WHERE datname = 'piggu_banking'" 2>/dev/null | grep -q 1 \
     && docker exec "$NOME" pg_isready -U piggu -h 127.0.0.1 > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
for servico in identity finance rewards lifestyle; do
  pasta="$AQUI/../piggu-$servico/src/main/resources/db/migration"
  for arquivo in $(find "$pasta" -name "V*.sql" -printf "%f\n" | sort -V); do
    docker exec -i "$NOME" psql -U piggu -d "piggu_$servico" -qX -v ON_ERROR_STOP=1 < "$pasta/$arquivo" > /dev/null 2>&1 \
      || falhar "migracao $servico/$arquivo"
  done
done

passo "Duas familias; os exemplos vao so para a da conta pedida"
for i in 1 2; do
  sql piggu_identity "INSERT INTO households (id) VALUES ('f0000000-0000-0000-0000-00000000000$i');
    INSERT INTO users (id, email, role, household_id, lifestyle_modules)
    VALUES ('10000000-0000-0000-0000-00000000000$i', 'pessoa$i@exemplo.test', 'TITULAR',
            'f0000000-0000-0000-0000-00000000000$i', '')" > /dev/null
done
export PIGGU_PSQL="docker exec -i $NOME psql"
"$AQUI/dados-de-exemplo.sh" --premium Pessoa1@Exemplo.test

F1="household_id = 'f0000000-0000-0000-0000-000000000001'"
F2="household_id = 'f0000000-0000-0000-0000-000000000002'"
[ "$(sql piggu_finance "SELECT count(*) >= 24 FROM expenses WHERE $F1")" = t ] || falhar "faltam gastos"
[ "$(sql piggu_finance "SELECT count(*) FROM expenses WHERE expense_date > current_date")" = 0 ] \
  || falhar "gasto com data no futuro"
[ "$(sql piggu_finance "SELECT count(*) FROM incomes WHERE $F1")" = 4 ] || falhar "receitas"
[ "$(sql piggu_finance "SELECT count(*) FROM piggy_deposits WHERE $F1 AND goal_id IS NOT NULL")" = 3 ] \
  || falhar "depositos do objetivo"
[ "$(sql piggu_rewards "SELECT sum(amount) FROM coin_ledger WHERE $F1")" = 55 ] || falhar "saldo de moedas"
[ "$(sql piggu_lifestyle "SELECT count(*) FROM shopping_items WHERE $F1")" = 4 ] || falhar "lista de compras"
for banco_tabela in piggu_finance:expenses piggu_rewards:prizes piggu_lifestyle:places; do
  [ "$(sql "${banco_tabela%%:*}" "SELECT count(*) FROM ${banco_tabela#*:} WHERE $F2")" = 0 ] \
    || falhar "exemplo vazou para a outra familia em ${banco_tabela#*:}"
done
[ "$(sql piggu_identity "SELECT premium_until > now() + interval '29 days' FROM households WHERE id = 'f0000000-0000-0000-0000-000000000001'")" = t ] \
  || falhar "Premium nao foi ligado"
[ "$(sql piggu_identity "SELECT premium_until IS NULL FROM households WHERE id = 'f0000000-0000-0000-0000-000000000002'")" = t ] \
  || falhar "Premium foi ligado na outra familia"
[ "$(sql piggu_identity "SELECT lifestyle_modules IS NULL FROM users WHERE email = 'pessoa1@exemplo.test'")" = t ] \
  || falhar "modulos de estilo de vida nao foram ligados"

passo "Segunda vez na mesma familia para sem mexer em nada"
antes=$(sql piggu_finance "SELECT count(*) FROM expenses")
if "$AQUI/dados-de-exemplo.sh" pessoa1@exemplo.test 2> /tmp/erro-$$; then falhar "rodou duas vezes"; fi
grep -q "ja tem gastos" /tmp/erro-$$ || { cat /tmp/erro-$$; falhar "mensagem inesperada"; }
[ "$(sql piggu_finance "SELECT count(*) FROM expenses")" = "$antes" ] || falhar "gravou na segunda vez"

passo "Conta que nao existe e producao sao recusadas"
if "$AQUI/dados-de-exemplo.sh" ninguem@exemplo.test 2> /tmp/erro-$$; then falhar "aceitou conta inexistente"; fi
grep -q "Nenhuma conta" /tmp/erro-$$ || { cat /tmp/erro-$$; falhar "mensagem inesperada"; }
if PIGGU_AMBIENTE=producao "$AQUI/dados-de-exemplo.sh" pessoa2@exemplo.test 2> /tmp/erro-$$; then
  falhar "rodou em producao"
fi
grep -q "producao" /tmp/erro-$$ || { cat /tmp/erro-$$; falhar "mensagem inesperada"; }
rm -f /tmp/erro-$$
[ "$(sql piggu_finance "SELECT count(*) FROM expenses WHERE $F2")" = 0 ] || falhar "producao gravou"

echo; echo "Dados de exemplo: tudo certo."
