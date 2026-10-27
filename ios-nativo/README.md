# Piggu para iOS (nativo, SwiftUI)

Versão nativa do Piggu, em Swift e SwiftUI, ao lado do app Capacitor (`frontend/ios`), que
continua como está. iOS 17 ou mais novo, iPhone e iPad, Bundle ID `br.com.piggu.app`.

## Como abrir

```bash
open ios-nativo/Piggu.xcodeproj
```

No Xcode: alvo **Piggu** → *Signing & Capabilities* → escolha o seu *Team* (a assinatura é
automática e o time está em branco no projeto). Depois é só escolher um simulador ou o seu
iPhone e apertar ⌘R.

Se o `xcodebuild` reclamar que está nas Command Line Tools, aponte para o Xcode:

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

## Como rodar pela linha de comando

```bash
cd ios-nativo
xcodebuild -project Piggu.xcodeproj -scheme Piggu -destination 'platform=iOS Simulator,name=iPhone 17' build
xcodebuild -project Piggu.xcodeproj -scheme Piggu -destination 'platform=iOS Simulator,name=iPhone 17' test
# Auditoria de acessibilidade e desempenho (UI tests, mais lentos; fora do CI)
xcodebuild -project Piggu.xcodeproj -scheme PigguUITests -destination 'platform=iOS Simulator,name=iPhone 17' test
```

O esquema **PigguUITests** tem quatro testes:

- `AcessibilidadeUITests`: abre cada tela logada pela demonstração e roda a auditoria do
  sistema (a mesma do Accessibility Inspector: contraste, Dynamic Type, texto cortado, área de
  toque, rótulos) no texto padrão, no maior tamanho e no escuro. O relatório sai no log, entre
  `AUDITORIA` e `FIM DA AUDITORIA` e anexado ao resultado. É um relatório, não uma trava: a
  auditoria também aponta texto sob a barra de abas de vidro, o link "Legal" do mapa, valor em R$
  como rótulo ilegível e botão desligado.
- `PrintsUITests`: só roda com uma pasta, e grava o print de cada tela nela:
  `TEST_RUNNER_PIGGU_PRINTS=/tmp/prints TEST_RUNNER_PIGGU_TAMANHO=UICTContentSizeCategoryAccessibilityXXXL
  TEST_RUNNER_PIGGU_ESCURO=1 TEST_RUNNER_PIGGU_PAISAGEM=1 TEST_RUNNER_PIGGU_TELAS="painel gastos"
  xcodebuild ... -only-testing:PigguUITests/PrintsUITests test` (todos opcionais, menos a pasta).
  Em lotes de até 5 telas: o teste longo demais é encerrado pelo simulador.
- `TopoETecladoUITests`: tocar fora fecha o teclado do lançar gasto, a busca do topo abre um
  gasto e o assistente (na demonstração, com resposta de exemplo) pede a autorização, responde e
  abre a tela sugerida.
- `DesempenhoUITests`: mede "Abrir ate o Painel" (do `init` do app ao Painel com dados, um
  `os_signpost` nos Points of Interest) com o cache cheio, com e sem 3.000 gastos, e a rolagem
  dos Gastos e do Painel. A meta é menos de 1 s; no Instruments, o intervalo aparece na trilha
  Points of Interest do Time Profiler ou do App Launch.

### Argumentos de lançamento (Debug)

No Xcode, em *Product → Scheme → Edit Scheme → Run → Arguments* (já cadastrados, desligados):

| Argumento | O que faz |
| --- | --- |
| `-PigguAPIURL http://localhost:8080/api` | Troca a API (o padrão é `https://piggu.com.br/api`, do build setting `PIGGU_API_URL`). |
| `-PigguDemo YES` | Dados inventados, sem rede. Qualquer e-mail e senha entram. |
| `-PigguDemoLogado YES` | Com a demonstração, já abre logado. |
| `-PigguDemoFalha rede` / `servidor` / `vazio` | Com a demonstração: sem internet, erro 500 ou mês sem gastos, para ver os estados das telas. |
| `-PigguDemoMuitos 3000` | Com a demonstração: o mês com milhares de gastos, para medir a lista e o Painel. |
| `-PigguDemoCache YES` | Com a demonstração: guarda o cache num arquivo próprio (`Demonstracao.store`), para medir a abertura com cache. |
| `-PigguAba gastos` / `receitas` / `relatorios` / `mais` | Abre direto numa aba. |
| `-PigguLancar YES` | Abre direto o lançamento de gasto. |
| `-piggu_tema azul` | Abre com outra cor do Piggu (`chiclete`, `rosa`, `menta`, `azul`, `lilas`). |
| `-PigguDicas YES` | Com a demonstração: mostra as dicas do TipKit (escondidas na demonstração para os prints). |
| `-piggu_trava_biometrica YES` | Abre com a trava do Face ID ligada. |
| `-PigguRefreshNoCorpo YES` | Refresh no corpo e no Keychain em vez do cookie (veja *Sessão*). |
| `-PigguDemoConta termos` / `sobre-voce` / `cpf` / `email` / `premium` / `membro` | Com a demonstração: a conta abre com os Termos mudados, sem apelido e nascimento, sem CPF, com o e-mail sem confirmar, no Premium ou como membro da família. |
| `-PigguEntrada cadastro` / `conta-nova` | Abre a entrada na aba Criar conta ou no aceite de conta nova pela Apple ou pelo Google. |
| `-PigguLink piggu://termos` | Abre um link depois de restaurar a sessão (`termos`, `privacidade`, `esqueci-senha`, `cpf`, `confirmar-email?token=...`, `redefinir-senha?token=...`). |

A demonstração e esses atalhos só existem em Debug; não vão para o build de Release (o
`-PigguRefreshNoCorpo` vale nos dois).

### Links

`piggu://gasto?valor=12.50` abre o lançamento com o valor preenchido. Os links do app Capacitor
continuam valendo (`piggu://gastos?rapido=1&valor=12.50`, do atalho do ícone e da Siri), e as
rotas do site abrem a tela certa: `piggu://painel`, `piggu://gastos`, `piggu://receitas`,
`piggu://relatorios`, `piggu://perfil`, e no Mais `piggu://planejar`, `metas`, `orcamentos`, `contas-fixas`,
`contas-e-cartoes`, `calendario`, `compras`, `lugares`, `filmes`, `feed`, `premios`, `girls-math`,
`familia` e `perfil`. `piggu://gastos?id=...` abre um gasto (é o que a busca do iPhone usa). Para testar no simulador:

```bash
xcrun simctl openurl booted 'piggu://gasto?valor=12.50'
```

Os links dos e-mails do backend também abrem no app, por cima de qualquer tela e com ou sem
sessão: `https://piggu.com.br/confirmar-email?token=...` e
`https://piggu.com.br/redefinir-senha?token=...` (e os mesmos com `piggu://`).
`piggu://termos`, `piggu://privacidade`, `piggu://esqueci-senha` e (com sessão) `piggu://cpf`
abrem as telas do mesmo nome do site. Na demonstração, o token `vencido`
responde como link vencido.

### Universal Links (links do e-mail)

O app tem o *Associated Domains* `applinks:piggu.com.br`, e o site publica
`https://piggu.com.br/.well-known/apple-app-site-association` (`frontend/public/.well-known/`,
servido como JSON pelo `deploy/Caddyfile`) com o App ID `H766LA27ZJ.br.com.piggu.app`. Os links
de `/confirmar-email` e `/redefinir-senha` abrem o app; sem o app, abrem o site, que tem as
mesmas telas. A Apple lê o arquivo pela CDN dela, então o app só passa a abrir os links depois
que ele estiver no ar em produção.

## Bibliotecas

Só os SDKs da Apple (SwiftUI, Observation, Foundation, SwiftData, Network, Security,
AuthenticationServices, CryptoKit, Charts, PhotosUI, UIKit para a câmera, MapKit, WidgetKit, AppIntents,
LocalAuthentication, UserNotifications, TipKit, VisionKit, CoreSpotlight, Swift Testing) e o Google Sign-In (D-055), pelo Swift Package
Manager: `GoogleSignIn-iOS` 10 (`https://github.com/google/GoogleSignIn-iOS`), que traz AppAuth,
GTMAppAuth, GTMSessionFetcher, AppCheckCore e GoogleUtilities. As versões ficam presas em
`Piggu.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`. Nada além disso sem
uma decisão nova.

A fonte Sora (`Piggu/Recursos/Sora.woff2`) é o mesmo arquivo do pacote
`@fontsource-variable/sora` do site, sob a licença SIL Open Font License 1.1
(`Piggu/Recursos/Sora-OFL.txt`).

## Entrar com Apple

O alvo tem a capacidade *Sign in with Apple* (`Piggu/Piggu.entitlements`). O app manda o
identity token, o nonce e, na primeira vez, o nome para `POST /api/auth/apple` (D-072). No
servidor, `APPLE_CLIENT_IDS=br.com.piggu.app`; sem isso a rota responde 501 e o app mostra
"O login com a Apple ainda não está disponível". O projeto assina com o Team `H766LA27ZJ`; a
assinatura automática registra a capacidade no App ID.

## Entrar com Google

Os IDs OAuth ficam em `Config/Piggu.xcconfig`: são públicos e vão dentro do app. O do iOS
(tipo **iOS** no [Google Cloud Console](https://console.cloud.google.com/apis/credentials), no
mesmo projeto do site, ID do pacote `br.com.piggu.app`) abre o login e dá o esquema de URL de
volta (`GOOGLE_IOS_URL_SCHEME`, o mesmo ID ao contrário). Sem `GOOGLE_IOS_CLIENT_ID` o botão
não aparece.

O servidor não muda: o app pede o token para o ID do site (`GIDServerClientID`), então o `aud` é
o mesmo que o backend já aceita em `GOOGLE_CLIENT_ID`. Não ponha dois IDs separados por vírgula
nessa variável: o `application.yml` a lê como um item só da lista e o login do site para.

## Regras e CI

As regras de cada etapa estão em [`CLAUDE.md`](CLAUDE.md). Todo PR que mexe em `ios-nativo/`
compila e roda os testes num simulador de iPhone (`.github/workflows/ios-nativo.yml`).

## O que já tem

- **Visual do site** (`Core/Design`): as cores do `styles.scss` no claro e no escuro, com os
  temas Chiclete, Rosa Piggu, Menta, Azul e Lilás (Premium), escolhidos em Mais e guardados
  no aparelho, a fonte Sora e os formatos de R$ e data em pt_BR.
- **Liquid Glass** só no que flutua: o `+` de lançar, o seletor de mês, os filtros de Gastos e o
  aviso depois de lançar (`pigguVidro()`, `GrupoDeVidro`, `botaoPrincipal()`). No iOS 17 e 18 vira o material translúcido do sistema;
  com Reduzir Transparência, fundo sólido.
- **Navegação**: abas Painel, Gastos, Receitas, Relatórios e Mais. O Mais segue os grupos e a
  ordem do menu do site (Planejar, Contas, Estilo de vida, Conta), com o Perfil no topo, o
  Premium só para o titular e as telas de estilo de vida que a pessoa ligou no Perfil (D-092) (`Tab` e `.sidebarAdaptable` no iOS
  18+, barra que encolhe ao rolar no 26), um `NavigationStack` por aba, o `+` flutuante e os
  links `piggu://`.
- **Estados** de toda tela: carregando (esqueleto), vazio, erro, erro do servidor e sem
  internet, com os textos do site.
- **Sem internet**: o resumo e os gastos de cada mês aberto ficam no SwiftData. A tela mostra
  o guardado na hora e atualiza em seguida; sem rede, avisa de quando é o que está na tela. O
  `NWPathMonitor` recarrega quando a rede volta. O cache é apagado ao sair da conta.
- **Entrada** (`Telas/Entrada`, espelho de `features/login`, `cpf`, `aceite`, `sobre-voce`,
  `redefinir-senha`, `confirmar-email` e `legal`):
  - Entrar com e-mail e senha, com a Apple (`SignInWithAppleButton`, `POST /api/auth/apple`
    com nonce e o nome da primeira vez) ou com o Google (`POST /api/auth/google` com o ID
    token), e "Continuar conectado neste aparelho". Conta nova pela Apple ou pelo Google pede o
    aceite dos termos (`TERMOS_NECESSARIOS`) e reaproveita o token.
  - Criar conta (`POST /api/auth/cadastro`) com nome, nascimento (13 anos ou mais), e-mail,
    senha com as regras de `regras-da-senha.ts`, celular com DDI, país, CPF com máscara e
    dígitos conferidos, e o aceite da versão atual dos termos.
  - Esqueci a senha, senha nova pelo link (`LINK_INVALIDO` oferece pedir outro) e confirmar
    e-mail pelo link, abertos por cima de qualquer tela.
  - Termos mudados (`PUT /api/me/termos`) e "Sobre você" (`PUT /api/me/sobre-voce`) antes das
    abas, como os guards do site; CPF opcional pelo Mais (`PUT /api/me/pais`, depois renova o
    token).
  - Termos de Uso e Aviso de Privacidade nativos, com o texto do site
    (`Piggu/Recursos/Termos.md` e `Privacidade.md`, gerados por `scripts/textos-legais.py`; o CI
    falha se ficarem diferentes do HTML do site).
  - Excluir minha conta no Mais e na tela dos termos (`DELETE /api/me`, digitando EXCLUIR).
- **Sessão como no app web**: o access token fica só em memória; o refresh vem no cookie
  HttpOnly (`Path=/api/auth`, `SameSite=Strict`) e fica no `HTTPCookieStorage` do sistema.
  Em 401 o app faz uma única renovação (`POST /api/auth/refresh`) e repete o pedido; chamadas
  simultâneas esperam a mesma renovação. Ao abrir o app, a sessão é recuperada pelo cookie.
  Sair chama `POST /api/auth/logout` e apaga o cookie. Sem rede ao abrir, o app entra com o
  perfil guardado e renova a sessão quando a rede volta.
- **Sessão no Keychain (desligada)**: com `PIGGU_REFRESH_NO_CORPO = YES` (build setting) ou
  `-PigguRefreshNoCorpo YES`, o login pede `refreshNoCorpo` e o refresh vai para o Keychain
  (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, só com "Continuar conectado"), e
  `/auth/refresh` e `/auth/logout` o recebem no corpo (D-072). Fica desligada até a etapa 1 do
  backend chegar à produção.
- **Painel** (`features/dashboard`): os cartões do site na ordem e com a visibilidade escolhidas em
  "Personalizar o Painel" (mesma chave e JSON do site, `piggu_painel_<id>`): sobra do mês,
  Para ficar de olho (orçamentos, fatura, contas do mês, quem está devendo), gasto e limite,
  contas bancárias do Open Finance (atualizar e desconectar), cofrinho com depósitos, cofrinhos
  por objetivo (criar, editar, guardar, apagar), metas do mês com anel, Girls Math, suas notas e
  por categoria (Swift Charts). Primeiros passos (moeda, limite, primeiro gasto), visão
  Tudo/Meu/Compartilhado para quem mora com alguém e a cotação da moeda no topo quando ligada no
  Perfil. No iOS 27 os cartões se arrastam no próprio Painel (`reorderable`); antes, pela tela de
  personalizar (`List` com `onMove`).
- **Gastos** (`features/expenses`): lista do mês por dia, busca na barra (`.searchable`, também em
  outros meses por `/expenses/search`), filtros de categoria e forma de pagamento em vidro, editar
  (com "Nova categoria" e o compartilhado), apagar com swipe (e as parcelas seguintes), sugestão e
  regras de categoria, e a divisão do mês.
- **Receitas** (`features/incomes`): resumo do mês, nova receita (recorrente e compartilhada),
  as que entram todo mês (parar de repetir), a receber (Recebi, apagar) e as receitas do mês.
- **Relatórios** (`features/reports`): o mês (gastos, mês anterior, projeção e cada categoria
  contra o mês anterior em Swift Charts), o ano mês a mês com receitas contra gastos, tabela e
  para onde foi o dinheiro (Premium), e "Exportar gastos do mês (CSV)" pelo `ShareLink`, com o
  mesmo arquivo do site (`/expenses/export`).
- **Contas do mês** (`features/bills`): as contas fixas do mês com a situação, marcar como paga
  (uma ou várias), apagar, criar (com "lançar sozinha") e as receitas que entram todo mês.
- **Orçamento** (`features/budgets`): o limite total do mês e o dos outros meses, e o limite por
  categoria com barra e alerta (Premium para criar e mudar).
- **Calendário** (`features/calendar`): a grade do mês com o gasto e os lembretes de cada dia, o
  dia escolhido, o gráfico de receitas e gastos acumulados (passando o dedo) com a tabela por
  semana, notas com e sem data (com valor, a nota lança um gasto). No iPad e em janela larga,
  grade e dia lado a lado.
- **Cartões e bancos** (`features/accounts`): contas e cartões (criar, compartilhar com o casal,
  apagar), a fatura de cada mês com os gastos e "Marcar como paga" / "Desfazer pagamento", e os
  bancos do Open Finance (também no Painel).
- **Minhas metas** (`features/minhas-metas`): o anel de gomos com o porquinho, que anima ao
  aparecer, criar, editar, +1, apagar e compartilhar o texto. As barras de progresso do app
  também enchem com animação (desligada com Reduzir Movimento).
- **Girls Math** (`features/girls-math`): quanto deixou de gastar no mês e desde o começo, com
  foto pela galeria (`PhotosPicker`) ou câmera.
- **Planejar** (`features/plan-hub`), **Prêmios** (`features/rewards`: Fofocoins, ajuste, novo
  prêmio, resgatar e resgates, só o que cada papel pode) e **Família** (`features/family`:
  convites enviados e recebidos, parceria, papéis, remover, sair e o histórico; quem é membro vê
  a lista e "Sair da família").
- **Perfil** (`features/profile`): foto pela galeria ou câmera (recortada em 320 px como no site),
  foto do Google, nome, moeda e cotação, avisos por e-mail, compartilhamento, organizar o Painel,
  telas do menu (módulos), aparência (automático, claro, escuro), cor do Piggu, ícone do app (os
  12 do app Capacitor, com `setAlternateIconName`), autorizações da IA, criar e trocar a senha,
  baixar meus dados, excluir a conta e, para o admin, as contas da instalação.
- **Compras, Lugares, Filmes e Fotos** (`features/shopping`, `places`, `movies`, `feed`): listas de
  compras e desejos com o catálogo, lugares com nota, marcadores, foto e um mapa (MapKit, a
  localização escrita vira ponto pela busca do Mapas, com "Abrir no Mapas"), filmes do casal com
  busca, sorteio por gênero e a nota de cada um, e as fotos do mês com legenda (enviar é Premium).
- **Widgets** (alvo `PigguWidgetsExtension`, D-093): gasto do mês contra o limite (pequeno, médio
  e na tela de bloqueio), a próxima meta do mês e, no iOS 18, o controle "Lançar gasto" na
  Central de Controle. Leem o resumo que o app grava no App Group `group.br.com.piggu.app`; nunca
  chamam a API nem guardam token.
- **Siri e Atalhos**: "Lançar gasto no Piggu" (o intent do app Capacitor, agora abrindo a folha
  nativa) e "Quanto gastei este mês no Piggu", que responde em voz sem abrir o app.
- **Face ID**: no Perfil, travar o app ao abrir e ao voltar do fundo, com o vidro por cima. A
  trava abre numa janela própria, acima de qualquer folha aberta, e fecha o teclado (D-097).
- **Notificações**: a permissão é pedida ao ligar "Notificação neste iPhone" no Perfil; o token vai
  para `POST /api/auth/me/devices` (plataforma IOS) a cada abertura e sai ao deslogar; tocar no
  aviso abre o Painel.
- **Dicas** (TipKit): as do tour do site, cada uma perto do que explica.
- **Ler a nota**: no lançar gasto, a câmera (VisionKit) lê o total e a data do cupom e preenche para
  a pessoa conferir; o botão só aparece onde o aparelho lê texto.
- **Busca do iPhone**: os gastos do mês entram no Spotlight; tocar abre o gasto na aba Gastos.
- **Topo das abas** (o cabeçalho do site, D-097): a marca do Piggu à esquerda, que volta ao Painel,
  e à direita a **busca** (telas, gastos de todos os meses, lugares, filmes e compras; tocar abre
  a tela ou o gasto no mês dele) e o **assistente** de IA (Premium: autorização do envio ao
  Gemini, sugestões, atalho para a tela da resposta; a conversa fica só no aparelho por 30 dias e
  sai ao sair da conta).
- **Teclado**: tocar fora de um campo ou arrastar a lista fecha o teclado, em qualquer tela.
- **Barra de abas**: fica sempre inteira; "Esconder a barra de abas ao rolar" no Perfil (iOS 26+).
- **Conectar banco** (D-091): o app pede o token da Pluggy com a autorização da pessoa e abre a
  página `/conectar-banco` do site num `ASWebAuthenticationSession` efêmero, nunca num WebView; o
  banco conectado volta em `piggu://banco-conectado?item=...`.
- **Lançar gasto** (botão +): o lançamento rápido do site: valor com vírgula (`12,50`,
  `1.234,56`) e aviso de preço acima da média, categoria (Automática ou nova), descrição, cartão
  ou débito/Pix com a conta (a última fica de padrão), a fatura em que a compra cai, à vista,
  parcelado ou recorrente, e em "Mais opções" a data, a moeda da compra (convertida pela
  cotação), dividir e compartilhar. Depois de lançar, avisa se a categoria passou de 80% do
  orçamento.
- **Sem internet ao lançar**: o gasto vai para uma fila no SwiftData (`FilaDeGastos`) e sobe na
  ordem quando a rede volta, ao abrir o app ou no "Enviar agora" de Gastos, com o mesmo
  `idDoEnvio`. Um aviso discreto no topo diz que ficou guardado e quando foi enviado; o que o
  servidor recusar (400/422) sai da fila com aviso. A fila é de quem lançou e não some ao sair.
- **Mais**: nome, e-mail, plano, e-mail sem confirmar (com reenviar), CPF não informado, Cor do
  Piggu, Termos, Aviso de Privacidade, Excluir minha conta e Sair.
- **Premium**: o plano de `/api/billing/plan`; para o titular sem assinatura na casa, a loja da
  Apple (`SubscriptionStoreView`, mensal e anual) com restaurar compras. A compra leva o id da
  conta (`appAccountToken`) e a transação vai ao backend antes de ser finalizada; renovações e
  compras feitas fora da tela chegam por `Transaction.updates`. Quem é Premium pelo site vê o
  plano ativo e não vê a loja (D-094).
- Textos em pt-BR no String Catalog (`Piggu/Localizable.xcstrings`), valores em R$.
- Testes (Swift Testing): os produtos do `Piggu.storekit` contra os do app (pelo `SKTestSession`),
  quem vê a loja do Premium, o leitor do cupom, o resumo dos widgets (sem token), a fala da Siri, o
  destino das notificações e os links novos, as metas (percentual e texto de compartilhar), o recorte da foto de
  perfil, o histórico e o plano da família, os ícones do app (cada um no catálogo), o Open Finance (a página com o token no fragmento, a volta e o fluxo
  inteiro), as contas do calendário (os casos do `grafico-do-mes.spec.ts`), a fila sem internet, a ordem do Painel (com o JSON salvo pelo site), a
  fatura em que cai a compra (os casos do `fatura.spec.ts`), decodificação dos modelos com JSON no formato do backend, parse de
  valor com vírgula, formatos pt_BR, o fluxo 401 → refresh → repetição, o refresh no corpo e
  no Keychain, o cache, os links e o fluxo de entrada inteiro contra a API falsa (Apple e Google
  com conta nova, cadastro, links vencidos, termos, CPF, excluir conta), além das regras de
  senha, e-mail, CPF e nascimento com os mesmos casos do site.

Prints em [`docs/prints/`](docs/prints/) (tirados com `-PigguDemo`, sem dados reais); os da
etapa 2, no iPhone e no iPad, em [`docs/prints/etapa-2/`](docs/prints/etapa-2/); os da etapa 3
(entrada), em [`docs/prints/etapa-3/`](docs/prints/etapa-3/); os da etapa 4 (Painel, Gastos e
Receitas), em [`docs/prints/etapa-4/`](docs/prints/etapa-4/); os da etapa 5 (organização), em
[`docs/prints/etapa-5/`](docs/prints/etapa-5/); os da etapa 6 (metas e conta), em
[`docs/prints/etapa-6/`](docs/prints/etapa-6/); os da etapa 7, em
[`docs/prints/etapa-7/`](docs/prints/etapa-7/); os da etapa 8 (Premium), em
[`docs/prints/etapa-8/`](docs/prints/etapa-8/); os da etapa 9 (texto grande, escuro e iPad), em
[`docs/prints/etapa-9/`](docs/prints/etapa-9/).

### Acessibilidade e desempenho (etapa 9)

- Nos tamanhos de texto de acessibilidade, o que fica lado a lado vira um embaixo do outro
  (`LadoALado`), o seletor de mês encurta o nome e os filtros dos Gastos rolam de lado; o
  calendário e o número do anel das metas param no xxLarge e xxxLarge, com o VoiceOver lendo cada
  dia e o percentual (D-095).
- VoiceOver: as estrelas de lugares e filmes são um elemento ajustável (deslize para cima ou para
  baixo), e os avisos que somem sozinhos são lidos na hora. Áreas de toque de pelo menos 44 pt.
- Reduzir Movimento tira a animação do anel, do porquinho e do aviso que desce do topo; Reduzir
  Transparência troca o vidro por cor sólida.
- No iPad e na janela redimensionável do iOS 27, o layout segue o tamanho disponível: barra
  lateral na janela larga, abas no topo na estreita (como o iPad mini em retrato), e a mesma tela
  do iPhone abaixo disso. Testado do iPhone 17e ao iPad Pro 13 em paisagem.
- Abrir o app com o perfil guardado não espera a rede: as telas mostram o que guardaram e a
  renovação do token segue por trás (D-095). No simulador, com a build otimizada: 0,63 a 0,75 s
  do `init` ao Painel com dados, também com 3.000 gastos.

## Versão e envio

Versão 2.0.0 (`MARKETING_VERSION`), build 2 (`CURRENT_PROJECT_VERSION`), Bundle ID
`br.com.piggu.app`, time `H766LA27ZJ`, assinatura automática. Entitlements: push
(`aps-environment`, que o Xcode troca para production ao exportar), App Group
`group.br.com.piggu.app` (o widget também), Entrar com Apple e os domínios associados de
`piggu.com.br`. Para gerar o archive e o pacote da App Store pelo Terminal:

```bash
cd ios-nativo
xcodebuild archive -project Piggu.xcodeproj -scheme Piggu -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Piggu.xcarchive -allowProvisioningUpdates
xcodebuild -exportArchive -archivePath build/Piggu.xcarchive -exportPath build/exportado \
  -exportOptionsPlist Config/ExportOptions.plist -allowProvisioningUpdates
```

O `Config/ExportOptions.plist` tem `destination` = `export` (só gera o `.ipa`). Enviar ao App
Store Connect (`destination` = `upload`) só quando o Dudu pedir. A cada envio, suba o build.

### Assinatura no simulador

O esquema **Piggu** usa o `PigguTests/Piggu.storekit` ao rodar pelo Xcode (⌘R): os dois planos
com 7 dias grátis, sem cobrança e sem conta da Apple. Em *Debug → StoreKit → Manage
Transactions* dá para renovar, cancelar e reembolsar. Aberto fora do Xcode (`simctl launch`) o
simulador não carrega o arquivo e a loja aparece "indisponível", por isso os prints mostram só o
plano ativo. Com `-PigguDemo`, a compra vira Premium pela App Store na API falsa.

## O que falta

- **Revogar o token da Apple ao excluir a conta**, que a App Store exige: falta no backend
  (`docs/API_SPEC.md`).
- **Ligar o Keychain**: trocar `PIGGU_REFRESH_NO_CORPO` para `YES` quando a etapa 1 do backend
  estiver em produção.
- **Demais telas** do app web: até chegarem, os atalhos do Painel e dos formulários abrem a
  página no site (`LinkDoSite`, D-090).
- **Leitura da nota pela IA** (a foto inteira com os itens, no site) e **importar extrato**: por
  enquanto só no site; o app lê o total e a data do cupom pela câmera.
- Prints para a App Store.
- Medir a abertura num iPhone de verdade (Instruments, App Launch); no simulador o carregamento
  antes do `init` não vale como medida.
