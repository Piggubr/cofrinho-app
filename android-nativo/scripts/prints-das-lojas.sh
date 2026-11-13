#!/bin/zsh
# Prints do Google Play pela demonstracao (sem dado real), em 1080 x 1920 (9:16).
# Antes: emulador ligado e `./gradlew installDemoDebug -Ppiggu.prints=true` (sem a faixa da
# demonstracao), com o app ja logado (qualquer e-mail e senha entram).
# Uso: scripts/prints-das-lojas.sh [pasta]   (padrao: ~/Desktop/prints-das-lojas/android)
set -euo pipefail
PASTA=${1:-$HOME/Desktop/prints-das-lojas/android}
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
APP=br.com.piggu.app.demo
TELAS=(painel gastos receitas relatorios orcamentos metas calendario plano)
mkdir -p "$PASTA"
$ADB shell wm size 1080x1920
$ADB shell wm density 420
$ADB shell settings put global sysui_demo_allowed 1
demo() { $ADB shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide -e airplane hide
demo status -e vpn hide -e location hide -e alarm hide -e sync hide -e mute hide -e bluetooth hide
demo notifications -e visible false
for tela in $TELAS; do
  $ADB shell am start -W -a android.intent.action.VIEW -d "piggu://$tela" $APP >/dev/null
  sleep 3
  $ADB exec-out screencap -p > "$PASTA/$tela.png"
done
demo exit
$ADB shell wm size reset
$ADB shell wm density reset
