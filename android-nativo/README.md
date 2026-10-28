# Piggu Android nativo

App Android do Piggu em Kotlin com Jetpack Compose e Material 3. Vai substituir o app
Capacitor de `frontend/android` como versão 2.0.0 do mesmo app da loja (`br.com.piggu.app`,
D-057). As regras de trabalho estão em [CLAUDE.md](CLAUDE.md).

## Rodar

Precisa do JDK 21 e do Android SDK (o Android Studio instala). O Gradle baixa sozinho a
plataforma que faltar.

```sh
./gradlew installDemoDebug       # modo demonstração, com dados de exemplo e sem backend
./gradlew installProducaoDebug   # contra a API de verdade
./gradlew build                  # tudo que o CI roda: variantes, lint e testes
./gradlew verifyRoborazziDemoDebug   # confere as capturas de tela
./gradlew recordRoborazziDemoDebug   # grava as capturas de novo (tela mudou de propósito)
```

## Módulos

| Módulo | O que tem |
|---|---|
| `app` | `MainActivity` (links), `PigguApp` (sessão → login, pendência ou moldura), rotas tipadas de todas as telas do site (`navegacao/`), a moldura com barra, trilho e gaveta, o Mais e o + (`moldura/`) |
| `core:model` | Os tipos do contrato da API (`models.ts` e `docs/api/*.yaml`), sem Android: dinheiro em `BigDecimal`, enum com `DESCONHECIDO` (D-101) |
| `core:network` | Um serviço Retrofit por microserviço, `ErroDaApi` e as mensagens de erro, `User-Agent` e o aviso de atualizar (426) |
| `core:database` | Room: as respostas da API guardadas em JSON por chave (D-103) |
| `core:datastore` | O cofre da sessão (refresh cifrado pelo Android Keystore) e as preferências do aparelho (cor, modo) |
| `core:data` | `Sessao` (login, renovação, sair), as regras da conta (`auth.guard.ts`), o cache offline-first, o monitor de rede |
| `core:designsystem` | `PigguTheme` com as 13 cores do site, Sora e os ícones (`Icone`, Material Symbols) |
| `core:ui` | Formatos em pt-BR, campo de valor, campo de categoria, seletor de mês, chips de visão, estados (esqueleto, vazio, erro), telas de 404 e 500, o porquinho e o Custom Tab do site |
| `core:testing` | `ApiDaDemo`: a API falsa do modo demonstração (porte de `api-da-demo.ts`), usada também nos testes |
| `feature:login` | Entrar por e-mail e senha (o resto da entrada chega na A3) |
| `feature:painel`, `feature:gastos` | As abas, por enquanto com o link para o site (chegam na A4) |
| `baselineprofile` | Gera o perfil de inicialização com um aparelho conectado |

## Sessão e rede

- Login com `refreshNoCorpo: true` (D-072). O access token fica só na memória; com "Continuar
  conectado", o refresh vai cifrado para o DataStore junto com o perfil, e o app abre logado na
  hora, renovando por trás (D-100).
- Um 401 renova uma vez, e as chamadas ao mesmo tempo esperam a mesma renovação. Só a
  renovação recusada (401 ou 403) leva de volta ao login.
- Tela que ainda não é nativa abre a página do site num Custom Tab, com o ícone de "abre fora"
  (D-099). Os links `piggu://gasto?valor=12.50`, `piggu://gastos?id=...&mes=aaaa-mm` e
  `piggu://<rota do site>` funcionam; os App Links de `https://piggu.com.br` só cobrem as
  telas nativas.
- Variante `demo`: a `ApiDaDemo` responde tudo, com qualquer e-mail e senha (a conta da Ana).

## Tema

- Cores: as 13 do site (`frontend/src/app/core/ui/tema.ts`), em claro e escuro, geradas por
  [`core/designsystem/gerar-cores.mjs`](core/designsystem/gerar-cores.mjs). Chiclete, Rosa
  Piggu, Menta e Azul são grátis; as outras, do Premium, voltam ao Chiclete quando o Premium
  acaba. Modo Automático, Claro ou Escuro, e as Cores do celular no Android 12 ou mais novo.
- Fonte: Sora variável embutida (SIL Open Font License, [OFL-Sora.txt](core/designsystem/OFL-Sora.txt)).
- Ícones: Material Symbols Outlined (Apache 2.0), gerados por
  [`core/designsystem/gerar-icones.mjs`](core/designsystem/gerar-icones.mjs) com as chaves de `icone.ts`.
- Ícone adaptativo com camada monocromática, gerado do ícone do iOS por [`app/gerar-icone.py`](app/gerar-icone.py).

## Versões

Só versões estáveis, conferidas em 27/10/2026 no Google Maven, no Maven Central e no
Gradle Plugin Portal ([gradle/libs.versions.toml](gradle/libs.versions.toml)): Gradle 9.8.2,
AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00 (Material 3 1.4.0), Navigation 3 1.2.0,
Hilt 2.60.1, Retrofit 3.0.0, OkHttp 5.5.1, kotlinx.serialization 1.12.0, Room 2.8.5,
DataStore 1.3.0 e WorkManager 2.12.0.

minSdk 26, targetSdk 36 e compileSdk 37: o Compose e o Navigation 3 estáveis pedem
compilar com o SDK 37, e o comportamento continua o do targetSdk 36 (D-089).

## Capturas

- [docs/prints/etapa-2](docs/prints/etapa-2): login, Painel com a moldura (barra no celular,
  gaveta no tablet), Mais, a folha do +, 404, 500 e os componentes comuns, em claro e escuro,
  celular (Pixel 7) e tablet. O CI confere essas imagens a cada PR.
- [docs/prints/etapa-0](docs/prints/etapa-0): as abas vazias da A0, só como registro.
