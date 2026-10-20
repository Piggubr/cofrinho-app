#!/usr/bin/env bash
# Regressao (18/10/2026): duas mensagens chegaram ao usuario como "SÃ³ quem lanÃ§ou" e
# "O Piggu Ã© para quem", texto UTF-8 gravado de novo como se fosse Latin-1. Este script
# falha se esse padrao (Ã ou Â seguido de um byte de continuacao) voltar ao codigo.
#
# Uso: bash scripts/conferir-acentos.sh   (da raiz do repositorio)
set -euo pipefail

cd "$(dirname "$0")/.."

# Le e escreve UTF-8 mesmo em maquina com outro idioma configurado.
export LC_ALL=C.UTF-8

if grep -rnIP '[ÃÂ][\x{80}-\x{BF}]' \
     --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist \
     backend/*/src frontend/src frontend/e2e frontend/stack docs deploy README.md; then
  echo "::error::Acento quebrado (UTF-8 lido como Latin-1) nas linhas acima. Reescreva o texto com o acento certo."
  exit 1
fi
echo "Nenhum acento quebrado."
