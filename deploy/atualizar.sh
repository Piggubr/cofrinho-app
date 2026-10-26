#!/usr/bin/env bash
# Atualiza o Piggu no servidor para um commit da branch de producao.
#
# Quem chama e o GitHub Actions (job "publicar" do .github/workflows/ci.yml), por SSH, com uma chave
# que o authorized_keys prende a este script:
#
#   command="/opt/piggu/deploy/atualizar.sh",restrict ssh-ed25519 AAAA... github-actions
#
# Assim, quem tiver a chave so consegue pedir "atualize para o commit X", e X precisa
# estar na branch de producao. Tambem roda a mao:  deploy/atualizar.sh <sha>
#
# Imagens: o CI ja compilou, testou e publicou as imagens do commit no GitHub (ghcr.io, job
# "imagens-da-main"); aqui elas so sao baixadas. O Actions manda pela entrada um token que
# so vale ate o job acabar e, na linha seguinte, o digest de cada imagem que publicou: so
# esses sao baixados. A tag do registro nunca: qualquer token com escrita no pacote pode
# reescreve-la depois do CI. Sem digests (rollback, ou a mao) valem as imagens do commit que
# ja estao aqui; sem elas, ou com imagem de outro processador, o script compila aqui mesmo.
#
# Rollback: se a versao nova nao fica saudavel em 5 minutos, o script volta sozinho para o
# commit que estava no ar e termina com erro (o job fica vermelho). As migracoes de banco
# nao voltam: o Flyway da versao anterior ignora as migracoes novas e sobe, desde que a
# versao nova so tenha acrescentado coisas ao banco (docs/implantacao-producao.md, secao 8).
#
# Variaveis (opcionais):
#   PIGGU_RAMO      branch de producao (main)
#   PIGGU_REGISTRO  onde ficam as imagens (ghcr.io/piggubr/cofrinho-app)
#
# Com PIGGU_OBSERVABILIDADE=true no backend/.env, sobe tambem o monitoramento (perfil
# observabilidade: Grafana, Prometheus, Loki) e o reinicia quando a configuracao dele muda.
# Um problema no monitoramento nunca reprova o deploy: so os servicos do app contam.
set -euo pipefail

RAMO=${PIGGU_RAMO:-main}
REGISTRO=${PIGGU_REGISTRO:-ghcr.io/piggubr/cofrinho-app}
# Os servicos com imagem propria (build no docker-compose.yml) no perfil producao.
SERVICOS_DO_APP=(identity finance rewards lifestyle media gateway web)
MONITORAMENTO='prometheus|loki|alloy|node-exporter|grafana'

# Tudo dentro de funcoes: o bash le cada funcao inteira antes de rodar, entao o git
# checkout abaixo pode trocar este arquivo sem atrapalhar a execucao.
main() {
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

  # O token do registro chega pela entrada, numa linha (so o Actions manda). A mao, no
  # terminal, nao ha token: segue sem ele.
  local token="" digests=""
  if [[ ! -t 0 ]]; then
    IFS= read -r -t 10 token || true
    IFS= read -r -t 10 digests || true
  fi
  if [[ -n $digests ]] && ! digests_validos "$digests"; then
    echo "Os digests das imagens nao vieram certos; nada foi feito." >&2
    exit 2
  fi

  echo "==> Buscando $RAMO"
  git fetch --quiet origin "$RAMO"
  if ! git merge-base --is-ancestor "$sha" "origin/$RAMO"; then
    echo "O commit $sha nao esta em $RAMO; nada foi feito." >&2
    exit 1
  fi
  # .env, keys/ e o docker-compose.override.yml ficam fora do Git e nao sao tocados.
  local antes
  antes=$(git rev-parse HEAD)

  if publicar "$sha" "$token" "$digests"; then
    limpar_imagens "$sha" "$antes"
    echo "==> Piggu em ${sha:0:7}"
    return 0
  fi

  if [[ $antes == "$sha" ]]; then
    echo "O mesmo commit ja estava no ar; nao ha versao anterior para voltar." >&2
    exit 1
  fi
  echo "==> ${sha:0:7} nao ficou saudavel; voltando para ${antes:0:7}" >&2
  # Sem digests: a versao anterior volta com as imagens que ja estao aqui.
  if publicar "$antes" "$token" ""; then
    echo "==> Rollback feito: o servidor continua em ${antes:0:7}. O deploy de ${sha:0:7} falhou." >&2
  else
    echo "==> Nem ${antes:0:7} ficou saudavel. Veja: docker compose logs --tail 50 <servico>" >&2
  fi
  exit 1
}

# Poe o servidor no commit $1 e espera os servicos do app ficarem saudaveis.
# Chamada dentro de um if, onde o set -e nao vale: cada passo confere o proprio erro.
publicar() {
  local alvo=$1 token=$2 digests=$3 de
  de=$(git rev-parse HEAD)
  git checkout --quiet -B "$RAMO" "$alvo" || return 1

  local perfis=(--profile producao) monitoramento=false
  if grep -qE '^PIGGU_OBSERVABILIDADE=(true|sim|1)[[:space:]]*$' backend/.env 2> /dev/null; then
    perfis+=(--profile observabilidade)
    monitoramento=true
  fi
  # O docker-compose.yml monta o nome de cada imagem com estas duas.
  export PIGGU_IMAGENS=$REGISTRO PIGGU_VERSAO=$alvo

  echo "==> Imagens de ${alvo:0:7}"
  if ! imagens_de "$token" "$digests"; then
    echo "==> Sem as imagens conferidas; compilando aqui"
    (cd frontend && npm ci --no-audit --no-fund --loglevel=error && npm run build) || return 1
    (cd backend && docker compose "${perfis[@]}" build "${SERVICOS_DO_APP[@]}") || return 1
  fi

  echo "==> Subindo os servicos"
  (cd backend && docker compose "${perfis[@]}" up -d --no-build --remove-orphans) || return 1
  # Config do monitoramento e arquivo montado: o container nao percebe a troca sozinho.
  if $monitoramento && ! git diff --quiet "$de" "$alvo" -- backend/observabilidade; then
    echo "==> Reiniciando o monitoramento (configuracao mudou)"
    (cd backend && docker compose "${perfis[@]}" restart prometheus loki alloy grafana) || true
  fi

  esperar_saude
}

# "servico@sha256:<64 hex>" para cada servico do app, separados por espaco.
digests_validos() {
  local item servico
  for item in $1; do
    [[ $item =~ ^[a-z-]+@sha256:[0-9a-f]{64}$ ]] || return 1
  done
  for servico in "${SERVICOS_DO_APP[@]}"; do
    [[ " $1 " == *" $servico@sha256:"* ]] || return 1
  done
}

# Com digests, baixa exatamente essas imagens; sem eles, usa as do commit ja guardadas aqui.
imagens_de() {
  local token=$1 digests=$2 servico
  if [[ -n $digests ]]; then
    baixar_imagens "$token" "$digests"
    return
  fi
  for servico in "${SERVICOS_DO_APP[@]}"; do
    docker image inspect "$PIGGU_IMAGENS/$servico:$PIGGU_VERSAO" > /dev/null 2>&1 || return 1
  done
  imagens_da_plataforma_daqui
}

# Baixa cada imagem pelo digest e da a ela a tag que o docker-compose.yml usa.
baixar_imagens() {
  local token=$1 digests=$2 resultado=0 item
  if [[ -n $token ]]; then
    printf '%s' "$token" | docker login ghcr.io -u piggu-deploy --password-stdin > /dev/null 2>&1 || true
  fi
  for item in $digests; do
    if ! docker pull --quiet "$REGISTRO/$item" > /dev/null \
      || ! docker tag "$REGISTRO/$item" "$PIGGU_IMAGENS/${item%@*}:$PIGGU_VERSAO"; then
      resultado=1
      break
    fi
  done
  # O token morre com o job, mas nao fica gravado no servidor nem ate la.
  if [[ -n $token ]]; then
    docker logout ghcr.io > /dev/null 2>&1 || true
  fi
  if ((resultado == 0)) && ! imagens_da_plataforma_daqui; then
    resultado=1
  fi
  return $resultado
}

# Imagem de outro processador (Intel num servidor ARM) baixa, mas nao roda: conta como
# imagem que nao existe, e o script compila aqui.
imagens_da_plataforma_daqui() {
  local daqui servico imagem dela
  daqui=$(docker version --format '{{.Server.Arch}}') || return 1
  for servico in "${SERVICOS_DO_APP[@]}"; do
    imagem="$PIGGU_IMAGENS/$servico:$PIGGU_VERSAO"
    dela=$(docker image inspect --format '{{.Architecture}}' "$imagem") || return 1
    if [[ $dela != "$daqui" ]]; then
      echo "==> A imagem $imagem e $dela, e este servidor e $daqui" >&2
      return 1
    fi
  done
}

esperar_saude() {
  echo "==> Esperando os servicos ficarem saudaveis"
  local estado
  for _ in $(seq 1 60); do
    # So os servicos do app; o monitoramento (perfil observabilidade) fica de fora.
    estado=$(cd backend && docker compose --profile producao ps --format '{{.Service}} {{.State}} {{.Health}}') || true
    estado=$(grep -vE "^($MONITORAMENTO) " <<< "$estado" || true)
    if [[ -n $estado ]] && ! grep -qE ' (created|restarting|exited|dead)| (starting|unhealthy)$' <<< "$estado"; then
      echo "$estado"
      return 0
    fi
    sleep 5
  done
  echo "$estado" >&2
  echo "Algum servico nao ficou saudavel em 5 minutos. Veja: docker compose logs --tail 50 <servico>" >&2
  return 1
}

# Guarda so as imagens da versao no ar e da anterior (a do rollback); o resto ocupa disco.
limpar_imagens() {
  local manter="^$REGISTRO/[a-z-]+:($1|$2)$"
  docker images --filter "reference=$REGISTRO/*" --format '{{.Repository}}:{{.Tag}}' \
    | grep -vE "$manter" | xargs -r docker rmi > /dev/null 2>&1 || true
  docker image prune -f > /dev/null
}

# Na mesma linha: depois do main o bash nao le mais nada deste arquivo, que o checkout trocou.
main "$@"; exit
