#!/bin/bash
# Regrava o contrato da API (docs/api/<servico>.yaml) a partir do codigo.
#
# Rode depois de mudar uma rota ou um DTO, e confira no diff que so entrou coisa nova:
# a API nunca remove nem renomeia campo (docs/API_SPEC.md). Precisa de Docker (o teste
# sobe o Postgres do servico).
#
# Uso: ./scripts/regravar-contrato.sh            (de dentro de backend/, todos os servicos)
#      ./scripts/regravar-contrato.sh finance    (so um)
set -e
cd "$(dirname "$0")/.."

modulos=()
if [ -n "$1" ]; then
  modulos=(-pl "piggu-$1" -am)
fi

# -am compila o common e o testing junto; so os testes de contrato rodam.
mvn -B -q "${modulos[@]}" test -Dtest=ContratoTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Djacoco.skip=true -Dpiggu.regravar-contrato=true

git -C .. status --short docs/api
