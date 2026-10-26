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
```

### Argumentos de lançamento (Debug)

No Xcode, em *Product → Scheme → Edit Scheme → Run → Arguments* (já cadastrados, desligados):

| Argumento | O que faz |
| --- | --- |
| `-PigguAPIURL http://localhost:8080/api` | Troca a API (o padrão é `https://piggu.com.br/api`, do build setting `PIGGU_API_URL`). |
| `-PigguDemo YES` | Dados inventados, sem rede. Qualquer e-mail e senha entram. |
| `-PigguDemoLogado YES` | Com a demonstração, já abre logado. |
| `-PigguDemoFalha rede` / `servidor` / `vazio` | Com a demonstração: sem internet, erro 500 ou mês sem gastos, para ver os estados das telas. |
| `-PigguAba gastos` / `relatorios` / `mais` | Abre direto numa aba. |
| `-PigguLancar YES` | Abre direto o lançamento de gasto. |
| `-piggu_tema azul` | Abre com outra cor do Piggu (`chiclete`, `rosa`, `menta`, `azul`, `lilas`). |
| `-PigguRefreshNoCorpo YES` | Refresh no corpo e no Keychain em vez do cookie (veja *Sessão*). |
| `-PigguDemoConta termos` / `sobre-voce` / `cpf` / `email` | Com a demonstração: a conta abre com os Termos mudados, sem apelido e nascimento, sem CPF ou com o e-mail sem confirmar. |
| `-PigguEntrada cadastro` / `conta-nova` | Abre a entrada na aba Criar conta ou no aceite de conta nova pela Apple ou pelo Google. |
| `-PigguLink piggu://termos` | Abre um link depois de restaurar a sessão (`termos`, `privacidade`, `esqueci-senha`, `cpf`, `confirmar-email?token=...`, `redefinir-senha?token=...`). |

A demonstração e esses atalhos só existem em Debug; não vão para o build de Release (o
`-PigguRefreshNoCorpo` vale nos dois).

### Links

`piggu://gasto?valor=12.50` abre o lançamento com o valor preenchido. Os links do app Capacitor
continuam valendo (`piggu://gastos?rapido=1&valor=12.50`, do atalho do ícone e da Siri), e as
rotas do site abrem a aba certa: `piggu://painel`, `piggu://gastos`, `piggu://relatorios`,
`piggu://perfil`. Para testar no simulador:

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

O app já tem o *Associated Domains* `applinks:piggu.com.br`. Para o link do e-mail abrir o app em
vez do site, falta publicar no site o arquivo `https://piggu.com.br/.well-known/apple-app-site-association`
(JSON, sem extensão, sem redirecionamento), com o Team ID da conta de desenvolvedor no lugar de
`TEAMID`:

```json
{
  "applinks": {
    "details": [
      {
        "appIDs": ["TEAMID.br.com.piggu.app"],
        "components": [
          { "/": "/confirmar-email", "?": { "token": "?*" } },
          { "/": "/redefinir-senha", "?": { "token": "?*" } }
        ]
      }
    ]
  },
  "webcredentials": { "apps": ["TEAMID.br.com.piggu.app"] }
}
```

No site, o arquivo vai em `frontend/public/.well-known/` e o `deploy/Caddyfile` precisa servi-lo
com `Content-Type: application/json`. Enquanto ele não existir, o link abre o site, que tem as
mesmas telas.

## Bibliotecas

Só os SDKs da Apple (SwiftUI, Observation, Foundation, SwiftData, Network, Security,
AuthenticationServices, CryptoKit, Swift Testing) e o Google Sign-In (D-055), pelo Swift Package
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
"O login com a Apple ainda não está disponível". No Xcode, ao escolher o *Team*, a assinatura
automática registra a capacidade no App ID.

## Entrar com Google

O botão só aparece quando o build tem o ID OAuth do iOS, que fica em
`Config/Google.xcconfig`, fora do Git (`Config/Piggu.xcconfig` o inclui se existir). Para
criar:

1. No [Google Cloud Console](https://console.cloud.google.com/apis/credentials), no mesmo
   projeto do ID do site, *Criar credenciais → ID do cliente OAuth*.
2. Tipo de aplicativo **iOS**, nome "Piggu iOS", ID do pacote `br.com.piggu.app` (o Team ID e o
   ID da App Store são opcionais). Criar.
3. Copie `Config/Google.exemplo.xcconfig` para `Config/Google.xcconfig` e preencha
   `GOOGLE_IOS_CLIENT_ID` com o *ID do cliente* e `GOOGLE_IOS_URL_SCHEME` com o *esquema de URL
   do iOS* que a tela mostra (o mesmo ID ao contrário, `com.googleusercontent.apps....`).

O servidor não muda: o app pede o token para o ID do site (`GIDServerClientID`, em
`Config/Piggu.xcconfig`), então o `aud` é o mesmo que o backend já aceita em `GOOGLE_CLIENT_ID`.
Não ponha dois IDs separados por vírgula nessa variável: o `application.yml` a lê como um
item só da lista e o login do site para.

No CI o arquivo não existe: o app compila e o botão fica escondido.

## Regras e CI

As regras de cada etapa estão em [`CLAUDE.md`](CLAUDE.md). Todo PR que mexe em `ios-nativo/`
compila e roda os testes num simulador de iPhone (`.github/workflows/ios-nativo.yml`).

## O que já tem

- **Visual do site** (`Core/Design`): as cores do `styles.scss` no claro e no escuro, com os
  temas Chiclete, Rosa Piggu, Menta, Azul e Lilás (Premium), escolhidos em Mais e guardados
  no aparelho, a fonte Sora e os formatos de R$ e data em pt_BR.
- **Liquid Glass** só no que flutua: o `+` de lançar e o seletor de mês (`pigguVidro()`,
  `GrupoDeVidro`, `botaoPrincipal()`). No iOS 17 e 18 vira o material translúcido do sistema;
  com Reduzir Transparência, fundo sólido.
- **Navegação**: abas Painel, Gastos, Relatórios e Mais (`Tab` e `.sidebarAdaptable` no iOS
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
- **Painel**: resumo do mês (`GET /api/reports/month`): sobra, receitas, gastos, projeção,
  comparação com o mês anterior e gastos por categoria, com troca de mês.
- **Gastos**: lista do mês (`GET /api/expenses?mes=`), agrupada por dia, com total.
- **Relatórios**: o mês do relatório do site (gastos, mês anterior, projeção e cada categoria
  contra o mês anterior).
- **Lançar gasto** (botão +): `POST /api/expenses`, valor aceitando vírgula (`12,50`,
  `1.234,56`), categoria vinda de `GET /api/categories`, data e estabelecimento.
- **Mais**: nome, e-mail, plano, e-mail sem confirmar (com reenviar), CPF não informado, Cor do
  Piggu, Termos, Aviso de Privacidade, Excluir minha conta e Sair.
- Textos em pt-BR no String Catalog (`Piggu/Localizable.xcstrings`), valores em R$.
- Testes (Swift Testing): decodificação dos modelos com JSON no formato do backend, parse de
  valor com vírgula, formatos pt_BR, o fluxo 401 → refresh → repetição, o refresh no corpo e
  no Keychain, o cache, os links e o fluxo de entrada inteiro contra a API falsa (Apple e Google
  com conta nova, cadastro, links vencidos, termos, CPF, excluir conta), além das regras de
  senha, e-mail, CPF e nascimento com os mesmos casos do site.

Prints em [`docs/prints/`](docs/prints/) (tirados com `-PigguDemo`, sem dados reais); os da
etapa 2, no iPhone e no iPad, em [`docs/prints/etapa-2/`](docs/prints/etapa-2/); os da etapa 3
(entrada), em [`docs/prints/etapa-3/`](docs/prints/etapa-3/).

## O que falta

- **ID do Google para iOS** e **Universal Links**: os passos acima.
- **Revogar o token da Apple ao excluir a conta**, que a App Store exige: falta no backend
  (`docs/API_SPEC.md`).
- Trocar e criar senha no Perfil (`PUT /api/auth/me/senha`, com o `refreshToken` no corpo).
- **Ligar o Keychain**: trocar `PIGGU_REFRESH_NO_CORPO` para `YES` quando a etapa 1 do backend
  estiver em produção.
- **Relatório do ano** (Premium) e os demais relatórios: etapa 5.
- **Demais telas** do app web: receitas, contas fixas, orçamentos, cofrinho, metas, família,
  assistente, recibos, importação de extrato, o resto do perfil etc.
- Editar e excluir gasto, parcelas, divisão e compartilhamento no lançamento.
- Modo sem rede, notificações, ícone e prints para a App Store.
