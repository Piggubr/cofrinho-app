# Piggu Android nativo (android-nativo/)

App Android do Piggu em Kotlin, com Jetpack Compose e Material 3, substituindo aos poucos o
app Capacitor (D-056, D-057). As regras do repositório (`../CLAUDE.md`) continuam valendo;
estas são as do app Android, para todas as etapas (A0 a A9).

## Branches e PR

- Cada etapa numa branch `android/etapa-N` a partir da `feature/android-nativo`, com PR para a
  `feature/android-nativo` (D-137). Ela vai para a `develop` por PR só quando o Dudu pedir.
- Não mexer em `frontend/android` (o app Capacitor) nem em `ios-nativo`.
- Nunca publicar nada no Google Play sem o Dudu pedir.

## Antes de cada tela

- Ler o componente Angular em `frontend/src/app/features/<tela>` e o serviço em
  `frontend/src/app/core/api`, e copiar o contrato da API exatamente (`docs/API_SPEC.md`).
- Se a tela já existir em `ios-nativo`, ler também, para manter o mesmo comportamento.

## Arquitetura

- ViewModel com um `StateFlow` de um estado imutável e funções de evento. Nada de lógica na
  Composable: ela só desenha o estado e chama os eventos.
- Repositórios offline-first em `core:data`: lê do Room (`core:database`) primeiro, depois
  busca na rede (`core:network`) e grava no Room.
- Material 3 com o tema do Piggu (`PigguTheme`, em `core:designsystem`). Layout pelo
  WindowSizeClass, edge-to-edge e predictive back. Nunca travar a orientação.

## Textos, valores e acessibilidade

- Textos em pt-BR no `strings.xml` do módulo, nunca escritos no código.
- R$ formatado com `Locale("pt", "BR")`; o campo de valor aceita vírgula (D-076).
- `contentDescription` em português (ou `null` quando o texto ao lado já descreve), áreas de
  toque de 48dp no mínimo, e a tela tem que funcionar com a fonte grande do sistema.

## Toda tela

- Tem os estados carregando, vazio, erro e sem internet.
- Funciona com a API falsa de `core:testing`, que alimenta o modo demonstração (variante
  `demo`: `./gradlew installDemoDebug`).

## Fim de cada etapa

1. `./gradlew build` passando (compila todas as variantes, lint e testes unitários).
2. Capturas Roborazzi das telas novas em claro e escuro, celular e tablet, em
   `docs/prints/etapa-N/`: grave com `./gradlew recordRoborazziDemoDebug` e confira com
   `./gradlew verifyRoborazziDemoDebug`.
3. `README.md` atualizado.
4. PR com o que mudou, as capturas e o resultado do CI.
