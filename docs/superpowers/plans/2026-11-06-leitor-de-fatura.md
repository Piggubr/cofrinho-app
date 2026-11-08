# Conferir a fatura do cartão — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A pessoa envia o PDF da fatura e o Piggu diz quais compras já foram lançadas e quais faltam; no Premium, adiciona as que faltam.

**Architecture:** No `piggu-finance`: PDFBox extrai o texto, `LeitorDeFatura` (regras) transforma o texto em linhas e confere a soma com o total; se não fechar e a pessoa for Premium, `GeminiFaturaReader` lê o texto. `ConciliacaoDaFatura` compara as linhas com os gastos da família. `ConferenciaDaFaturaService` junta tudo, aplica o plano e o consentimento. A gravação usa o `POST /expenses/import` que já existe, que passa a aceitar parcela. Site, iOS e Android ganham uma folha nova seguindo a importação de extrato de cada um.

**Tech Stack:** Java 21 / Spring Boot / Apache PDFBox 3.0.x (Apache 2.0); Angular (signals, `$localize`); SwiftUI (`fileImporter`, String Catalog); Kotlin/Compose (`OpenDocument`, `strings.xml` por módulo).

**Spec:** `docs/superpowers/specs/2026-11-06-leitor-de-fatura-design.md` (inclui "Decisões depois da revisão").

## Global Constraints

- A API só acrescenta campos (D-059). Mudou rota ou DTO: `cd backend && ./scripts/regravar-contrato.sh` no mesmo PR.
- PDF até **5 MB**; o `pdfBase64` aceita até 7 000 000 caracteres. PDF e texto nunca são gravados nem logados.
- Tolerância de "fecha": **R$ 0,05**. Datas: **±3 dias** para "já lançada"; **4 a 10 dias** ou empate: "Confira". Parcela: mesmo `n/m`, valor a até **R$ 0,01**, data no período ±3.
- Gratuito: a resposta **não traz `linhas`** (o servidor barra) e não há Gemini; não gasta cota. Premium: sem limite.
- Consentimento: finalidade **`GEMINI_FATURA`**, versão `Consentimentos.VERSAO_DO_AVISO` (não sobe).
- Erros: PDF com senha faltando ou errada → 422 código **`PDF_COM_SENHA`**; não fechou no gratuito → 422 `Plano.CODIGO_PREMIUM` (`PLANO_PREMIUM`); sem consentimento → o código que `Consentimentos.exigir` já usa (`CONSENTIMENTO_NECESSARIO`).
- Textos em português do Brasil; no Android nada fixo no código (strings.xml do módulo).
- Cópia fixa: gratuito sem leitura → `Não consegui ler esta fatura sozinho. A leitura pela IA faz parte do Piggu Premium.`; aviso do consentimento → `Para ler esta fatura, o Piggu envia o texto dela (não o PDF) para a IA do Google (Gemini). Autorize o envio para continuar.`

## Review Focus

1. Mesma compra duas vezes no mesmo dia e valor (dois cafés de R$ 6,00): cada linha casa com um gasto diferente; se só um foi lançado, a outra fica "Falta". Teste em Task 3.
2. Fatura com "saldo anterior" e "pagamento recebido" antes das compras: o total que vale é o da fatura do mês; pagamento é `IGNORADA` e não entra na soma. Teste em Task 1.
3. Compra internacional com linha de valor em dólar e a linha em reais, mais o IOF: só o valor em reais vira compra; IOF vira `TARIFA`. Teste em Task 1.
4. Gasto lançado à mão com valor diferente em centavos (R$ 45,90 lançado, R$ 45,99 na fatura): não é "já lançada"; vai para "Falta" (o valor é exato fora de parcela). Teste em Task 3.
5. Ler a mesma fatura duas vezes depois de importar: tudo "Já lançada" pelo `idExterno`, nada duplica. Teste em Task 5.

---

### Task 1: `LeitorDeFatura` (texto → linhas, sem IA)

**Files:**
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/integration/LeitorDeFatura.java`
- Create: `backend/piggu-finance/src/test/resources/faturas/{nubank,itau,inter,bradesco,c6}.txt` (sintéticos, no formato do PDFBox: uma linha da fatura por linha de texto)
- Test: `backend/piggu-finance/src/test/java/com/piggu/finance/integration/LeitorDeFaturaTest.java`

**Interfaces:**
- Produces:
  - `enum TipoDaLinha { COMPRA, TARIFA, IGNORADA }` (dentro de `LeitorDeFatura`)
  - `record Linha(LocalDate data, String descricao, BigDecimal valor, TipoDaLinha tipo, Integer parcelaNumero, Integer parcelaTotal, String idExterno)` — `valor` sempre positivo; crédito/estorno/pagamento ficam `IGNORADA`; `descricao` sem o sufixo `NN/MM`.
  - `record Leitura(String banco, LocalDate vencimento, BigDecimal total, List<Linha> linhas)` com `BigDecimal soma()` (COMPRA + TARIFA − créditos e estornos que estejam no período) e `boolean fecha()` (linhas não vazias, total e vencimento não nulos, |soma − total| ≤ 0,05).
  - `static Leitura ler(String texto)`.
- `idExterno` = `"fatura:" + sha256(data|descricao|valor|n/m|ocorrência)` truncado a 32, como `LeitorDeExtrato.comIdsGerados`.

- [ ] **Step 1: Escrever os 5 fixtures**, cada um com: cabeçalho (nome do banco, titular fictício), "Vencimento dd/mm/aaaa", "Total desta fatura R$ x" (Nubank/Inter) ou "Total da fatura" / "Valor total" (os outros), "Saldo anterior" e "Pagamento recebido" (em pelo menos dois), 5–10 compras `dd/mm DESCRIÇÃO valor`, ao menos uma parcela `LOJA X 03/10`, um estorno, uma compra internacional com IOF (em pelo menos um), uma compra de dezembro numa fatura com vencimento em janeiro (em pelo menos um).

- [ ] **Step 2: Escrever os testes**
  - `nubankFecha` (e um por banco): `fecha()` verdadeiro; número de `COMPRA`, `TARIFA`, `IGNORADA` igual ao do fixture; `vencimento` certo.
  - `parcelaSaiDaDescricao`: linha `LOJA X 03/10 150,00` → `descricao` "LOJA X", `parcelaNumero` 3, `parcelaTotal` 10.
  - `viradaDeAno`: vencimento 10/01/2027, linha `28/12` → `data` 2026-12-28.
  - `pagamentoESaldoAnteriorNaoContam` (Review Focus 2).
  - `internacionalComIof` (Review Focus 3): uma `COMPRA` em reais e uma `TARIFA` "IOF".
  - `semTotalNaoFecha`, `textoVazioNaoFecha`.
  - `mesmaCompraDuasVezesTemIdsDiferentes`.

- [ ] **Step 3: Rodar e ver falhar** — `cd backend && ./mvnw -q -pl piggu-finance test -Dtest=LeitorDeFaturaTest` → FAIL (classe não existe).

- [ ] **Step 4: Implementar `LeitorDeFatura`**, no estilo do `LeitorDeCupom` (regex âncorada, sem backtracking, `Texto.semAcento` para comparar). Palavras de `TARIFA`: iof, anuidade, juros, multa, encargo, tarifa, seguro. Palavras de `IGNORADA`: pagamento, pagto, credito, estorno, saldo anterior, devolucao. O total: a primeira linha que bate `total (desta|da) fatura|valor total`; se o texto tiver "saldo anterior" e só "total a pagar", não fecha. `banco` = primeiro nome conhecido (nubank, itau, inter, bradesco, c6, santander, caixa, banco do brasil) achado no texto, senão "desconhecido".

- [ ] **Step 5: Rodar e ver passar.** Mesmo comando → PASS.

- [ ] **Step 6: Commit** — `Fatura: leitor próprio do texto da fatura`

### Task 2: Extrair o texto do PDF

**Files:**
- Modify: `backend/piggu-finance/pom.xml` (`org.apache.pdfbox:pdfbox:3.0.x`, a versão estável mais nova que tenha ao menos duas semanas)
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/integration/TextoDoPdf.java`
- Test: `backend/piggu-finance/src/test/java/com/piggu/finance/integration/TextoDoPdfTest.java`

**Interfaces:**
- Produces: `static String extrair(byte[] pdf, String senha)` (`senha` pode ser nula). Lança `BusinessException` 422 com código `PDF_COM_SENHA` quando está cifrado e a senha falta ou está errada; `BusinessException` "O arquivo não parece um PDF." quando não abre. PDF só com imagem devolve texto vazio. Usa `Loader.loadPDF(bytes, senha)` e `PDFTextStripper` com `setSortByPosition(true)`.

- [ ] **Step 1: Testes** — gerar os PDFs no próprio teste com o PDFBox: `extraiOTexto` (PDF com "Total da fatura R$ 10,00" → o texto contém isso), `comSenhaSemSenhaPede` (código `PDF_COM_SENHA`), `comSenhaCerta`, `senhaErrada` (código `PDF_COM_SENHA`), `naoEPdf` (bytes "oi"), `soImagemDaVazio`.
- [ ] **Step 2: Rodar e ver falhar.**
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar e ver passar** — `./mvnw -q -pl piggu-finance test -Dtest=TextoDoPdfTest`.
- [ ] **Step 5: Commit** — `Fatura: texto do PDF pelo PDFBox (com senha)`

### Task 3: Conciliação

**Files:**
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/domain/ConciliacaoDaFatura.java`
- Test: `backend/piggu-finance/src/test/java/com/piggu/finance/domain/ConciliacaoDaFaturaTest.java`

**Interfaces:**
- Consumes: `LeitorDeFatura.Linha`, `Expense` (getters `getId`, `getExpenseDate`, `getAmount`, `getItem`, `getAccountId`, `getExternalId`, `getInstallmentNumber`, `getInstallmentCount`).
- Produces:
  - `enum Situacao { JA_LANCADA, CONFIRA, FALTA }`
  - `record Resultado(LeitorDeFatura.Linha linha, Situacao situacao, Expense gasto)` (`gasto` nulo em `FALTA`; linhas `IGNORADA` não entram).
  - `static List<Resultado> conciliar(List<LeitorDeFatura.Linha> linhas, List<Expense> candidatos, UUID contaId, LocalDate inicioDoPeriodo, LocalDate fimDoPeriodo)` — função pura; cada gasto é usado no máximo uma vez.
- Algoritmo, na ordem (cada passo só vê os gastos ainda livres): (1) `externalId` igual; (2) parcelas `n/m` contra gastos com o mesmo `n/m`, |valor| ≤ 0,01, data em [início−3, fim+3]; (3) valor exato e |dias| ≤ 3, desempate: mesma conta, menor |dias|, descrição mais parecida (tokens em comum sem acento); (4) valor exato com 4 ≤ |dias| ≤ 10, ou empate no passo 3 → `CONFIRA` (sem consumir o gasto no empate); o resto → `FALTA`.

- [ ] **Step 1: Testes** — `valorIgualDoisDiasJaLancada`; `doisCandidatosGanhaOMesmoCartao`; `empateVaiParaConfira`; `seisDiasConfira`; `parcelaComUmCentavoDeDiferenca`; `parcelaForaDoPeriodoFalta`; `externalIdJaImportado`; `mesmoGastoNaoServeDuasVezes` (Review Focus 1); `centavosDiferentesFalta` (Review Focus 4).
- [ ] **Step 2: Rodar e ver falhar. Step 3: Implementar. Step 4: Rodar e ver passar.**
- [ ] **Step 5: Commit** — `Fatura: conciliação com os gastos lançados`

### Task 4: Reserva pelo Gemini (texto)

**Files:**
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/integration/GeminiFaturaReader.java`
- Test: `backend/piggu-finance/src/test/java/com/piggu/finance/integration/GeminiFaturaReaderTest.java` (com `MockRestServiceServer`, como `GeminiReceiptReaderTest`)

**Interfaces:**
- Consumes: `IntegracoesProperties.gemini()` (mesma chave, modelo e URL do `GeminiReceiptReader`), `LeitorDeFatura`.
- Produces: `boolean habilitado()`, `LeitorDeFatura.Leitura ler(String texto)` — manda **só o texto** como `parts[{text: prompt}, {text: textoDaFatura}]`, com `responseSchema` de `{banco, vencimento, total, linhas[{data, descricao, valor, tipo, parcelaNumero, parcelaTotal}]}`; os `idExterno` são gerados como no `LeitorDeFatura` (reaproveitar o método de lá). Erros traduzidos como no `GeminiReceiptReader.traduzirErro` (reaproveitar; se for privado, torná-lo de pacote).
- [ ] **Step 1: Testes** — `leRespostaDoGemini` (JSON fixo → linhas e total), `mandaSoTexto` (o corpo enviado não tem `inline_data`), `erroDoGeminiViraMensagem`.
- [ ] **Step 2–4: falhar, implementar, passar.**
- [ ] **Step 5: Commit** — `Fatura: reserva pelo Gemini lendo só o texto`

### Task 5: Rota, plano, consentimento e importar com parcela

**Files:**
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/domain/ConferenciaDaFaturaService.java`
- Create: `backend/piggu-finance/src/main/java/com/piggu/finance/api/dto/ConferirFaturaRequest.java`, `ConferenciaDaFaturaResponse.java`
- Modify: `backend/piggu-finance/src/main/java/com/piggu/finance/api/ExpenseController.java` (rota nova; `LinhaRequest` ganha `parcelaNumero`, `parcelaTotal` opcionais, `@Min(1) @Max(48)`)
- Modify: `backend/piggu-finance/src/main/java/com/piggu/finance/domain/ExpenseService.java` (`LinhaImportada` ganha os dois campos; `importar` grava as parcelas `n..m`)
- Modify: `ExpenseRepository` se faltar a consulta por período (`findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc` já existe)
- Test: `backend/piggu-finance/src/test/java/com/piggu/finance/api/ConferenciaDaFaturaTest.java` (no estilo do `ImportacaoDeExtratoTest`), mais casos novos no `ImportacaoDeExtratoTest`

**Interfaces:**
- Rota: `POST /api/expenses/import/card-statement`, corpo `ConferirFaturaRequest(String pdfBase64 [@NotBlank, @Size(max=7_000_000)], String senha [@Size(max=100)], UUID contaId, Boolean autorizoIa, String versaoDoAviso)`.
- Resposta `ConferenciaDaFaturaResponse(String banco, LocalDate vencimento, BigDecimal total, BigDecimal somaLida, String origem /* PROPRIA|GEMINI */, String aviso, Contagem contagem, List<Linha> linhas /* null no gratuito */)`; `Contagem(int compras, int tarifas, int ignoradas, int jaLancadas, int confira, int faltam)`; `Linha(LocalDate data, String descricao, BigDecimal valor, String tipo, Integer parcelaNumero, Integer parcelaTotal, String situacao, UUID gastoId, String gastoDescricao, LocalDate gastoData, String categoria, String idExterno)`.
- Período: com `contaId` de cartão, o da `ContasECartoes.Fatura` do mês do vencimento (`inicio` a `ultimaCompra`); sem cartão, da menor à maior data das compras à vista lidas.
- Categoria: a mesma da prévia (`ExpenseService.previa` → reaproveitar `categoriaDe` com `ORIGEM_EXTRATO`).
- Importar parcela: com `parcelaNumero = n` e `parcelaTotal = m`, grava `m − n + 1` gastos, o k-ésimo com `dataDaParcela(data, n − 1 + k, conta)`, `parcela(n + k, m)`, mesmo `receiptId` (o lote), `idExterno` da primeira igual ao da linha e das outras `idExterno + ":" + (n + k)`. A resposta `importados` conta as linhas, não as parcelas.
- Fluxo do serviço: decodifica (base64 inválido → "O PDF chegou corrompido. Tente de novo."), > 5 MB → "PDF grande demais (máximo 5 MB).", `TextoDoPdf.extrair`, `LeitorDeFatura.ler`; não fechou → gratuito: 422 `PLANO_PREMIUM` com a cópia fixa (se houver linhas, devolve a contagem parcial com `aviso` em vez do erro); Premium e Gemini habilitado: `consentimentos.exigir(usuario, "GEMINI_FATURA", <cópia fixa>, autorizoIa, versaoDoAviso)` e `GeminiFaturaReader.ler`; Gemini falhou com leitura parcial → devolve a parcial com `aviso`. Concilia, monta a resposta, `linhas = null` se `!usuario.isPremium()`. Log: banco, número de linhas, origem, fecha — nada do conteúdo. Sem transação em volta do Gemini (como o `LeitorDeRecibos`).
- [ ] **Step 1: Testes** — `gratuitoSoContagem` (`linhas` ausente/nulo no JSON); `premiumComLinhas`; `naoFechouGratuito422Premium`; `premiumPedeConsentimento` (sem `autorizoIa` → código de consentimento; Gemini não chamado); `pdfComSenha422`; `grandeDemais`; `importarParcelaGravaAsSeguintes` (n=3, m=5 → 3 gastos 3/5, 4/5, 5/5); `lerDeNovoDepoisDeImportarTudoJaLancada` (Review Focus 5); `isolamentoEntreFamilias` (o gasto de outra família nunca casa).
- [ ] **Step 2–4: falhar, implementar, passar.**
- [ ] **Step 5: Contrato e docs** — `cd backend && ./scripts/regravar-contrato.sh`; conferir com `git diff docs/api/` que só acrescentou; `docs/API_SPEC.md` (rota nova e campos de parcela no import).
- [ ] **Step 6: Suíte do finance** — `./mvnw -q -pl piggu-finance verify` → BUILD SUCCESS.
- [ ] **Step 7: Commit** — `Fatura: rota de conferência, plano e importar parcelas`

### Task 6: Site

**Files:**
- Create: `frontend/src/app/features/expenses/conferir-fatura.ts` (componente `ConferirFatura`, selector `app-conferir-fatura`, template inline como `importar-extrato.ts`) e `conferir-fatura.spec.ts`
- Modify: `frontend/src/app/core/api/finance.service.ts` (`conferirFatura(pdfBase64, senha, contaId, versaoDoAviso?)`; `importarExtrato` aceita os campos de parcela), `frontend/src/app/core/api/models.ts` (tipos da resposta), `frontend/src/app/features/expenses/expenses.html` (botão ao lado do importar extrato), `frontend/src/app/features/accounts/accounts.html` e `accounts.ts` (na seção da fatura aberta, "Conferir com a fatura do banco" com o cartão), `frontend/src/app/demo/api-da-demo.ts` (rota com resposta de exemplo: Premium com os 4 grupos), `frontend/src/app/core/api/contrato-da-api.spec.ts` se ele listar rotas, `frontend/src/locale/messages.xlf` (`npm run i18n`)

**Interfaces:** consome a rota e os tipos da Task 5. Consentimento com `pedeConsentimento`/`VERSAO_DO_AVISO` de `core/privacidade/aviso.ts`; `PLANO_PREMIUM` por `codigoDoErro` → convite ao Premium (link para a página de planos que o site já usa); `PDF_COM_SENHA` → campo de senha e "Tentar de novo".

- [ ] **Step 1: Testes (`conferir-fatura.spec.ts`)** — gratuito mostra "41 já lançadas, faltam 6" e o convite, sem lista; Premium mostra os grupos com "Faltam" marcadas e "Confira"/"Tarifas" desmarcadas; "Adicionar N" chama `importarExtrato` só com as marcadas, com `parcelaNumero`/`parcelaTotal`; `PDF_COM_SENHA` mostra o campo de senha; consentimento pede e reenvia com a versão; arquivo > 5 MB nem é enviado. Depois de adicionar com cartão, aparece "Marcar fatura como paga" e chama `pagarFatura(id, mes)`.
- [ ] **Step 2–4: falhar, implementar, passar** — `cd frontend && npm test -- --watch=false`; `npm run lint`.
- [ ] **Step 5: Commit** — `Site: conferir a fatura do cartão`

### Task 7: iOS

**Files:**
- Create: `ios-nativo/Piggu/Telas/Gastos/ConferirFaturaView.swift`, `ios-nativo/PigguTests/ConferirFaturaTests.swift`
- Modify: `ios-nativo/Piggu/Core/API/Financas.swift` e `Modelos.swift` (chamada e modelos; `PedidoDeImportacao` com parcela), `ios-nativo/Piggu/Telas/GastosView.swift` (abre a folha), `ios-nativo/Piggu/Telas/Organizacao/ContasECartoesView.swift` (`FaturaView` ganha "Conferir com a fatura do banco"), `ios-nativo/Piggu/Core/API/TransporteDeDemonstracao.swift` (rota de demo), `ios-nativo/Piggu/Localizable.xcstrings`

**Interfaces:** mesma folha e comportamento do site. `.fileImporter(allowedContentTypes: [.pdf])`, limite 5 MB. Consentimento como em `AssistenteView.swift:263-292` (`autorizoIa = true`, `versaoDoAviso = Conta.versaoDosTermos`, código `CONSENTIMENTO_NECESSARIO`); `PLANO_PREMIUM` abre o convite do Premium que o app já usa (procurar o paywall atual). Lista agrupada em `List` com `Section`; "Já lançadas" em `DisclosureGroup`.

- [ ] **Step 1: Testes** — decodificar a resposta gratuita (sem `linhas`) e a Premium; montar o pedido de importação só com as marcadas e com parcela; a demo responde a rota.
- [ ] **Step 2–4: falhar, implementar, passar** — `cd ios-nativo && xcodebuild -project Piggu.xcodeproj -scheme Piggu -destination 'platform=iOS Simulator,name=iPhone 17' test`, e build no simulador de iPad (pede o `AGENTS.md`).
- [ ] **Step 5: Commit** — `iOS: conferir a fatura do cartão`

### Task 8: Android

**Files:**
- Modify: `android-nativo/core/model/src/main/kotlin/br/com/piggu/core/model/Financas.kt` (modelos; `LinhaParaImportar` com parcela), `android-nativo/core/network/.../ServicoDeFinancas.kt` (rota), `android-nativo/core/network/src/test/.../RotasTest.kt`, `android-nativo/core/testing/.../ApiDaDemo.kt` e `ApiDaDemoTest.kt`
- Create: `android-nativo/feature/contas/.../ConferirFatura.kt` (composable da folha) e o estado/ações no `ContasECartoesViewModel` (a conferência começa pela fatura do cartão); em Gastos, a entrada fica em `Partes.kt` (`ImportarEExportar`) abrindo a mesma folha via o módulo de contas, ou só pela fatura se cruzar módulos for contra a arquitetura (decisão do executor; dizer no PR)
- Modify: `strings.xml` dos módulos tocados (nomes próprios por módulo; o `TextosDosModulosTest` reclama de nome repetido com texto diferente)
- Test: `android-nativo/feature/contas/src/test/.../ContasECartoesViewModelTest.kt` (casos novos)

**Interfaces:** mesmo comportamento do site. `ActivityResultContracts.OpenDocument()` com `application/pdf`, limite 5 MB. Consentimento como `FormularioDeGastoViewModel.kt:440-482` (`CONSENTIMENTO_NECESSARIO`, `VERSAO_DOS_TERMOS`); `PLANO_PREMIUM` como `PainelViewModel` (`erro.codigo == CODIGO_PREMIUM`).

- [ ] **Step 1: Testes do ViewModel** — gratuito só contagem; Premium com grupos e marcação inicial; importar manda só as marcadas com parcela e depois oferece marcar como paga; `PDF_COM_SENHA` pede senha; consentimento.
- [ ] **Step 2–4: falhar, implementar, passar** — `cd android-nativo && ./gradlew build`; se houver teste de tela (Roborazzi) da folha da fatura, `./gradlew recordRoborazziDemoDebug` e conferir a imagem.
- [ ] **Step 5: Commit** — `Android: conferir a fatura do cartão`

### Task 9: Decisões, roadmap e PR

- [ ] **Step 1: Números** — `git fetch && git grep -h "^## D-" $(git branch -r) -- docs/DECISIONS.md | sort -t- -k2 -n | tail -3` e o PR do leitor de notas; pegar os próximos livres.
- [ ] **Step 2: D-xxx** (uma só, "Conferir a fatura do cartão em PDF"): leitura híbrida (PDFBox + regras, Gemini só no Premium e só o texto), conciliação e as regras de data/valor/parcela, gratuito só contagem barrado no servidor, `GEMINI_FATURA` sem subir a versão, parcelas `n..m` ao adicionar, nada gravado do PDF. "Decidido por: Dudu (06/11/2026)", "Registrado por: Claude". Linha na tabela do topo, `docs/ROADMAP.md`, e uma linha no aviso de privacidade das três plataformas ("Fatura do cartão em PDF": lida no servidor e descartada; o texto vai ao Google só no Premium e com autorização) — com `npm run i18n` no site.
- [ ] **Step 3: Verificação final** — backend `./mvnw -q verify`, site `npm test -- --watch=false && npm run lint`, iOS `xcodebuild … test`, Android `./gradlew build`. Relatar o que passou e o que não rodou.
- [ ] **Step 4: PR** — `git push -u origin feature/leitor-de-fatura`, `gh pr create --base develop`; no topo: **"Não fazer merge antes de testar com faturas reais (Nubank, Itaú, Inter, …)"** e como testar pela demo.
