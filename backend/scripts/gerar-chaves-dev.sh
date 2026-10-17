#!/bin/bash
# Gera o par de chaves RSA que o piggu-identity usa para assinar tokens no
# ambiente local. As chaves ficam em backend/keys/, fora do Git (.gitignore).
#
# Nunca use estas chaves em producao: la, passe JWT_PRIVATE_KEY e JWT_PUBLIC_KEY.
# Uso: ./scripts/gerar-chaves-dev.sh   (de dentro de backend/)
set -e

pasta="$(dirname "$0")/../keys"
mkdir -p "$pasta"

if [ -f "$pasta/piggu-dev-private.pem" ]; then
  echo "Chaves ja existem em $pasta. Apague-as para gerar outras."
  exit 0
fi

# genpkey ja grava em PKCS#8, o formato que o TokenConfig le.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$pasta/piggu-dev-private.pem"
openssl pkey -in "$pasta/piggu-dev-private.pem" -pubout -out "$pasta/piggu-dev-public.pem"

# O container roda com um usuario sem privilegios, de outro uid: precisa conseguir ler.
chmod 644 "$pasta"/piggu-dev-*.pem

echo "Chaves de desenvolvimento geradas em $pasta"
