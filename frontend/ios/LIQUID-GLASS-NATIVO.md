# Abas nativas com Liquid Glass (iOS)

Neste ramo (`claude/ios-liquid-glass-nativo`), o app iOS troca a barra de abas HTML por um
`UITabBar` nativo. O conteúdo continua sendo o mesmo app Angular no WebView. Só a barra de
baixo passa a ser do iOS.

Quando o app é compilado com o **Xcode 26 (SDK do iOS 26)** e roda no iOS 26, o `UITabBar`
ganha sozinho o visual **Liquid Glass**, sem nenhum código específico para isso. No iOS 15 a 18,
ou se o app for compilado com um Xcode mais antigo, aparece a barra translúcida padrão.

O navegador e o computador não mudam nada. Lá o plugin não existe, então o shell continua
usando a barra HTML.

## Como funciona

- `App/AbasNativasPlugin.swift` é um plugin Capacitor local. Ele cria o `UITabBar` preso à
  parte de baixo da tela e tem os métodos `configurar`, `selecionar`, `mostrar` e `esconder`.
  Também emite dois eventos:
  - `abaTocada`, com o `id` da aba tocada;
  - `alturaMudou`, com a altura da barra em pontos.
- `App/ViewControllerDoApp.swift` é a subclasse do `CAPBridgeViewController` que registra o
  plugin em `capacitorDidLoad()`. O `SceneDelegate` e o `Main.storyboard` apontam para ela.
- `src/app/core/nativo/abas-nativas.ts` é o serviço Angular que conversa com o plugin. Ele só
  fica disponível no iOS com o plugin compilado.
- `src/app/layout/shell/shell.ts` faz a ponte entre as abas nativas e o app:
  - quando o serviço está disponível, manda para a barra nativa os mesmos atalhos da barra
    HTML (Painel, Gastos, "+", Relatórios e Mais; para um membro da família, Painel, Família
    e Mais);
  - marca a aba da tela atual a cada navegação;
  - ao tocar numa aba, navega ou abre o "Mais";
  - esconde a barra HTML com a classe `abas-nativas`.
- O conteúdo continua passando por trás do vidro. O CSS só reserva embaixo a altura que o iOS
  informa, na variável `--abas-nativas-altura`.
- Na largura de iPad (900 px ou mais), a barra nativa fica escondida. Nessa largura o menu é a
  coluna lateral, como no computador.

## Como testar

```sh
cd frontend
npm run build:demo
npx cap sync ios
npx cap open ios   # ou abra ios/App/App.xcodeproj no Xcode 26
```

Rode no simulador do iOS 26 para ver o Liquid Glass. Se usar um simulador do iOS 17 ou 18,
deve aparecer a barra translúcida clássica.

## O que não foi verificado

Este ramo foi preparado sem Mac nem Xcode. Os testes e o build do Angular passam, mas nada do
lado nativo foi conferido.

- **O Swift não foi compilado.** Erros de tipo ou de API do Capacitor 8 só aparecem no Xcode.
- **O `project.pbxproj` foi editado à mão** para incluir os dois arquivos Swift novos. Um
  parser de pbxproj abriu o arquivo sem erros, mas o Xcode ainda não o abriu. Se ele reclamar,
  basta remover as duas referências e arrastar os arquivos para o grupo `App` de novo.
- **Altura e posição da barra no iOS 26.** A barra flutuante do Liquid Glass pode ter uma
  altura diferente de 49 pt. O código usa a altura que o próprio `UITabBar` informa, mas o
  espaçamento embaixo do conteúdo precisa ser conferido no aparelho.
- **Tema escuro.** A cor de destaque acompanha o modo claro ou escuro do sistema, igual ao CSS.
- **Folha do "Mais".** Ela abre acima da barra nativa, e o espaço reservado para ela ainda
  precisa ser ajustado no aparelho.
- **`contentInset: 'always'`.** Com essa opção, pode sobrar um pouco de espaço extra embaixo
  do conteúdo.
