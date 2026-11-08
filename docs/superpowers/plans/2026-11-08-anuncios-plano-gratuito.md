# Anúncios no plano gratuito (Android e iOS) — plano de implementação

> **Para agentes:** use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para seguir este plano tarefa por tarefa. Os passos usam
> checkbox (`- [ ]`). Os prompts prontos para cada PR estão no fim do arquivo.

**Objetivo:** mostrar um banner do Google AdMob, sem personalização, para quem está no plano
gratuito dos apps nativos; quem tem Premium (ou é ADMIN) nunca vê anúncio.

**Arquitetura:** uma regra pura `deveMostrarAnuncios` em cada app decide pelo usuário da sessão
e pelo modo demonstração. A moldura (Android `Moldura.kt`, iOS `PrincipalView.swift`) desenha
um banner adaptativo logo acima da barra de abas, só nas abas Painel e Lançamentos. O SDK só
é iniciado quando a regra der verdadeiro pela primeira vez. O site continua sem anúncios.

**Tecnologia:** Google Mobile Ads SDK (Android `com.google.android.gms:play-services-ads`;
iOS Swift Package `https://github.com/googleads/swift-package-manager-google-mobile-ads`),
Hilt e Compose no Android, SwiftUI e Observation no iOS.

**Spec:** a conversa de 08/11/2026 (resumida em "Decisões fixadas" abaixo) e a futura D-180.

## Decisões fixadas

- Só apps nativos. Site sem anúncios (CSP e cookies continuam como estão).
- Anúncio **não personalizado** sempre: parâmetro `npa=1` em toda requisição. Sem pedido de
  rastreamento (ATT) no iOS e sem `NSUserTrackingUsageDescription`.
- Android: remover a permissão `com.google.android.gms.permission.AD_ID` no manifesto
  (`tools:node="remove"`). Nenhum dado do Piggu (gasto, categoria, valor, e-mail) vai para o SDK.
- **Menores de 18 não veem anúncio** (ECA Digital, Lei 15.211/2025). Classificação máxima **PG**
  (`MAX_AD_CONTENT_RATING_PG` / `.parentalGuidance`) como segunda proteção.
- Formato: só **banner adaptativo ancorado**. Nada de tela cheia, vídeo com recompensa ou app open.
- Onde aparece: abas **Painel** e **Lançamentos**, acima da barra de abas (ou no pé do conteúdo
  quando o menu é lateral). Nunca em login, termos, "Sobre você", Premium/Plano, bancos/Open
  Finance, lançar gasto, assistente, telas legais e demonstração.
- Regra: mostra se há usuário logado, o plano é `GRATUITO`, não é ADMIN, não é demonstração e
  a pessoa tem **18 anos ou mais** pelo `nascimento` (data ISO), contados no dia de hoje em
  `America/Sao_Paulo`. Plano `DESCONHECIDO`, sessão abrindo, `nascimento` nulo ou ilegível: **não** mostra.
- Banner sem anúncio carregado (sem internet, sem preenchimento) ocupa **altura zero**.
- Ids: build de debug e a variante/modo demo usam os ids de teste do Google; release usa os
  ids reais, lidos de configuração (não são segredo, mas não ficam espalhados no código).
- Distribuição só no Brasil (Europa fora dos planos, decisão do Dudu em 08/11): sem formulário de
  consentimento UMP. Brasileiro abrindo o app na Europa recebe anúncio limitado ou nenhum, e o
  banner fica com altura zero.
  `shortcut: sem UMP, adicionar se o app for distribuído no Espaço Econômico Europeu ou no Reino Unido`.
- "Sem anúncios nos apps" entra na lista de benefícios do Premium. A lista vem do backend
  (`PlanoResponse.ITENS_PREMIUM`), então site e apps mostram sem mudar tela (feito no PR 1).
- A versão anterior do aviso (`2026-10-16`) vale até 07/01/2027 (`Consentimentos.versaoAceita`),
  para os apps instalados não quebrarem (feito no PR 1).
- Bloquear no console da AdMob as categorias: empréstimos e crédito, apostas e jogos de azar,
  criptomoedas, "ganhe dinheiro rápido", namoro e conteúdo sensível (tarefa manual, Tarefa 6).

## Restrições globais

- Textos em pt-BR (Android em `strings.xml` do módulo; iOS no `Localizable.xcstrings`).
- PR sempre para a `develop`; branch a partir da `develop` atualizada.
- Decisão nova: **D-180** (o maior D-xxx nas branches em 08/11/2026 era D-179; confira de novo
  com `git fetch && git grep -h "^## D-" $(git branch -r | grep -v -- '->') -- docs/DECISIONS.md`
  antes de abrir o PR), com "Decidido por: Dudu" e "Registrado por: <assistente>".
- Biblioteca nova no iOS só com decisão (README do `ios-nativo`, seção Bibliotecas): a D-180
  libera o Google Mobile Ads e o README passa a listá-lo.
- Nunca enviar ao TestFlight ou ao Google Play sem o Dudu pedir. Se pedir: iOS build 6,
  Android versionCode 204 ou mais (ver memória do projeto).
- Ids de teste do Google (confira na documentação oficial antes de usar):
  - Android app `ca-app-pub-3940256099942544~3347511713`, banner adaptativo `ca-app-pub-3940256099942544/9214589741`
  - iOS app `ca-app-pub-3940256099942544~1458002511`, banner adaptativo `ca-app-pub-3940256099942544/2435281174`

## Pontos de atenção na revisão

1. Pessoa assina o Premium dentro do app: o banner some na hora, sem reiniciar o app
   (a regra é derivada do estado da sessão, não lida uma vez). Teste na Tarefa 2 e na 4.
2. Sem internet ou sem anúncio disponível: nenhum espaço em branco nem pulo de layout
   (altura zero até `onAdLoaded`). Verificação manual nas Tarefas 3 e 5.
3. Sessão abrindo, plano `DESCONHECIDO`, usuário nulo, menor de 18 ou nascimento ausente: sem
   anúncio; o aniversário de 18 anos vale no fuso de Brasília. Teste nas Tarefas 2 e 4.
4. Fonte grande, tablet, iPad, paisagem e menu lateral: a largura do banner vem do espaço
   disponível, não da tela. Verificação manual com prints.
5. Sair e entrar com outra conta (uma Premium, outra gratuita): o banner segue a conta nova.
   Teste nas Tarefas 2 e 4.

---

## PR 1 — Decisão e textos legais (antes dos apps)

### Tarefa 1: D-180, privacidade, termos e benefício "Sem anúncios" (feita em 08/11/2026)

**Arquivos:**
- Modificar: `docs/DECISIONS.md` (nova D-180 no fim)
- Modificar: `docs/ROADMAP.md` (item "Anúncios no plano gratuito", com as Tarefas 2 a 6)
- Modificar: `frontend/src/app/features/legal/privacidade.html` (linhas ~84 e ~149; tabela de operadores ~86-111)
- Modificar: `frontend/src/app/features/legal/termos.html` (nova seção sobre anúncios no gratuito)
- Modificar: `android-nativo/feature/legal/src/main/assets/Privacidade.md` e o `Termos.md` ao lado
- Modificar: `ios-nativo/Piggu/Recursos/Privacidade.md` e o `Termos.md` ao lado
- Modificar: a versão do aviso em todos os lugares (`grep -rn "2026-10-16" frontend/src backend/*/src android-nativo/*/src android-nativo/*/*/src ios-nativo/Piggu`), incluindo
  `frontend/src/app/core/privacidade/aviso.ts`, `backend/piggu-common/src/main/java/com/piggu/common/dados/Consentimentos.java`,
  `android-nativo/core/data/src/main/kotlin/br/com/piggu/core/data/RegrasDaConta.kt` e o equivalente no iOS
- Modificar: `frontend/src/app/features/plan/plan.html` (benefício "Sem anúncios nos apps")
- Modificar: `frontend/src/locale/messages.xlf` (regerar com `npm run extract-i18n` ou o script do projeto)

- [ ] **Passo 1:** escrever a D-180 com as "Decisões fixadas" acima, alternativas
  (anúncio personalizado com ATT; AdSense no site; tela cheia) e por que não.
- [ ] **Passo 2:** privacidade: trocar "não usamos para publicidade e não mostramos anúncios" por
  texto que diga que **no plano gratuito dos apps** há anúncios do Google AdMob **não
  personalizados**, que o Piggu não passa dados financeiros para anunciantes e que o Premium
  remove os anúncios. Linha nova na tabela de operadores: "Google (AdMob, anúncios) · dados
  do aparelho e endereço IP para entregar o anúncio · só no plano gratuito dos apps".
  Ajustar a frase de cookies: o site continua sem cookies de publicidade.
- [ ] **Passo 3:** termos: seção curta "Anúncios no plano gratuito" com o mesmo conteúdo.
- [ ] **Passo 4:** copiar os mesmos textos para os `.md` do Android e do iOS.
- [ ] **Passo 5:** subir a versão do aviso para a data do merge em **todos** os lugares do grep
  (o app pede o aceite de novo, que é o que queremos). Rodar os testes que conferem a versão.
- [ ] **Passo 6:** "Sem anúncios nos apps" na lista do Premium do site.
- [ ] **Passo 7:** verificar: `cd frontend && npm test`, `cd backend && ./mvnw -q test -pl piggu-common`,
  `cd android-nativo && ./gradlew :feature:legal:testDebugUnitTest :core:data:testDebugUnitTest`.
- [ ] **Passo 8:** commit e PR para a `develop`.

---

## PR 2 — Android

### Tarefa 2: regra `deveMostrarAnuncios` e estado na moldura

**Arquivos:**
- Modificar: `android-nativo/core/data/src/main/kotlin/br/com/piggu/core/data/RegrasDaConta.kt`
- Testar: `android-nativo/core/data/src/test/kotlin/br/com/piggu/core/data/RegrasDaContaTest.kt` (criar se não existir)
- Modificar: `PerfilDaMoldura` (em `app/.../moldura/`) e `app/src/main/kotlin/br/com/piggu/app/AppViewModel.kt`

**Interfaces:**
- Produz: `fun Usuario?.deveMostrarAnuncios(demo: Boolean, hoje: LocalDate = LocalDate.now(ZoneId.of("America/Sao_Paulo"))): Boolean` em `core:data`
- Produz: `PerfilDaMoldura.mostraAnuncios: Boolean`, calculado no `AppViewModel` a partir de `sessao.estado`

- [ ] **Passo 1: testes que falham**

```kotlin
@Test fun gratuitoLogadoVeAnuncio() = assertTrue(usuario(plano = GRATUITO, role = TITULAR).deveMostrarAnuncios(demo = false))
@Test fun premiumNaoVe() = assertFalse(usuario(plano = PREMIUM, role = TITULAR).deveMostrarAnuncios(demo = false))
@Test fun adminNaoVe() = assertFalse(usuario(plano = GRATUITO, role = ADMIN).deveMostrarAnuncios(demo = false))
@Test fun demoNaoVe() = assertFalse(usuario(plano = GRATUITO, role = MEMBRO).deveMostrarAnuncios(demo = true))
@Test fun planoDesconhecidoNaoVe() = assertFalse(usuario(plano = DESCONHECIDO, role = MEMBRO).deveMostrarAnuncios(demo = false))
@Test fun semUsuarioNaoVe() = assertFalse((null as Usuario?).deveMostrarAnuncios(demo = false))
// hoje = 2026-11-08 nos testes de idade
@Test fun fez18HojeVe() = assertTrue(usuario(plano = GRATUITO, role = TITULAR, nascimento = "2008-11-08").deveMostrarAnuncios(false, hoje))
@Test fun faz18AmanhaNaoVe() = assertFalse(usuario(plano = GRATUITO, role = MEMBRO, nascimento = "2008-11-09").deveMostrarAnuncios(false, hoje))
@Test fun semNascimentoNaoVe() = assertFalse(usuario(plano = GRATUITO, role = TITULAR, nascimento = null).deveMostrarAnuncios(false, hoje))
@Test fun nascimentoIlegivelNaoVe() = assertFalse(usuario(plano = GRATUITO, role = TITULAR, nascimento = "xx").deveMostrarAnuncios(false, hoje))
```

Os testes que não falam de idade usam um adulto (`nascimento = "1990-01-01"`).

(`usuario(...)` é um construtor de teste; reutilize o que `SessaoTest` já usa.)

- [ ] **Passo 2:** `./gradlew :core:data:testDebugUnitTest` → falha (função não existe).
- [ ] **Passo 3:** implementar: `this != null && !demo && !ehPremium && plano == Plano.GRATUITO && maiorDeIdade(hoje)`,
  com `nascimento` lido por `LocalDate.parse` (falha de leitura conta como menor).
- [ ] **Passo 4:** rodar de novo → passa.
- [ ] **Passo 5:** em `AppViewModel`, preencher `mostraAnuncios` no `PerfilDaMoldura` com
  `usuario.deveMostrarAnuncios(demo = BuildConfig.FLAVOR == "demo")` (ou a flag de demo que
  a moldura já recebe). Teste em `app/src/test/.../moldura/`: estado da sessão muda de
  gratuito para premium → `mostraAnuncios` vai de `true` para `false`; trocar de conta
  gratuita para Premium também (pontos 1 e 5).
- [ ] **Passo 6:** `./gradlew :app:testDemoDebugUnitTest :app:testProducaoDebugUnitTest` → passa. Commit.

### Tarefa 3: SDK, banner e benefício no Plano

**Arquivos:**
- Modificar: `android-nativo/gradle/libs.versions.toml` (`playServicesAds` = última estável, conferida no Maven do Google)
- Modificar: `android-nativo/app/build.gradle.kts` (dependência; `manifestPlaceholders["admobAppId"]` e `buildConfigField("String", "ADMOB_BANNER", ...)`: debug e demo com ids de teste; release `producao` lendo `ADMOB_APP_ID` e `ADMOB_BANNER_ID` de `gradle.properties`/variável de ambiente, falhando o build de release se faltarem)
- Modificar: `android-nativo/app/src/main/AndroidManifest.xml` (`<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${admobAppId}"/>` e `<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove"/>`)
- Criar: `android-nativo/app/src/main/kotlin/br/com/piggu/app/moldura/BannerDeAnuncio.kt`
- Modificar: `android-nativo/app/src/main/kotlin/br/com/piggu/app/moldura/Moldura.kt` (`bottomBar` vira `Column { if (perfil.mostraAnuncios && abaComAnuncio) BannerDeAnuncio(); NavigationBar }`; no menu lateral, o banner fica no pé do conteúdo)
- Modificar: `android-nativo/README.md` (bibliotecas e como configurar os ids)

**Interfaces:**
- Consome: `PerfilDaMoldura.mostraAnuncios` (Tarefa 2)
- Produz: `@Composable fun BannerDeAnuncio(modifier: Modifier = Modifier)`

- [ ] **Passo 1:** iniciar o SDK uma vez, fora da thread principal, só quando `mostraAnuncios`
  ficar `true` pela primeira vez: `MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG).build())` e depois `MobileAds.initialize(contexto)`.
- [ ] **Passo 2:** `BannerDeAnuncio`: `AndroidView` com `AdView`, tamanho
  `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(contexto, larguraDisponivelDp)`
  (largura via `BoxWithConstraints`, não da tela), `AdRequest` com
  `addNetworkExtrasBundle(AdMobAdapter::class.java, bundleOf("npa" to "1"))`. Altura 0 até
  `onAdLoaded`; `destroy()` no `onRelease`. `contentDescription` do contêiner: "Anúncio".
- [ ] **Passo 3:** abas com anúncio: Painel e Lançamentos (gastos e receitas). Nenhuma outra.
- [ ] **Passo 4:** verificar: `./gradlew :app:assembleProducaoDebug lint testDemoDebugUnitTest testProducaoDebugUnitTest`;
  instalar `producaoDebug` com conta gratuita → banner "Test Ad" no Painel; conta Premium →
  sem banner; modo avião → sem espaço vazio; `installDemoDebug` → sem banner; fonte grande e
  tablet → banner cabe. Prints em claro e escuro em `android-nativo/docs/prints/anuncios/`.
- [ ] **Passo 5:** commit e PR para a `develop`.

---

## PR 3 — iOS

### Tarefa 4: regra `deveMostrarAnuncios` no iOS

**Arquivos:**
- Modificar: `ios-nativo/Piggu/Core/Auth/Conta.swift` (extensão de `Usuario`)
- Testar: `ios-nativo/PigguTests/AnunciosTests.swift` (Swift Testing)

**Interfaces:**
- Produz: `func deveMostrarAnuncios(_ usuario: Usuario?, demo: Bool, hoje: Date = .now) -> Bool` (idade pelo `Calendar` gregoriano no fuso `America/Sao_Paulo`)

- [ ] **Passo 1:** testes que falham com os casos da Tarefa 2 (gratuito adulto vê; premium, admin, demo, nil,
  quem faz 18 amanhã, sem nascimento e nascimento ilegível não veem; quem faz 18 hoje vê; o `Plano` do iOS em `Core/API/Modelos.swift` não tem caso desconhecido), mais: um `Sessao` de teste trocando o
  `usuario` de gratuito para premium faz a leitura computada `deveMostrarAnuncios(sessao.usuario, demo:)` mudar.
- [ ] **Passo 2:** `xcodebuild test -scheme Piggu -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PigguTests/AnunciosTests` → falha.
- [ ] **Passo 3:** implementar: `guard let usuario, !demo else { return false }; return usuario.role != .admin && usuario.plano == .gratuito && maiorDeIdade(usuario.nascimento, hoje)`
  Trocar o `ehPremium` duplicado de `AssistenteView.swift:42` por uma propriedade única em `Usuario` se ficar natural.
- [ ] **Passo 4:** rodar → passa. Commit.

### Tarefa 5: SDK, banner e benefício no Premium

**Arquivos:**
- Modificar: `ios-nativo/Piggu.xcodeproj/project.pbxproj` (pacote `swift-package-manager-google-mobile-ads`, última versão estável, travada no `Package.resolved`)
- Modificar: `ios-nativo/Piggu/Info.plist` (`GADApplicationIdentifier` = `$(ADMOB_APP_ID)`; `SKAdNetworkItems` com a lista atual do Google; **sem** `NSUserTrackingUsageDescription`)
- Modificar: `ios-nativo/Config/Piggu.xcconfig` (`ADMOB_APP_ID` e `ADMOB_BANNER_ID`, com `[config=Debug]` para os ids de teste e os reais no Release)
- Criar: `ios-nativo/Piggu/Telas/Componentes/BannerDeAnuncio.swift`
- Modificar: `ios-nativo/Piggu/Telas/PrincipalView.swift` (banner sobre a barra de abas, só nas abas `.painel` e `.gastos`, via `.safeAreaInset(edge: .bottom)` no conteúdo dessas abas)
- Modificar: `ios-nativo/README.md` (seção Bibliotecas cita a D-180 e o Google Mobile Ads)
- Modificar: `ios-nativo/Piggu/Recursos/` ou o `PrivacyInfo.xcprivacy` do app se existir (o SDK traz o dele; conferir que o relatório de privacidade do Xcode fica coerente)

**Interfaces:**
- Consome: `deveMostrarAnuncios(_:demo:)` (Tarefa 4)
- Produz: `struct BannerDeAnuncio: View`

- [ ] **Passo 1:** iniciar uma vez quando a regra der `true` pela primeira vez:
  `MobileAds.shared.requestConfiguration.maxAdContentRating = .parentalGuidance` e
  `await MobileAds.shared.start()`. Nunca no modo `-PigguDemo`.
- [ ] **Passo 2:** `BannerDeAnuncio`: `UIViewRepresentable` com `BannerView`, tamanho
  `currentOrientationAnchoredAdaptiveBanner(width:)` com a largura do `GeometryReader`,
  `Request` com `Extras` `additionalParameters = ["npa": "1"]`. Altura 0 até
  `bannerViewDidReceiveAd`. Rótulo de acessibilidade "Anúncio".
- [ ] **Passo 3:** verificar: `xcodebuild build test` num iPhone e num iPad; conta gratuita →
  "Test Ad" no Painel e em Lançamentos; conta Premium → nada; modo avião → sem espaço vazio;
  `-PigguDemo` → nada; Dynamic Type grande e iPad redimensionado → cabe. Prints em claro e
  escuro em `ios-nativo/docs/prints/anuncios/`.
- [ ] **Passo 4:** commit e PR para a `develop`.

---

## Fora do código (Dudu)

### Tarefa 6: AdMob e lojas

- [ ] Criar a conta AdMob (pagamentos e dados fiscais), um app Android (`br.com.piggu.app`) e
  um iOS, e um bloco **banner** em cada. Passar os 4 ids para o `.env`/segredos do CI (não
  são senha, mas ficam fora do código).
- [ ] AdMob → Controles de bloqueio: bloquear as categorias de "Decisões fixadas"; classificação
  máxima PG; desligar anúncios personalizados no app (além do `npa=1`).
- [ ] `app-ads.txt` em `https://piggu.com.br/app-ads.txt` com a linha que a AdMob mostrar
  (o site é o do desenvolvedor nas lojas). Colocar em `frontend/public/app-ads.txt`.
- [ ] Disponibilidade só no Brasil no Google Play e na App Store.
- [ ] Google Play Console: "Contém anúncios" = sim; público-alvo 13+ com anúncios só para adultos; Segurança dos dados: dados do aparelho e
  IP coletados pelo SDK para publicidade, não personalizados, não vendidos.
- [ ] App Store Connect: Privacidade do app: dados de uso e diagnóstico para "Publicidade de
  terceiros", **não** vinculados à identidade e **sem** rastreamento.
- [ ] Atualizar a nota `04-Projetos/projeto-piggu-cofrinho-app.md` no Segundo Cérebro.

---

## Prompts prontos

Use um prompt por sessão, nesta ordem. Os PRs 2 e 3 podem correr em paralelo depois do PR 1
mergeado.

### Prompt 1 — Decisão e textos legais

```
Leia docs/superpowers/plans/2026-11-08-anuncios-plano-gratuito.md e execute só o "PR 1"
(Tarefa 1), seguindo o AGENTS.md. Branch feature/anuncios-textos a partir da develop
atualizada. Antes de escrever a D-180, confira o maior D-xxx nas branches remotas e use o
próximo livre. Suba a versão do aviso para a data de hoje em todos os lugares do grep do
plano e rode os testes do passo 7. Abra o PR para a develop e me mande o link e o que mudou
nos textos de privacidade e termos, lado a lado, para eu aprovar a redação.
```

### Prompt 2 — Android

```
Leia docs/superpowers/plans/2026-11-08-anuncios-plano-gratuito.md e execute o "PR 2"
(Tarefas 2 e 3), seguindo o AGENTS.md da raiz e o android-nativo/AGENTS.md. O PR 1 já está
na develop. Branch feature/anuncios-android a partir da develop atualizada. Use TDD na
Tarefa 2. Use sempre os ids de teste do Google; os ids reais entram depois por
gradle.properties/variável de ambiente (ADMOB_APP_ID, ADMOB_BANNER_ID). Confira na
documentação oficial a versão estável atual do play-services-ads e os ids de teste antes
de usar. Faça as verificações do passo 4 num emulador (conta gratuita, conta Premium,
modo avião, demo, fonte grande, tablet) e salve os prints. Não publique nada no Google
Play. Abra o PR para a develop.
```

### Prompt 3 — iOS

```
Leia docs/superpowers/plans/2026-11-08-anuncios-plano-gratuito.md e execute o "PR 3"
(Tarefas 4 e 5), seguindo o AGENTS.md da raiz e o ios-nativo/AGENTS.md. O PR 1 já está na
develop. Branch feature/anuncios-ios a partir da develop atualizada. Use TDD na Tarefa 4
(Swift Testing). Adicione o Google Mobile Ads pelo Swift Package Manager na última versão
estável, conferida na documentação oficial, e trave no Package.resolved; ids de teste no
Debug e ADMOB_APP_ID/ADMOB_BANNER_ID no xcconfig de Release. Não peça permissão de
rastreamento (ATT). Rode build e testes num iPhone e num iPad, faça as verificações do passo
3 e salve os prints. Não envie nada ao TestFlight. Abra o PR para a develop.
```

### Prompt 4 — Revisão final antes de publicar

```
Os PRs de anúncios (textos, Android e iOS) estão na develop. Confira contra
docs/superpowers/plans/2026-11-08-anuncios-plano-gratuito.md: (1) os 5 "Pontos de atenção
na revisão" estão cobertos por teste ou print; (2) nenhum dado do Piggu vai para o SDK de
anúncios (grep por AdRequest/Request e o que é passado); (3) npa=1 e classificação PG nos
dois apps; (4) AD_ID removida no manifesto final (./gradlew :app:processProducaoReleaseManifest
e leia o manifesto mesclado); (5) privacidade, termos e as cópias .md dos apps dizem a mesma
coisa. Me diga o que falta da Tarefa 6 (AdMob e lojas) antes de eu publicar.
```
