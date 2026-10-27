# Piggu iOS nativo (SwiftUI)

Regras que valem para todas as etapas do plano do iOS nativo (D-055). As regras do
[CLAUDE.md da raiz](../CLAUDE.md) continuam valendo.

## Branches e PRs

- Uma branch por etapa: `swift/etapa-N`, criada a partir da `feature/ios-nativo` atualizada, com
  PR para a `feature/ios-nativo` (D-085). Se o PR da etapa anterior ainda não tiver sido mergeado,
  crie a partir da branch dela e diga isso no PR. A `feature/ios-nativo` vai para a `develop` quando
  o conjunto estiver testado.
- Não mexer em `frontend/ios` nem no backend, a não ser que a etapa peça.
- Nunca enviar nada para a App Store ou o TestFlight sem o Dudu pedir na conversa.

## Antes de escrever uma tela

- Ler o componente Angular correspondente em `frontend/src/app/features` e o serviço em
  `frontend/src/app/core/api`, e copiar o contrato da API exatamente (nomes de campos,
  parâmetros, erros). O site é a referência de comportamento.

## Código

- SwiftUI, Observation (`@Observable`), async/await, Swift 6 com concorrência estrita.
- Sem bibliotecas novas além das listadas no [README](README.md#bibliotecas).
- Liquid Glass só em controles que flutuam sobre o conteúdo, nunca em cartões ou listas; sempre
  com fallback para iOS 17 e 18 via `if #available(iOS 26, *)`.
- Layout pelo tamanho disponível (size classes e `containerRelativeFrame`), nunca por idiom nem
  `UIScreen.main`: no iOS 27 o app de iPhone é redimensionável no iPad.
- Textos em pt-BR no String Catalog. Valores em R$ com `pt_BR` e entrada de valor aceitando
  vírgula.
- Toda tela tem estado de carregando, vazio, erro e sem internet, e funciona no modo
  `-PigguDemo` com dados de exemplo.
- Acessibilidade: Dynamic Type, VoiceOver com rótulos em português, contraste nos modos claro e
  escuro.

## Fim de cada etapa

- `xcodebuild build` e `test` passando num simulador de iPhone e num de iPad.
- Prints das telas novas em claro e escuro em `ios-nativo/docs/prints/etapa-N/`.
- README atualizado.
- Commits pequenos e um PR para a `feature/ios-nativo` com o que mudou e os prints.
