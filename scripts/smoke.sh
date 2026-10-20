#!/usr/bin/env bash
# Smoke e regressao da stack inteira: sobe o backend do jeito que vai para o servidor
# (backend/docker-compose.yml, imagens do Dockerfile, Postgres novo com as migracoes) e
# roda os testes de frontend/stack/ no navegador, com o front servido pelo ng serve.
#
# Uso (da raiz do repositorio):
#   bash scripts/smoke.sh                  smoke e regressao
#   bash scripts/smoke.sh --grep @smoke    so o smoke (o resto vai para o Playwright)
#   PIGGU_MANTER_STACK=1 bash scripts/smoke.sh   deixa a stack de pe no fim
#
# Usa um projeto do Compose proprio (piggu-smoke), com banco proprio: nao toca nos dados
# do "docker compose up" de desenvolvimento. As portas (8080 e 5432) sao as mesmas, entao
# pare o de desenvolvimento antes. O front sobe em http://localhost:4400.
set -euo pipefail

RAIZ=$(cd "$(dirname "$0")/.." && pwd)
export PIGGU_STACK_PROJETO=${PIGGU_STACK_PROJETO:-piggu-smoke}

# Outro arquivo por cima (no CI nao tem): COMPOSE_FILE com caminhos absolutos, separados por ":".
export COMPOSE_FILE=${COMPOSE_FILE:-$RAIZ/backend/docker-compose.yml:$RAIZ/scripts/smoke.compose.yml}
# Valores de desenvolvimento do .env.example; o front do teste roda na porta 4400.
export CORS_ORIGINS=http://localhost:4400
# O servico "web" (Caddy) so sobe no perfil producao, mas o Compose confere as variaveis
# obrigatorias dele mesmo assim.
export DOMINIO=${DOMINIO:-localhost}
export EMAIL_DO_CERTIFICADO=${EMAIL_DO_CERTIFICADO:-smoke@exemplo.com}

compose() { docker compose -p "$PIGGU_STACK_PROJETO" --env-file "$RAIZ/backend/.env.example" "$@"; }

terminar() {
  local codigo=$?
  if [ "$codigo" -ne 0 ]; then
    echo "== Falhou; ultimas linhas de cada servico"
    compose logs --tail 80 || true
  fi
  if [ "${PIGGU_MANTER_STACK:-}" = 1 ]; then
    echo "Stack de pe (projeto $PIGGU_STACK_PROJETO). Para derrubar: docker compose -p $PIGGU_STACK_PROJETO down -v"
  else
    compose down -v --remove-orphans > /dev/null 2>&1 || true
  fi
  exit "$codigo"
}
trap terminar EXIT

echo "== Chaves de desenvolvimento"
(cd "$RAIZ/backend" && ./scripts/gerar-chaves-dev.sh)

echo "== Subindo Postgres e os servicos (build das imagens)"
compose up -d --build --wait --wait-timeout 300

# Os servicos de dominio nao tem healthcheck no Compose: pronto e quando o gateway chega
# em cada um e ele recusa quem nao tem token (401), em vez de 500/502.
echo "== Esperando cada servico responder pelo gateway"
for rota in /api/auth/me /api/expenses /api/coins /api/places /api/feed; do
  for tentativa in $(seq 1 90); do
    codigo=$(curl -s -o /dev/null -w '%{http_code}' --noproxy '*' "http://localhost:8080$rota" || true)
    [ "$codigo" = 401 ] && break
    if [ "$tentativa" = 90 ]; then
      echo "::error::$rota nao respondeu 401 em 3 minutos (ultimo: $codigo)"
      exit 1
    fi
    sleep 2
  done
done

echo "== Testes (frontend/stack)"
cd "$RAIZ/frontend"
npx playwright test -c playwright.stack.config.ts "$@"
