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
| `app` | `MainActivity` (links), `PigguApp` (sessão → login, pendência ou moldura), rotas tipadas de todas as telas do site (`navegacao/`), a moldura com barra, trilho e gaveta, o Mais, o + e o tour em balões (`moldura/`), a trava do app (`Trava.kt`) |
| `core:model` | Os tipos do contrato da API (`models.ts` e `docs/api/*.yaml`), sem Android: dinheiro em `BigDecimal`, enum com `DESCONHECIDO` (D-101) |
| `core:network` | Um serviço Retrofit por microserviço, `ErroDaApi` e as mensagens de erro, `User-Agent` e o aviso de atualizar (426) |
| `core:database` | Room: as respostas da API guardadas em JSON por chave (D-103) |
| `core:datastore` | O cofre da sessão (refresh cifrado pelo Android Keystore) e as preferências do aparelho (cor, modo, tour visto, trava e esconder nos recentes) |
| `core:data` | `Sessao` (login, renovação, sair), as regras da conta (`auth.guard.ts`), o cache offline-first, o monitor de rede |
| `core:designsystem` | `PigguTheme` com as 13 cores do site, Sora e os ícones (`Icone`, Material Symbols) |
| `core:ui` | Formatos em pt-BR, campo de valor, campo de categoria, seletor de mês, chips de visão, estados (esqueleto, vazio, erro), telas de 404 e 500, o porquinho e o Custom Tab do site |
| `core:testing` | `ApiDaDemo`: a API falsa do modo demonstração (porte de `api-da-demo.ts`), usada também nos testes |
| `feature:login` | A entrada: entrar (e-mail e senha, Google), criar conta, aceite, "Sobre você", CPF, confirmar e-mail, esqueci e redefinir a senha, excluir a conta |
| `feature:legal` | Termos de Uso e Aviso de Privacidade nativos, com o texto do site (`scripts/textos-legais.py`) |
| `feature:painel` | O Painel: resumo, meta do mês, categorias, avisos, cofrinho, objetivos, metas, Girls Math, notas e bancos, com organizar os cartões |
| `feature:gastos` | A lista do mês (busca, filtros, editar, apagar com Desfazer, divisão, regras, importar e exportar) e o formulário de gasto, com a fila sem internet |
| `feature:receitas` | Receitas do mês, "Entram todo mês", "A receber" e o formulário de receita |
| `feature:assistente` | A busca global e o assistente de IA do topo |
| `feature:contasfixas` | Contas do mês: vencidas, a vencer e pagas, nova conta fixa, marcar como paga e receitas fixas |
| `feature:orcamentos` | Orçamento: o limite do mês e os limites por categoria (Premium) |
| `feature:calendario` | Calendário: a grade do mês, as notas e o gráfico acumulado |
| `feature:relatorios` | Relatórios: o mês por categoria, o ano (Premium) e o CSV do mês |
| `feature:contas` | Cartões e bancos: contas, cartões, a fatura e os bancos conectados |
| `feature:metas` | Minhas metas do mês, com alvo, progresso e o +1 |
| `feature:girlsmath` | Girls Math: o que a pessoa não comprou, com foto, e quanto ficou no bolso |
| `feature:planejar` | Planejar: os atalhos para o que vem pela frente |
| `feature:premios` | Prêmios: as Fofocoins, os prêmios e os resgates |
| `feature:familia` | Família: pessoas, convites, parceria e o histórico de mudanças |
| `feature:plano` | Premium: o plano, os preços, o cancelamento e a assinatura individual pelo Google Play Billing (D-132) |
| `feature:perfil` | Perfil: foto e nome, moeda, avisos, aparência, ícone do app (D-119), módulos, segurança (travar com a biometria, esconder nos recentes), senha, privacidade e excluir a conta |
| `feature:listas` | Compras (com o leitor de código de barras), Lugares (abre no app de mapas) e Filmes |
| `feature:fotos` | O mural de fotos do mês (Premium) |
| `widget` | Os widgets do Glance (gasto do mês, meta do mês, lançar gasto) e o bloco "Lançar gasto" das Configurações rápidas, lendo só o resumo que o app grava (D-129) |
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

## Entrada

- Sem sessão, o app abre no login (Entrar e Criar conta na mesma tela, como no site). Os
  links do e-mail (`/confirmar-email`, `/redefinir-senha`) abrem as telas nativas pelos App
  Links, quando o `assetlinks.json` estiver no ar.
- Com os Termos novos ou o cadastro incompleto, o app só abre o aceite ou o "Sobre você"
  (`auth.guard.ts`). Um 403 `CADASTRO_PENDENTE` leva ao "Sobre você".
- Google: o botão só aparece com `./gradlew -Ppiggu.googleNoAndroid=true ...` (ou a linha em
  `gradle.properties`), depois de criar o ID OAuth do Android no Google Cloud (D-104).
- Termos e Aviso: `python3 -I scripts/textos-legais.py` copia o texto do site; o CI confere.

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
DataStore 1.3.0 e WorkManager 2.12.0. Na A7, conferidas em 28/10/2026: Coil 3.6.3, Glance 1.2.0,
Biometric 1.1.0, o leitor de código de barras 16.1.0 e o recorte de documentos 16.0.0 do Google
Play services. Na A8, conferida em 28/10/2026: Google Play Billing Library 9.1.0 (`billing-ktx`).

## Recursos do Android

- Widgets e atalhos: Gasto do mês, Meta do mês e Lançar gasto; os atalhos "Lançar gasto" e "Ver
  gastos" no ícone e o bloco das Configurações rápidas (D-129).
- Trava com a biometria ou o bloqueio de tela, ao abrir e depois de 1 minuto fora, e "Esconder
  nos apps recentes" (D-131).
- Imagens pelo Coil; só as do Piggu levam o token (D-127).
- Mapa: sem chave do Google Maps, "Abrir no mapa" usa o app de mapas do aparelho (D-125). Para pôr
  o mapa no app: ative o Maps SDK for Android no Google Cloud, crie uma chave restrita ao pacote
  `br.com.piggu.app` (SHA-1 da chave de assinatura do Play e a de debug) e só a essa API.
- Push pelo Firebase: espera a A1 (backend) entrar na `feature/android-nativo`.
- Premium pelo Google Play (D-132): assinaturas `premium_mensal` e `premium_anual` no Play Console,
  cada uma com um plano básico (e, se quiser, uma oferta de teste grátis). A compra leva o id da
  conta Piggu em `obfuscatedAccountId` e vai a `POST /api/billing/google-play/purchases`, que faz o
  acknowledge. Na variante demo a loja é de mentira (`LojaDeMentira`). A compra de verdade só
  funciona com o app publicado numa faixa de teste do Play Console.
- Idioma: só pt-BR, com o `locales_config` pronto para o idioma por app.

minSdk 26, targetSdk 36 e compileSdk 37: o Compose e o Navigation 3 estáveis pedem
compilar com o SDK 37, e o comportamento continua o do targetSdk 36 (D-089).

## Lançamento (A9)

- **Versão:** 2.0.0, `versionCode` 200, acima do 1 do app Capacitor (o único APK já distribuído), como o mesmo app `br.com.piggu.app` das lojas (D-057).
- **Assinatura:** a chave de upload vem de `PIGGU_UPLOAD_KEYSTORE` (caminho do .jks), `PIGGU_UPLOAD_KEYSTORE_SENHA`, `PIGGU_UPLOAD_KEY_ALIAS` e `PIGGU_UPLOAD_KEY_SENHA`. Nunca entra no repositório (`*.jks` e `*.keystore` no .gitignore). No CI, os secrets de mesmo nome (o .jks em base64 em `PIGGU_UPLOAD_KEYSTORE_BASE64`); sem eles, o AAB sai sem assinatura.
- **AAB:** o job "AAB de produção" do workflow `android-nativo.yml` guarda o `app-producao-release.aab` como artefato. O envio ao Play Console é manual, com a confirmação do Dudu.
- **R8 e Baseline Profile:** a release usa R8 e o perfil de `app/src/main/generated/baselineProfiles`, gerado por `./gradlew :app:generateBaselineProfile` com um emulador do Android 13 ou mais novo (entra na demo, abre o Painel e as abas).
- **Abertura:** num emulador simples (API 26, 2 GB de RAM), com o perfil compilado como o Play compila, o primeiro quadro sai em ~250 ms e o Painel com os números em ~500 ms (mediana de 8 aberturas a frio, `am start -S -W` e o "Fully drawn" do `ReportDrawnWhen`). Sem o perfil compilado (APK instalado à mão), o Painel leva ~1,7 s.

### Teste interno no Play Console

1. Play Console > o app Piggu > Testar e lançar > Teste > **Teste interno** > Criar nova versão.
2. Envie o `app-producao-release.aab` do artefato do CI (assinado com a chave de upload). Na primeira vez, aceite a assinatura de apps do Google Play.
3. Nome da versão "2.0.0 (200)" e as notas; Salvar > Revisar versão > Iniciar o lançamento no teste interno.
4. Em **Testadores**, crie a lista (até 100 e-mails) e copie o link de participação. Quem aceitar instala pela Play Store, em minutos.
5. Para comprar o Premium sem cobrança, ponha os mesmos e-mails em Configurações > **Teste de licença**.

## Capturas

- [docs/prints/etapa-8](docs/prints/etapa-8): Premium com o "Restaurar compras" (a demonstração
  já é Premium pelo site, então não mostra a compra).
- [docs/prints/etapa-7](docs/prints/etapa-7): Compras, Lugares, Filmes, Fotos e o tour (o
  balão do +).
- [docs/prints/etapa-6](docs/prints/etapa-6): Minhas metas, Girls Math, Planejar, Prêmios,
  Família, Premium e Perfil.
- [docs/prints/etapa-5](docs/prints/etapa-5): Relatórios, Contas do mês, Orçamento,
  Calendário (lado a lado no tablet) e Cartões e bancos.
- [docs/prints/etapa-4](docs/prints/etapa-4): Painel, Gastos, Receitas, lançar gasto, lançar
  receita, a busca e o assistente, com os dados da demonstração (o Hilt de verdade, D-112).
- [docs/prints/etapa-3](docs/prints/etapa-3): entrar (com e sem o Google), criar conta,
  aceite do Google, conta criada, esqueci a senha, senha nova, e-mail confirmado, aceite dos
  termos, excluir a conta (só tablet), Sobre você, CPF e os Termos.
- [docs/prints/etapa-2](docs/prints/etapa-2): login, Painel com a moldura (barra no celular,
  gaveta no tablet), Mais, a folha do +, 404, 500 e os componentes comuns, em claro e escuro,
  celular (Pixel 7) e tablet. O CI confere essas imagens a cada PR.
- [docs/prints/etapa-0](docs/prints/etapa-0): as abas vazias da A0, só como registro.
