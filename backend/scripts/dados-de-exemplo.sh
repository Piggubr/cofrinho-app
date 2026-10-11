#!/usr/bin/env bash
# Dados de exemplo para o backend de desenvolvimento (DB3).
#
# Entre uma vez pelo app com a sua conta Google (isso cria a conta e a familia) e rode:
#
#   ./scripts/dados-de-exemplo.sh voce@gmail.com            # gastos, receitas, cofrinho...
#   ./scripts/dados-de-exemplo.sh --premium voce@gmail.com  # e 30 dias de Premium
#
# Grava tres meses de gastos e receitas, orcamentos, contas fixas, um objetivo com
# depositos, premios e moedas, lugares, filmes e lista de compras na familia dessa
# conta, e liga os modulos de estilo de vida dela. So roda uma vez por familia: se ja
# houver gasto lancado, para sem mexer em nada.
#
# Usa o Postgres do Compose (docker compose exec postgres). Recusa rodar com
# PIGGU_AMBIENTE=producao, no ambiente ou no .env.
set -euo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
EXEMPLOS="$AQUI/../db/exemplo"

falhar() { echo "$*" >&2; exit 1; }

premium=false
if [ "${1:-}" = "--premium" ]; then premium=true; shift; fi
email=${1:-}
[ -n "$email" ] && [ $# -eq 1 ] || falhar "Uso: $0 [--premium] email-da-conta"

ambiente=${PIGGU_AMBIENTE:-}
if [ -z "$ambiente" ] && [ -f "$AQUI/../.env" ]; then
  ambiente=$(sed -n 's/^PIGGU_AMBIENTE=//p' "$AQUI/../.env" | tail -1)
fi
[ "$ambiente" != "producao" ] || falhar "PIGGU_AMBIENTE=producao: dados de exemplo sao so para desenvolvimento."

# PIGGU_PSQL troca o caminho ate o psql (o teste usa um container avulso).
psql_em() {
  local banco=$1; shift
  if [ -n "${PIGGU_PSQL:-}" ]; then
    local comando
    read -ra comando <<< "$PIGGU_PSQL"
    "${comando[@]}" -U piggu -d "$banco" -AtqX -v ON_ERROR_STOP=1 "$@"
  else
    (cd "$AQUI/.." && docker compose exec -T postgres psql -U piggu -d "$banco" -AtqX -v ON_ERROR_STOP=1 "$@")
  fi
}

conta=$(psql_em piggu_identity -v email="$email" \
  <<< "SELECT id || ' ' || household_id FROM users WHERE lower(email) = lower(:'email')")
[ -n "$conta" ] || falhar "Nenhuma conta com o e-mail $email. Entre uma vez pelo app e rode de novo."
read -r pessoa familia <<< "$conta"

ja_tem=$(psql_em piggu_finance -v familia="$familia" \
  <<< "SELECT count(*) FROM expenses WHERE household_id = :'familia'")
[ "$ja_tem" = 0 ] || falhar "Essa familia ja tem gastos lancados; nada foi mudado."

psql_em piggu_finance -v familia="$familia" -v pessoa="$pessoa" < "$EXEMPLOS/financeiro.sql"
psql_em piggu_rewards -v familia="$familia" -v pessoa="$pessoa" < "$EXEMPLOS/premios.sql"
psql_em piggu_lifestyle -v familia="$familia" -v pessoa="$pessoa" < "$EXEMPLOS/estilo-de-vida.sql"
# NULL liga todos os modulos de estilo de vida no menu.
psql_em piggu_identity -v pessoa="$pessoa" <<< "UPDATE users SET lifestyle_modules = NULL WHERE id = :'pessoa'"

if $premium; then
  psql_em piggu_identity -v familia="$familia" <<< \
    "UPDATE households SET premium_since = COALESCE(premium_since, now()),
                           premium_until = GREATEST(COALESCE(premium_until, now()), now()) + interval '30 days'
     WHERE id = :'familia'"
fi

echo "Dados de exemplo gravados na familia de $email$($premium && echo ', com 30 dias de Premium')."
echo "Saia e entre de novo no app para o plano e os modulos aparecerem."
