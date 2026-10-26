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
| `-PigguAba gastos` / `mais` | Abre direto numa aba. |
| `-PigguLancar YES` | Abre direto o lançamento de gasto. |

A demonstração e esses atalhos só existem em Debug; não vão para o build de Release.

## Bibliotecas

Só os SDKs da Apple (SwiftUI, Observation, Foundation, SwiftData, Swift Testing) e, quando
chegar o login do Google, o Google Sign-In (D-055). Nada além disso sem uma decisão nova.

## Regras e CI

As regras de cada etapa estão em [`CLAUDE.md`](CLAUDE.md). Todo PR que mexe em `ios-nativo/`
compila e roda os testes num simulador de iPhone (`.github/workflows/ios-nativo.yml`).

## O que já tem

- **Login por e-mail e senha** (`POST /api/auth/entrar`) com "Continuar conectado".
- **Sessão como no app web**: o access token fica só em memória; o refresh vem no cookie
  HttpOnly (`Path=/api/auth`, `SameSite=Strict`) e fica no `HTTPCookieStorage` do sistema.
  Em 401 o app faz uma única renovação (`POST /api/auth/refresh`) e repete o pedido; chamadas
  simultâneas esperam a mesma renovação. Ao abrir o app, a sessão é recuperada pelo cookie.
  Sair chama `POST /api/auth/logout` e apaga o cookie.
- **Painel**: resumo do mês (`GET /api/reports/month`): sobra, receitas, gastos, projeção,
  comparação com o mês anterior e gastos por categoria, com troca de mês.
- **Gastos**: lista do mês (`GET /api/expenses?mes=`), agrupada por dia, com total.
- **Lançar gasto** (botão +): `POST /api/expenses`, valor aceitando vírgula (`12,50`,
  `1.234,56`), categoria vinda de `GET /api/categories`, data e estabelecimento.
- **Mais**: nome, e-mail, plano e Sair.
- Cor principal `#d1006b` (claro) / `#ff5cae` (escuro), textos em português, valores em R$.
- Testes (Swift Testing): decodificação dos modelos com JSON no formato do backend, parse de
  valor com vírgula, formatos pt_BR e o fluxo 401 → refresh → repetição.

Prints em [`docs/prints/`](docs/prints/) (tirados com `-PigguDemo`, sem dados reais).

## O que falta

- **Entrar com Google**: os botões estão na tela, desabilitados. Falta o Google Sign-In e o
  `POST /api/auth/google` com o ID token.
- **Entrar com Apple**: botão desabilitado; o backend ainda não tem a rota.
- **Keychain para o refresh**: hoje o cookie fica no armazenamento de cookies do app (protegido
  pelo sandbox do iOS). O ideal é o backend aceitar o refresh no corpo para clientes nativos e
  o app guardar no Keychain.
- **Demais telas** do app web: receitas, contas fixas, orçamentos, cofrinho, metas, família,
  assistente, recibos, importação de extrato, perfil, cadastro, esqueci a senha, termos etc.
- Editar e excluir gasto, parcelas, divisão e compartilhamento no lançamento.
- Modo sem rede, notificações, ícone e prints para a App Store.
