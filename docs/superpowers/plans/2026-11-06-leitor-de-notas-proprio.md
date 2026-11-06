# Leitor próprio de notas — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer o OCR próprio ler a maioria dos cupons e ligá-lo por padrão, com o Gemini só como reserva no Premium.

**Architecture:** Nenhuma peça nova. Bateria de testes no `LeitorDeCupom` (regras sobre o texto do Tesseract), correções onde ela falhar, e a troca do padrão `piggu.leitura-de-nota.leitor-proprio` para `true`. O fluxo do `LeitorDeRecibos` (cota, consentimento, Premium) não muda.

**Tech Stack:** Java 21, Spring Boot, JUnit 5 + AssertJ, Tesseract (já no Dockerfile).

**Spec:** `docs/superpowers/specs/2026-11-06-leitor-de-notas-proprio-design.md`

## Global Constraints

- Tolerância de "fecha" continua R$ 0,05 (`LeitorDeCupom.TOLERANCIA`); nenhuma regra fica mais frouxa.
- Na dúvida, o leitor **não fecha** (deixa a reserva decidir), nunca chuta.
- Nenhum texto de cupom real no repositório; os fixtures são sintéticos.
- `Consentimentos.VERSAO_DO_AVISO` **não** muda.
- O `.env` de produção não é tocado.
- Textos em português do Brasil. D-xxx nova com "Decidido por: Dudu" e "Registrado por: Claude".

## Review Focus

1. Cupom cuja soma bate com o total por coincidência, com um item a mais e um desconto mal atribuído: precisa de um teste que garanta que o desconto só abate o item anterior (já é a regra) e que a linha de subtotal não vira item.
2. Valor com `O` no lugar de `0` (`1O,99`): o certo é a linha não virar item com valor errado; a leitura não fecha.
3. Linha do total partida ("VALOR A PAGAR R$" e o valor na linha seguinte): já tratada por `esperandoValorDoTotal`; o teste fixa isso com `R$`.
4. Cupom com dois totais ("VALOR TOTAL" maior e "VALOR A PAGAR" menor, depois do desconto): vale o a pagar.
5. Combustível com litros em 3 casas (`40,123 L X 5,89`): a quantidade não pode ser lida como valor.

---

### Task 1: Bateria de cupons e correções no `LeitorDeCupom`

**Files:**
- Create: `backend/piggu-finance/src/test/resources/recibos/nfce-desconto-ocr.txt`, `nfce-kg-ocr.txt`, `posto-ocr.txt`, `farmacia-ocr.txt`, `cfe-sat-ocr.txt`, `ruido-ocr.txt`
- Modify: `backend/piggu-finance/src/test/java/com/piggu/finance/integration/LeitorDeCupomTest.java`
- Modify (onde falhar): `backend/piggu-finance/src/main/java/com/piggu/finance/integration/LeitorDeCupom.java`

**Interfaces:**
- Consumes: `LeitorDeCupom.ler(String texto, LocalDate hoje) -> Leitura` (já existe; assinatura não muda).
- Produces: nada novo.

- [ ] **Step 1: Escrever os fixtures** no formato da saída do Tesseract (maiúsculas, espaços irregulares, cabeçalho com CNPJ e endereço, rodapé com forma de pagamento e tributos). Valores de cada um:
  - `nfce-desconto-ocr.txt`: 3 itens (12,90; 7,50; 22,00), uma linha `DESCONTO -2,00` depois do 2º, `VALOR TOTAL R$ 42,40`, `VALOR A PAGAR R$ 40,40`.
  - `nfce-kg-ocr.txt`: `1,235 KG X 6,99 8,63` e `0,456 KG X 39,90 18,19`, total 26,82.
  - `posto-ocr.txt`: `GASOLINA COMUM 40,123 L X 5,89 236,32`, total 236,32.
  - `farmacia-ocr.txt`: 2 itens (34,90; 18,50), `DESC CONVENIO -5,34` depois do 2º, `TOTAL 48,06`.
  - `cfe-sat-ocr.txt`: CF-e SAT de 3 itens no layout `001 CODIGO DESCRICAO 1 UN X 9,99 9,99`, `TOTAL R$ 29,47`, com a chave de acesso de 44 dígitos no rodapé.
  - `ruido-ocr.txt`: cupom de 3 itens em que um valor saiu `1O,99` e o total `VALOR A PAGAR R$` vem numa linha e `31,47` na seguinte.

- [ ] **Step 2: Escrever os testes**, um por fixture, no estilo dos que já existem (`@DisplayName` em português):
  - `nfceComDescontoFechaPeloAPagar`: valores `12.90, 5.50, 22.00`; `total` = `40.40`; `fecha()` verdadeiro.
  - `itemPesadoEmKg`: valores `8.63, 18.19`; fecha.
  - `postoNaoLeLitrosComoValor`: um item, valor `236.32`; fecha.
  - `farmaciaComDescontoDeConvenio`: valores `34.90, 13.16`; total `48.06`; fecha.
  - `cfeSat`: 3 itens, total `29.47`, fecha; a chave de acesso não vira item.
  - `ruidoDeOcrNaoFecha`: `total` = `31.47`; `fecha()` falso.

- [ ] **Step 3: Rodar e ver quais falham**

Run: `cd backend && ./mvnw -q -pl piggu-finance test -Dtest=LeitorDeCupomTest`
Expected: os 5 testes antigos passam; anote quais dos novos falham e por quê.

- [ ] **Step 4: Corrigir cada falha na causa**, uma de cada vez, rodando a classe toda depois de cada correção (os 5 antigos não podem quebrar). Se uma falha só se resolve afrouxando a regra de "fecha", não corrija: mude o teste para esperar `fecha()` falso e registre o motivo num comentário de uma linha.

- [ ] **Step 5: Rodar a suíte do leitor**

Run: `cd backend && ./mvnw -q -pl piggu-finance test -Dtest='LeitorDeCupomTest,LeitorDeRecibosTest,TesseractOcrTest'`
Expected: BUILD SUCCESS (o `TesseractOcrTest` pode ser pulado se o Tesseract não estiver instalado na máquina; diga isso).

- [ ] **Step 6: Commit** — `Leitor de nota: bateria de cupons (desconto, kg, posto, farmácia, SAT, ruído) e correções`

### Task 2: Ligar o leitor por padrão, voltar o aviso de privacidade e registrar a decisão

**Files:**
- Modify: `backend/piggu-finance/src/main/resources/application.yml:43-45` (comentário e `${PIGGU_LEITOR_PROPRIO:true}`)
- Modify: `backend/piggu-finance/src/main/java/com/piggu/finance/integration/LeitorDeRecibos.java` (javadoc da classe e `@Value("${piggu.leitura-de-nota.leitor-proprio:true}")`)
- Modify: `backend/.env.example:34-36`, `docs/implantacao-producao.md:129`
- Modify: `frontend/src/app/features/legal/privacidade.html:58` e `:94`, `frontend/src/locale/messages.xlf` (regerado)
- Modify: `ios-nativo/Piggu/Recursos/Privacidade.md:29,47`, `android-nativo/feature/legal/src/main/assets/Privacidade.md:29,47`
- Modify: `docs/planos-premium.md` (tira a linha do leitor da tabela "Desligado por enquanto" e o parágrafo do aviso), `docs/DECISIONS.md` (D-xxx nova; D-047 vira "Substituída pela D-xxx"), `docs/ROADMAP.md`

**Interfaces:** nenhuma.

- [ ] **Step 1: Trocar o padrão** nos quatro lugares de configuração acima para `true`, com o comentário dizendo que `false` manda tudo direto ao Gemini.

- [ ] **Step 2: Voltar o texto do aviso**, idêntico nas três plataformas:
  - Linha "Foto da nota fiscal", finalidade: `Ler os itens automaticamente. Primeiro tentamos no nosso servidor; só se não der, e com sua autorização, a foto vai para a IA do Google`
  - Linha "Google (Gemini, IA)", quando: `Só quando nosso leitor não consegue e você autoriza, ou quando você pergunta ao assistente depois de autorizar`

- [ ] **Step 3: Regerar o i18n do site**

Run: `cd frontend && npm run i18n`
Expected: `messages.xlf` muda só nas duas frases.

- [ ] **Step 4: Escrever a D-xxx.** Antes, o número: `git fetch && git grep -h "^## D-" $(git branch -r) -- docs/DECISIONS.md | sort -t- -k2 -n | tail -3`, e pegar o próximo livre (a branch `feature/leitor-de-fatura` também vai pegar números; esta pega o primeiro). Conteúdo: contexto (custo do Gemini; D-047), decisão (padrão `true`; gratuito com OCR dentro das 10 por mês e a reserva pelo Gemini só no Premium, como em `planos-premium.md`; aviso de privacidade de volta ao texto da D-046 sem subir `VERSAO_DO_AVISO`; produção liga só depois do teste com ~10 fotos reais), alternativas (manter a D-047; gratuito com reserva pelo Gemini), "Decidido por: Dudu (06/11/2026)", "Registrado por: Claude". Linha na tabela do topo do arquivo e no ROADMAP.

- [ ] **Step 5: Verificar**

Run: `cd backend && ./mvnw -q -pl piggu-finance test` e `cd frontend && npx ng test --watch=false` (ou o script de teste do `package.json`)
Expected: tudo passa. Se algum teste do site fixar o texto antigo do aviso, atualize-o.

- [ ] **Step 6: Commit** — `Leitor próprio de nota ligado por padrão e aviso de privacidade de volta (D-xxx)`

### Task 3: PR

- [ ] **Step 1:** `git push -u origin feature/leitor-de-notas-proprio` e `gh pr create --base develop`, com o corpo dizendo, no topo: **"Não fazer merge antes de testar com ~10 fotos reais de cupom"**, como testar (`PIGGU_LEITOR_PROPRIO=true`, ver no log "Recibo lido pelo OCR" ou "segue para o Gemini") e que o `.env` de produção não foi mudado.
