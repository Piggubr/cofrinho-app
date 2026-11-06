# Leitor próprio de notas ligado por padrão

Branch: `feature/leitor-de-notas-proprio` (sai da `develop`, PR para a `develop`).

## Por quê

Desde 11/10/2026, `piggu.leitura-de-nota.leitor-proprio=false`: toda foto de cupom vai direto
para o Gemini, e cada leitura custa. O leitor próprio (Tesseract + `LeitorDeCupom`, com o Gemini
só quando a soma dos itens não bate com o total) já existe, mas foi desligado até ser testado
com cupons reais. O objetivo é fazer a maioria das fotos ser lida sem o Gemini, e o custo cair.

## Objetivos

- Uma bateria de testes do `LeitorDeCupom` com textos de cupom como o Tesseract costuma
  devolver, cobrindo os casos comuns no Brasil; corrigir o leitor onde falhar.
- Ligar `leitor-proprio=true` por padrão (`application.yml`, e o que mais carregar o padrão,
  como `docker-compose.yml`).
- Medir em produção quantas fotos o OCR resolve e quantas vão para o Gemini.
- Nova D-xxx em `docs/DECISIONS.md` (com "Decidido por: Dudu" e "Registrado por: Claude"),
  e o ROADMAP atualizado.

## Fora do escopo

- Mudar o fluxo do `LeitorDeRecibos` (cota, consentimento, Premium, D-156): fica como está.
- Trocar o Tesseract ou mexer no pré-processamento da imagem, a não ser que os testes mostrem
  que o problema está ali (aí vira uma pergunta, não uma mudança automática).
- Telas: nada muda no site, no iOS ou no Android.

## Falha mesmo funcionando

- Ler errado e **dizer que fechou**. O pior caso é soma e total iguais com item errado. A
  tolerância de R$ 0,05 e a regra "fecha só se a soma bate" não podem ficar mais frouxas.
- Gravar texto de cupom real (CPF, endereço) no repositório ou no log.

## Restrições

- Ainda não há fotos reais. Os testes usam textos sintéticos, no formato do OCR, em
  `backend/piggu-finance/src/test/resources/recibos/` (já existem `nfce-ocr.txt`, `europeu.txt`,
  `europeu-foto-ruim-ocr.txt`). O PR diz claramente que **o merge espera o Dudu testar com umas
  10 fotos reais**.
- O Gemini continua como reserva: na dúvida, o leitor próprio deve "não fechar" e deixar o
  Gemini ler, nunca chutar.

## Casos que a bateria cobre (mínimo)

1. NFC-e de mercado com descrição quebrada em duas linhas (já existe; manter).
2. NFC-e com desconto por item e "VALOR A PAGAR" menor que o "VALOR TOTAL".
3. Item pesado em kg (`1,235 KG X 6,99`), com arredondamento.
4. Posto de combustível (litros com 3 casas, um item só).
5. Farmácia com desconto de convênio no fim.
6. CF-e SAT (cupom de São Paulo).
7. Ruído típico de OCR: `O` no lugar de `0` dentro do valor, espaços dentro do número,
   linha de total partida em duas. Aqui, o certo pode ser "não fecha" em vez de ler.
8. Cupom com `R$` antes dos valores.

## Medição

Hoje o `LeitorDeRecibos` já registra no log "Recibo lido pelo OCR" e "Recibo segue para o
Gemini". Se o `piggu-finance` já usa Micrometer (ver `backend/observabilidade/`), acrescentar
um contador com a origem (`ocr`, `gemini`, `parcial`). Se não usa, o log basta. Decisão do builder.

## Fica com o builder

- Como corrigir cada falha no `LeitorDeCupom`, sempre com um teste antes.
- Nomes dos arquivos de teste.

## Decisões depois da revisão (minhas, para o Dudu conferir)

- **Aviso de privacidade**: a D-047 pede que, ao religar, volte o texto "Foto da nota fiscal" e o
  do Google (Gemini) no aviso (`docs/planos-premium.md`, "Desligado por enquanto"). Volta neste PR,
  **sem** subir `Consentimentos.VERSAO_DO_AVISO`: o envio ao Gemini continua igual (só passa a ser
  a reserva), então o consentimento dado continua valendo e os apps já publicados não quebram.
- **Produção**: o `.env.example` e o `docs/implantacao-producao.md` mudam para `true`; o `.env` de
  produção **não** é tocado. Ligar em produção é ação do Dudu, depois do teste com fotos reais.
- **Gratuito**: com o leitor ligado, volta a regra que já está em `docs/planos-premium.md` (linhas
  "Leitura pelo leitor próprio: grátis até 10" e "reserva pela IA: Premium"), confirmada pelo Dudu em 06/11/2026: no gratuito, a IA fica só
  no Premium, e a cota de 10 conta as leituras pelo OCR.
