#!/bin/zsh
# Prints da App Store pela demonstracao (sem dado real), nos tamanhos que a App Store Connect pede:
# iPhone com Dynamic Island medio (obrigatorio, 1206 x 2622) e grande (1320 x 2868), iPhone Duo
# por fora (1398 x 2034) e iPad 13" (2064 x 2752). O simulador do Duo nao abre: a tela de dentro
# (2007 x 2853) sai do aparelho.
# As capturas cruas vao para <pasta>/capturas e as montadas (frase e fundo, montar-prints.swift)
# para <pasta>/<aparelho>.
# Uso: scripts/prints-das-lojas.sh [pasta]   (padrao: ~/Desktop/prints-das-lojas/ios)
set -euo pipefail
cd "$(dirname "$0")/.."
PASTA=${1:-$HOME/Desktop/prints-das-lojas/ios}
APARELHOS=${PIGGU_APARELHOS:-"iPhone 18 Pro|iPhone 18 Pro Max|iPhone Duo|iPad Pro 13-inch (M5)"}
# Em lotes de ate 5 telas: o teste longo demais e encerrado pelo simulador.
LOTES=("painel gastos receitas relatorios" "orcamentos metas calendario familia")
for aparelho in "${(@s:|:)APARELHOS}"; do
  # Sistema em portugues: a data da barra de status do iPad sai no idioma do aparelho.
  xcrun simctl boot "$aparelho" 2>/dev/null || true
  xcrun simctl spawn "$aparelho" defaults write -g AppleLanguages -array pt-BR
  xcrun simctl spawn "$aparelho" defaults write -g AppleLocale -string pt_BR
  xcrun simctl shutdown "$aparelho" && xcrun simctl boot "$aparelho"
  xcrun simctl status_bar "$aparelho" override --time 9:41 --batteryState discharging \
    --batteryLevel 100 --wifiBars 3 --cellularBars 4 --dataNetwork 5g
  for telas in "${LOTES[@]}"; do
    # O simulador as vezes derruba o teste no meio: uma segunda tentativa.
    for tentativa in 1 2; do
      TEST_RUNNER_PIGGU_PRINTS="$PASTA/capturas/${aparelho// /-}" TEST_RUNNER_PIGGU_TELAS="$telas" \
        xcodebuild test -project Piggu.xcodeproj -scheme PigguUITests -derivedDataPath build/dd \
        -destination "platform=iOS Simulator,name=$aparelho" \
        -only-testing:PigguUITests/PrintsUITests -quiet && break
      [[ $tentativa == 2 ]] && exit 1
    done
  done
  xcrun simctl shutdown "$aparelho"
done
swift scripts/montar-prints.swift "$PASTA/capturas" "$PASTA"
