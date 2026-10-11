#!/usr/bin/env bash
# Atualiza o Piggu no servidor para um commit da branch de producao.
#
# Quem chama e o GitHub Actions (.github/workflows/deploy.yml), por SSH, com uma chave
# que o authorized_keys prende a este script:
#
#   command="/opt/piggu/deploy/atualizar.sh",restrict ssh-ed25519 AAAA... github-actions
#
# Assim, quem tiver a chave so consegue pedir "atualize para o commit X", e X precisa
# estar na branch de producao. Tambem roda a mao:  deploy/atualizar.sh <sha>
#
# Variaveis (opcionais):
#   PIGGU_RAMO   branch de producao (feature/nova-arquitetura)
set -euo pipefail

# Tudo dentro de uma funcao: o bash le a funcao inteira antes de rodar, entao o
# git checkout abaixo pode trocar este arquivo sem atrapalhar a execucao.
main() {
  local ramo=${PIGGU_RAMO:-feature/nova-arquitetura}
  # Pela chave do Actions o sha chega em SSH_ORIGINAL_COMMAND; a mao, no argumento.
  local sha=${SSH_ORIGINAL_COMMAND:-${1:-}}
  if [[ ! $sha =~ ^[0-9a-f]{40}$ ]]; then
    echo "Uso: atualizar.sh <sha do commit, 40 caracteres>" >&2
    exit 2
  fi

  local raiz
  raiz=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
  cd "$raiz"

  # Um deploy por vez; o segundo desiste em vez de brigar pelo docker compose.
  exec 9> /tmp/piggu-deploy.lock
  flock -n 9 || { echo "Outro deploy esta em andamento." >&2; exit 1; }

  echo "==> Buscando $ramo"
  git fetch --quiet origin "$ramo"
  if ! git merge-base --is-ancestor "$sha" "origin/$ramo"; then
    echo "O commit $sha nao esta em $ramo; nada foi feito." >&2
    exit 1
  fi
  # .env, keys/ e o docker-compose.override.yml ficam fora do Git e nao sao tocados.
  git checkout --quiet -B "$ramo" "$sha"

  echo "==> Compilando o site"
  (cd frontend && npm ci --no-audit --no-fund --loglevel=error && npm run build)

  echo "==> Subindo os servicos"
  cd backend
  docker compose --profile producao up -d --build --remove-orphans

  echo "==> Esperando os servicos ficarem saudaveis"
  local estado
  for _ in $(seq 1 60); do
    estado=$(docker compose --profile producao ps --format '{{.Service}} {{.State}} {{.Health}}')
    if ! grep -qE ' (created|restarting|exited|dead)| (starting|unhealthy)$' <<< "$estado"; then
      echo "$estado"
      docker image prune -f > /dev/null
      echo "==> Piggu em $(git rev-parse --short HEAD)"
      return 0
    fi
    sleep 5
  done
  echo "$estado" >&2
  echo "Algum servico nao ficou saudavel em 5 minutos. Veja: docker compose logs --tail 50 <servico>" >&2
  exit 1
}

main "$@"
