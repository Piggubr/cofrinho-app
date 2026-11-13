#!/bin/zsh
# Prints da App Store pela demonstracao (sem dado real): iPhone 6,9" e iPad 13".
# Uso: scripts/prints-das-lojas.sh [pasta]   (padrao: ~/Desktop/prints-das-lojas/ios)
set -euo pipefail
cd "$(dirname "$0")/.."
PASTA=${1:-$HOME/Desktop/prints-das-lojas/ios}
# Em lotes de ate 5 telas: o teste longo demais e encerrado pelo simulador.
LOTES=("painel gastos receitas relatorios" "orcamentos metas calendario familia")
for aparelho in "iPhone 18 Pro Max" "iPad Pro 13-inch (M5)"; do
  # Sistema em portugues: a data da barra de status do iPad sai no idioma do aparelho.
  xcrun simctl boot "$aparelho" 2>/dev/null || true
  xcrun simctl spawn "$aparelho" defaults write -g AppleLanguages -array pt-BR
  xcrun simctl spawn "$aparelho" defaults write -g AppleLocale -string pt_BR
  xcrun simctl shutdown "$aparelho" && xcrun simctl boot "$aparelho"
  xcrun simctl status_bar "$aparelho" override --time 9:41 --batteryState charged \
    --batteryLevel 100 --wifiBars 3 --cellularBars 4 --dataNetwork 5g
  for telas in "${LOTES[@]}"; do
    TEST_RUNNER_PIGGU_PRINTS="$PASTA/${aparelho// /-}" TEST_RUNNER_PIGGU_TELAS="$telas" \
      xcodebuild test -project Piggu.xcodeproj -scheme PigguUITests -derivedDataPath build/dd \
      -destination "platform=iOS Simulator,name=$aparelho" \
      -only-testing:PigguUITests/PrintsUITests -quiet
  done
done
