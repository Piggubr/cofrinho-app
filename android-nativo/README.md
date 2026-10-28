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
| `app` | `MainActivity`, navegação (Navigation 3) e abas (`NavigationSuiteScaffold`) |
| `core:model` | Tipos do contrato da API, sem Android |
| `core:network` | Retrofit, OkHttp e kotlinx.serialization |
| `core:database` | Room, o cache local |
| `core:datastore` | Preferências do aparelho |
| `core:data` | Repositórios offline-first e sincronização (WorkManager) |
| `core:designsystem` | `PigguTheme`: cores, fonte Sora e ícones |
| `core:ui` | Composables compartilhados (`TelaVazia`) |
| `core:testing` | API falsa e utilidades de teste |
| `feature:login`, `feature:painel`, `feature:gastos` | As telas |
| `baselineprofile` | Gera o perfil de inicialização com um aparelho conectado |

Na etapa A0, `core:model`, `network`, `database`, `datastore`, `data`, `testing` e
`feature:login` só têm as dependências: o código chega nas próximas etapas.

## Tema

- Cores: um esquema Material 3 por variante do perfil (Chiclete, Azul, Lilás e Menta, do
  `frontend/src/styles.scss`), em claro e escuro, gerado por
  [`core/designsystem/gerar-cores.mjs`](core/designsystem/gerar-cores.mjs). A primária é a
  mesma do site. No Android 12 ou mais novo, `PigguTheme(coresDinamicas = true)` usa as
  cores do papel de parede.
- Fonte: Sora variável embutida (SIL Open Font License, [OFL-Sora.txt](core/designsystem/OFL-Sora.txt)),
  em toda a escala de tipografia do Material 3.
- Ícone adaptativo com camada monocromática (ícones temáticos do Android 13), gerado do ícone
  do iOS por [`app/gerar-icone.py`](app/gerar-icone.py).
- Ícones das abas: Material Symbols (Apache 2.0).

## Versões

Só versões estáveis, conferidas em 27/10/2026 no Google Maven, no Maven Central e no
Gradle Plugin Portal ([gradle/libs.versions.toml](gradle/libs.versions.toml)): Gradle 9.8.2,
AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00 (Material 3 1.4.0), Navigation 3 1.2.0,
Hilt 2.60.1, Retrofit 3.0.0, OkHttp 5.5.1, kotlinx.serialization 1.12.0, Room 2.8.5,
DataStore 1.3.0 e WorkManager 2.12.0.

minSdk 26, targetSdk 36 e compileSdk 37: o Compose e o Navigation 3 estáveis pedem
compilar com o SDK 37, e o comportamento continua o do targetSdk 36 (D-089).

## Capturas

[docs/prints/etapa-0](docs/prints/etapa-0): as quatro abas vazias, em claro e escuro,
celular (Pixel 7) e tablet. O CI confere essas imagens a cada PR.
