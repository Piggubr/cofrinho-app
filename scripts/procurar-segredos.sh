#!/usr/bin/env bash
# Procura chave, token e senha esquecidos no codigo e no historico do Git.
# Rode antes de publicar:  bash scripts/procurar-segredos.sh
#
# Com o gitleaks instalado, usa ele (regras completas, config em .gitleaks.toml).
# Sem ele, faz uma busca simples pelos formatos mais comuns. Saida 1 = achou algo.
set -euo pipefail
cd "$(dirname "$0")/.."

if command -v gitleaks >/dev/null 2>&1; then
  exec gitleaks git --config .gitleaks.toml --redact .
fi

echo "gitleaks nao encontrado; fazendo a busca simples."
PADROES='AIza[0-9A-Za-z_-]{35}|sk_(live|test)_[0-9A-Za-z]{10,}|rk_(live|test)_|whsec_[0-9A-Za-z]{10,}|-----BEGIN ([A-Z]+ )?PRIVATE KEY-----|ghp_[0-9A-Za-z]{36}|xox[baprs]-|AKIA[0-9A-Z]{16}|GOCSPX-|ya29[.]'
LIBERADOS='src/test/resources/keys/|src/main/resources/keys/piggu-dev-private.pem'

achou=0
if git grep --untracked -nIE "$PADROES" -- . ':!package-lock.json' ':!scripts/procurar-segredos.sh' | grep -vE "$LIBERADOS"; then
  achou=1
fi
# No historico: arquivo e commit de cada ocorrencia, sem mostrar o segredo.
if git log --all -p --no-color --format='commit %h' | awk -v p="$PADROES" '
    /^commit / { c = $2 } /^--- a\// { f = substr($0, 7) } /^\+\+\+ b\// { f = substr($0, 7) }
    $0 ~ p && f !~ /src\/test\/resources\/keys\/|piggu-dev-private[.]pem|procurar-segredos[.]sh|[.]gitleaks[.]toml/ { print c " " f; achou = 1 }
    END { exit achou ? 0 : 1 }'; then
  achou=1
fi

if [ "$achou" = 1 ]; then
  echo "ATENCAO: possivel segredo encontrado (acima). Troque a chave antes de publicar."
  exit 1
fi
echo "Nenhum segredo encontrado."
