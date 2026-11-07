# Conferir a fatura do cartão

Branch: `feature/leitor-de-fatura` (sai da `develop`, PR para a `develop`).

## Por quê

Quem lança os gastos à mão chega no fim do mês sem saber o que esqueceu de lançar. Hoje, para
descobrir, precisa abrir a fatura e conferir linha por linha. A ideia é que a pessoa envie o PDF
da fatura e o Piggu responda: "Sua fatura tem 47 compras: 41 você já lançou, faltam 6. Quer
adicionar?". O diferencial é **conciliar** com o que já foi lançado, e não só ler o PDF: o OFX e
o Open Finance já trazem transações, mas duplicariam o que foi digitado à mão.

## Objetivos

- Ler o PDF da fatura no servidor, primeiro com o leitor próprio (sem IA) e, se a leitura não
  fechar, com o Gemini (Premium), no mesmo modelo do leitor de notas (`LeitorDeRecibos`).
- Conciliar as compras da fatura com os gastos já lançados e dizer quais faltam.
- **Gratuito**: só a contagem (compras, já lançadas, faltam). **Premium**: a lista do que falta,
  para adicionar com um toque, e "Marcar fatura como paga" (D-085) no fim.
- Site, iOS nativo e Android nativo nesta mesma branch.

## Fora do escopo

- Estorno abatendo um gasto existente (o estorno só aparece como ignorado).
- Guardar o PDF ou o texto da fatura.
- Ajustes por banco além do leitor genérico (vêm depois, guiados pelo log de qual banco caiu no Gemini).
- App em Capacitor (`frontend/ios`, `frontend/android`): só o site Angular.

## Falha mesmo funcionando

- Sugerir como "faltando" uma compra que a pessoa já lançou: a pessoa adiciona e duplica o gasto.
  Na dúvida entre "já lançada" e "falta", a linha vai para um grupo "Confira" com o gasto
  parecido ao lado, nunca para "falta" direto.
- Ler errado e dizer que fechou.
- Mandar o PDF ao Gemini, ou qualquer dado da fatura, sem a autorização da pessoa.
- Barrar o Premium só na tela: o servidor **não devolve** as linhas para quem é gratuito.

## Como funciona

### Leitura (backend, `piggu-finance`)

1. A pessoa envia o PDF (até 5 MB; JSON com base64, como os recibos), uma senha opcional e,
   opcionalmente, o cartão (`contaId`).
2. **Apache PDFBox** (dependência nova) extrai o texto. PDF com senha sem a senha certa devolve
   422 com um código próprio (ex.: `PDF_COM_SENHA`), e a tela pede a senha. PDF sem texto
   (escaneado) não fecha e segue a regra abaixo.
3. **Leitor genérico de fatura** (regras, sem IA, no estilo do `LeitorDeCupom`): linhas
   `dd/mm DESCRIÇÃO valor`, parcela `NN/MM` na descrição, o total da fatura e o vencimento. O ano
   de cada linha vem do vencimento (compra de dezembro numa fatura de janeiro é do ano anterior).
   Cada linha ganha um tipo:
   - `COMPRA` (inclui parcela, com número e total);
   - `TARIFA`: IOF, anuidade, juros, multa, encargos. Vai para a lista **desmarcada**;
   - `IGNORADA`: pagamento da fatura anterior, crédito, estorno. Aparece só na contagem.
4. **Fecha** quando a soma das compras e tarifas, menos créditos e estornos, bate com o total da
   fatura (tolerância de R$ 0,05, como no cupom). O leitor precisa saber se o "total" impresso é
   o da fatura do mês ou se inclui saldo anterior; se não der para saber, não fecha.
5. Não fechou:
   - **Gratuito**: 422 com `Plano.CODIGO_PREMIUM` e o texto "Não consegui ler esta fatura
     sozinho. A leitura pela IA faz parte do Piggu Premium."
   - **Premium**: pede o consentimento (finalidade nova `GEMINI_FATURA`, com aviso próprio que
     fala em "texto da fatura") e manda **só o texto extraído** ao Gemini, com o mesmo formato de
     saída das linhas acima. Se o Gemini falhar e houver leitura parcial, devolve a parcial com
     um aviso.
6. No gratuito não há Gemini, então a leitura **não gasta** a cota de notas. O Premium não tem
   limite. Log: banco (se der para identificar pelo texto), número de linhas e origem
   (`PROPRIA` / `GEMINI`), nunca o conteúdo.

### Conciliação

Para cada `COMPRA` ou `TARIFA`, procura um gasto da família que ainda não tenha sido usado por
outra linha:

1. Mesmo `external_id`: já importado desta fatura antes. O id é um hash da data, da descrição,
   do valor e da ocorrência, como em `LeitorDeExtrato.comIdsGerados`.
2. Valor exato e data a até 3 dias. Havendo mais de um, ganha o do cartão escolhido, depois o
   de data mais próxima, depois o de descrição mais parecida.
3. Parcela `n/m`: um gasto com `installment_number = n`, `installment_count = m` e o mesmo
   valor ganha de qualquer outro.
4. Valor exato com data entre 4 e 10 dias, ou dois candidatos empatados: grupo **Confira**,
   com o gasto parecido ao lado.

Resultado por linha: `JA_LANCADA` (com o id do gasto), `CONFIRA` (com o id do candidato) ou `FALTA`.

### Resposta

Só acrescenta campos (D-059). Sempre: total da fatura, soma lida, origem, vencimento e a
contagem (compras, já lançadas, confira, faltam, tarifas, ignoradas). Só no Premium: as linhas
(data, descrição, valor, parcela, tipo, situação, id do gasto relacionado, categoria sugerida
pelas regras de categoria e pela memória, como na prévia do extrato, e `idExterno`).

### Adicionar

Usa o `POST /expenses/import` que já existe, com o `contaId` do cartão. Se a `LinhaImportada`
ainda não leva parcela, ganha os campos opcionais de parcela (só acrescentando). Ler a mesma
fatura de novo não duplica, por causa do `idExterno`.

### Telas (site, iOS, Android)

- Onde entra: na folha da fatura do cartão em Contas e cartões ("Conferir com a fatura do banco",
  já com o cartão escolhido) e na área de importar extrato dos Gastos.
- Uma folha: escolher o PDF, depois "lendo", depois o resultado:
  - **Gratuito**: a contagem e o convite ao Premium para ver quais são.
  - **Premium**: grupos "Faltam" (marcados), "Confira" (desmarcados, com o gasto parecido ao
    lado), "Tarifas" (desmarcadas) e "Já lançadas" (recolhido); cada linha troca de categoria;
    "Adicionar N" grava e mostra quantos entraram; depois oferece "Marcar fatura como paga"
    quando há cartão.
- PDF com senha: um campo de senha na própria folha.
- Pede o consentimento do Gemini como na leitura de nota, em cada plataforma.
- As demos (`TransporteDeDemonstracao.swift`, `ApiDaDemo.kt` e a demo do site, se existir)
  ganham uma resposta de exemplo.
- Textos em português do Brasil.

## Obrigações do repositório no mesmo PR

- `cd backend && ./scripts/regravar-contrato.sh`, conferindo que só acrescentou.
- D-xxx novas em `docs/DECISIONS.md`. Antes, conferir o maior número nas branches abertas
  (`git fetch && git grep -h "^## D-" $(git branch -r) -- docs/DECISIONS.md`); a branch do leitor
  de notas também vai pegar um número. "Decidido por: Dudu", "Registrado por: Claude".
- `docs/API_SPEC.md`, `docs/ROADMAP.md`.

## Testes

- Leitor: textos sintéticos no formato do PDFBox para Nubank, Itaú, Inter, Bradesco e C6, mais
  um PDF gerado no próprio teste com o PDFBox (de ponta a ponta), um PDF com senha e um sem texto.
  Ainda não há faturas reais; o PR avisa que o merge espera o teste com faturas de verdade.
- Conciliação: valor igual com data a 2 dias, dois candidatos, parcela, gasto já importado, mesmo
  gasto não usado duas vezes, virada de ano.
- Gratuito sem linhas na resposta; Premium com linhas; consentimento exigido antes do Gemini.

## Fica com o builder

- Nomes de classes, rota exata (sugestão: `POST /api/expenses/import/card-statement`) e DTOs.
- Organização das telas em cada plataforma, seguindo a importação de extrato que já existe
  (`ImportarExtratoView.swift`, `importar-extrato.ts`, a importação do `feature/gastos` no Android).
- O prompt do Gemini.

## Decisões depois da revisão (minhas, para o Dudu conferir)

- **Parcela que falta**: "Adicionar" a parcela `n/m` grava ela e as seguintes (`n` a `m`), uma por
  mês, como o parcelamento manual (DATA_MODEL, "Parcelamento"), com o mesmo `receipt_id`. Assim a
  fatura do mês que vem já mostra `n+1/m` como "já lançada".
- **Casar parcela**: mesmo `n/m`, valor a até R$ 0,01 (o app põe o centavo que sobra na última;
  o banco pode pôr em outra) e `expense_date` dentro do período de compras da fatura, com folga de
  3 dias (o app pode mover a parcela para o último dia de compra). Fora disso, vai para "Confira".
- **Consentimento**: finalidade `GEMINI_FATURA`, com a versão de aviso global que já existe, sem
  subir. O texto da autorização aparece na hora de pedir; versionar por finalidade fica para
  quando algum texto mudar de novo.
- **Candidatos da conciliação**: todos os gastos da família, de qualquer conta, com data entre a
  primeira compra da fatura menos 3 dias e a última mais 3 (mais as parcelas pela regra acima). O
  cartão escolhido só desempata. Sem cartão escolhido, igual, e não há "Marcar como paga" no fim.
