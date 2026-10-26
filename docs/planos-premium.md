# Piggu Premium: o que é grátis e o que é pago

Proposta implementada na branch `feature/nova-arquitetura` (1º/10/2026). Os limites
são aplicados no backend (erro `422` com código `PLANO_PREMIUM`); o front só esconde
o botão e mostra o convite.

## Desligado por enquanto (lembrar de reverter)

Decidido em 11/10/2026, antes da 1.0.0. Cada item volta com uma variável, sem mexer no código:

| O quê | Como está | Para voltar |
|---|---|---|
| Conectar banco (Open Finance) | Card escondido, conexão nova recusada, some da lista do Premium | `PIGGU_OPEN_FINANCE=true` (mais `PLUGGY_*` e o perfil `open-finance`) |
| Leitor próprio de nota (Tesseract) | Toda foto vai direto ao Gemini; 10 por mês por família no grátis, sem limite no Premium | `PIGGU_LEITOR_PROPRIO=true` |

O aviso de privacidade foi ajustado para o Gemini direto; ao religar o leitor próprio, volte
o texto da linha "Foto da nota fiscal" e o do Google (Gemini).

## Divisão

| Recurso | Plano | Por quê |
|---|---|---|
| Gastos, categorias, meta do mês, calendário | Grátis | É o núcleo do app; sem isso ninguém chega a querer o Premium |
| Cofrinho e depósitos da família (inclui o MEMBRO) | Grátis | Idem; é o que traz a família para dentro |
| Um cofrinho por objetivo (nome, valor, prazo e quanto guardar por mês) | Grátis | Dá o gosto de planejar; o segundo objetivo é o convite natural ao Premium |
| Lista de compras, lugares (com foto), filmes | Grátis | Custo baixo e geram uso diário |
| Prêmios e Fofocoins | Grátis | Engajamento |
| Moeda e cotação | Grátis | Custo zero (Frankfurter) |
| Receitas, relatório do mês, comparação com o mês anterior e projeção | Grátis | Saber para onde vai o dinheiro é o básico de um app de finanças |
| Exportar e apagar os dados | Sempre grátis | Portabilidade e eliminação são direitos (LGPD art. 18) |
| **Orçamento por categoria com alerta** (80% e 100%) | **Premium** | Planejamento ativo, o que mais pesa na decisão de pagar |
| **Relatório do ano com gráficos** | **Premium** | Visão de longo prazo; o relatório do mês continua grátis |
| **Contas bancárias pelo Open Finance** (conectar, atualizar saldo e trazer os gastos) | **Premium** | Custo por conexão na Pluggy; é o recurso que mais vale |
| Leitura da nota fiscal pela foto, pelo leitor próprio (OCR) | Grátis até 10 por mês por família | A foto não sai do servidor e custa pouco; o limite segura abuso |
| **Leitura de nota além de 10 por mês, e a reserva pela IA (Gemini)** | **Premium** | Custo por chamada de IA |
| **Mural de fotos** (feed) | **Premium** | Custo de armazenamento |
| **Vários cofrinhos por objetivo** | **Premium** | Planejamento de quem já guarda para mais de uma coisa |
| **Assistente de IA** (perguntas sobre gastos, orçamento, contas e cofrinho; só leitura; 150 por pessoa no mês) | **Premium** | Custo por chamada de IA; é o recurso pago mais visível |
| Ver, apagar e desconectar o que já existe | Sempre grátis | Premium vencido nunca prende dado da pessoa (LGPD) |

O Premium é da família: o titular assina e todos da família usam. O ADMIN usa tudo
sem plano (opera a instalação). O MEMBRO não alcança nenhum
recurso Premium, então não assina.

## Posso cobrar pelo Open Finance?

Sim. Conferi no texto consolidado da [Resolução Conjunta nº 1/2020](https://normativos.bcb.gov.br/Lists/Normativos/Attachments/51028/Res_Conj_0001_v7_L.pdf):

- **O que é proibido é cobrar pelo compartilhamento em si.** O art. 54 da resolução
  alterou a Resolução nº 3.919 para vedar tarifa "pelo compartilhamento de dados".
- **Cobrar pela agregação é permitido.** O mesmo art. 54 incluiu "agregação de dados
  compartilhados" na lista de serviços diferenciados que podem ser cobrados. Isso vale
  até para banco; o Piggu nem é instituição participante, e cobra pelo serviço que
  constrói em cima da Pluggy (saldo consolidado no painel).
- **Revogar tem que ser grátis e a qualquer momento** (art. 15), pelo mesmo canal em
  que a pessoa conectou. Por isso desconectar nunca pode ficar atrás do Premium.

Pela LGPD: o consentimento continua livre porque o tratamento só acontece se a pessoa
liga o recurso (art. 8); como a conexão é condição do recurso, isso precisa estar
destacado na tela (art. 9 §3); e quando o Premium acaba o dado do banco perde a
finalidade (art. 6, III), então o item deve ser desconectado (ver pendências).

Isto é leitura minha da norma, não parecer jurídico. Antes de cobrar, vale um advogado
confirmar, principalmente o contrato com a Pluggy.

## Preços

| | Mensal | Anual |
|---|---|---|
| Individual, site (Stripe) | R$ 19,90 | R$ 199,00 (2 meses grátis) |
| Individual, app iOS/Android (+15% pela taxa das lojas) | R$ 22,89 | R$ 228,85 |
| Casal (2 pessoas), site | R$ 29,90 | R$ 299,00 |
| Casal (2 pessoas), app (+15%, arredondado para baixo) | R$ 34,39 | R$ 343,85 |
| Família (até 5 pessoas), site | R$ 39,99 | R$ 399,90 |
| Família (até 5 pessoas), app (+15%, arredondado para baixo) | R$ 45,99 | R$ 459,89 |

**Individual x família (Eduardo, 2026-10-11).** O individual libera o Premium só para quem
assinou; o família libera a casa inteira, que fica limitada a 5 pessoas (contando convites
pendentes). Uma assinatura por casa. Assinaturas que já existiam viraram individuais de
quem pagou, na hora (migration `V18`). O tipo vem do preço cobrado na Stripe
(`STRIPE_PRICE_FAMILIA_*`) ou do produto da loja com "familia" no id, então trocar de
plano pelo Portal do Cliente funciona sem código novo.

**Plano casal (Eduardo, 2026-10-22).** Fica entre o individual e o família: libera o
Premium para quem assinou e para o parceiro (o titular e o parceiro da casa, nunca um
membro). O tipo vem do preço (`STRIPE_PRICE_CASAL_*`) ou do produto da loja com "casal"
no id, como o família (migration `V34`). Sem os preços do casal na Stripe, o card some
da tela e o Checkout recusa.

## Compartilhamento entre o casal e a família

Compartilhar é grátis em qualquer plano; o plano casal só paga o Premium dos dois.

- **Cada um vê o que é seu.** Gasto, receita, conta fixa, conta ou cartão e objetivo do
  cofrinho têm a marca "compartilhado" (`shared`, migration `V20` do finance). O que é
  compartilhado aparece para toda a casa; o resto, só para quem lançou.
- **Quem pode mexer.** Num item compartilhado, todos que veem editam; só quem criou
  apaga ou volta para "só meu".
- **Conta ou cartão do casal.** Marcada como compartilhada, tudo o que entra nela já nasce
  compartilhado (conta conjunta, cartão adicional). No Perfil, "Lançamentos novos já
  nascem compartilhados" faz o mesmo para tudo o que a pessoa lança.
- **Ver tudo do parceiro só com consentimento.** No Perfil, cada um liga "{parceiro} vê
  tudo o que eu lanço" (`users.shows_all_to`, migration `V34` do identity). O
  consentimento é dado a uma pessoa: se o parceiro sai ou deixa de ser parceiro, ele cai.
  Casais que já existiam continuam vendo tudo, porque a migration liga para os dois. O
  outro lado vê a mudança quando o token renova (até 5 minutos).
- **Família.** Titular e parceiro podem ver tudo um do outro (com o consentimento acima);
  os demais veem o próprio e o compartilhado. O titular só vê tudo de quem é menor de
  idade, nunca de um adulto da casa.
- **Painel.** Quem mora com alguém escolhe "Tudo / Meu / Compartilhado" (parâmetro
  `visao=meu|casal` em `/api/expenses` e `/api/reports/month`).
- **Quem deve a quem.** `GET /api/expenses/splits/transfers?mes=` resume a divisão do mês
  no menor número de transferências ("Bia paga R$ 30 para Ana").

Apple e Google exigem a compra dentro do app para assinatura digital vendida no app,
por isso o preço do app é separado. A tela web mostra só o preço do site.

## Como funciona

- O plano mora no identity (`users.premium_until`, migration `V3`) e vai no token
  (claim `plano`). Os outros serviços leem dali, sem chamar o identity.
- Pagamento no site pela `StripePagamentos` (Checkout + Portal do Cliente, API REST sem
  SDK). Sem interface enquanto há um provedor só; um segundo traz a interface de volta (D-077).
- O Premium só muda pelo webhook assinado (HMAC, tolerância de 5 min). Voltar da página
  de pagamento não concede nada. Aviso antigo que chega depois de um mais novo é ignorado.
- Compra nas lojas: `POST /api/billing/stores/{loja}/purchases` responde `501` até os
  apps existirem.
- Cancelar é tão fácil quanto assinar: botão "Gerenciar assinatura" abre o portal da Stripe.

## Falta antes de cobrar de verdade

1. **Stripe:** criar a conta, um produto com dois preços recorrentes em BRL, o webhook
   (`/api/billing/webhooks/stripe`, eventos `customer.subscription.*`) e o Portal do
   Cliente com cancelamento liberado. Preencher as variáveis `STRIPE_*` do `.env`.
2. ~~Desconectar banco~~: feito. `DELETE /api/banking/connections/{id}` apaga o item na
   Pluggy e as contas salvas; nunca pede Premium.
3. **Premium vencido:** hoje as contas salvas seguem visíveis (certo), mas o item
   continua sincronizando na Pluggy (custo e tratamento sem finalidade). Sugestão:
   desconectar sozinho 30 dias depois do vencimento, com aviso.
4. ~~Arrependimento de 7 dias~~ (CDC art. 49): feito, botão "Cancelar e pedir reembolso"
   na tela de planos nos 7 primeiros dias, que cancela e devolve pela API da Stripe.
5. ~~Aviso de privacidade~~: feito, a Stripe está na lista de operadores em `/privacidade`.
6. ~~Teste grátis~~: 7 dias (`PIGGU_DIAS_DE_TESTE`) por `trial_period_days` no Checkout,
   uma vez por conta (marcado quando a Stripe avisa que o teste começou).
7. ~~Apps~~: webhook do RevenueCat em `POST /api/billing/stores/{app-store|play-store}/purchases`
   (o RevenueCat valida o recibo com as lojas), autenticado pelo segredo
   `REVENUECAT_WEBHOOK_SECRET`, idempotente pelo id do aviso. Falta criar a conta no
   RevenueCat e publicar os apps com o id da conta Piggu como `app_user_id`.
8. ~~Premium magro~~: entraram o orçamento por categoria com alerta e o relatório do ano
   com gráficos (C5). Exportar os dados e o relatório do mês seguem grátis.
