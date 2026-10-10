# Piggu Premium: o que é grátis e o que é pago

Proposta implementada na branch `feature/nova-arquitetura` (1º/10/2026). Os limites
são aplicados no backend (erro `422` com código `PLANO_PREMIUM`); o front só esconde
o botão e mostra o convite.

## Divisão

| Recurso | Plano | Por quê |
|---|---|---|
| Gastos, categorias, meta do mês, calendário | Grátis | É o núcleo do app; sem isso ninguém chega a querer o Premium |
| Cofrinho e depósitos da família (inclui o MEMBRO) | Grátis | Idem; é o que traz a família para dentro |
| Lista de compras, lugares (com foto), filmes | Grátis | Custo baixo e geram uso diário |
| Prêmios e Fofocoins | Grátis | Engajamento |
| Moeda e cotação | Grátis | Custo zero (Frankfurter) |
| Receitas, relatório do mês, comparação com o mês anterior e projeção | Grátis | Saber para onde vai o dinheiro é o básico de um app de finanças |
| Exportar e apagar os dados | Sempre grátis | Portabilidade e eliminação são direitos (LGPD art. 18) |
| **Orçamento por categoria com alerta** (80% e 100%) | **Premium** | Planejamento ativo, o que mais pesa na decisão de pagar |
| **Relatório do ano com gráficos** | **Premium** | Visão de longo prazo; o relatório do mês continua grátis |
| **Contas bancárias pelo Open Finance** (conectar e atualizar saldo) | **Premium** | Custo por conexão na Pluggy; é o recurso que mais vale |
| Leitura da nota fiscal pela foto, pelo leitor próprio (OCR) | Grátis até 10 por mês por família | A foto não sai do servidor e custa pouco; o limite segura abuso |
| **Leitura de nota além de 10 por mês, e a reserva pela IA (Gemini)** | **Premium** | Custo por chamada de IA |
| **Mural de fotos** (feed) | **Premium** | Custo de armazenamento |
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
| Site (Stripe) | R$ 19,90 | R$ 199,00 (2 meses grátis) |
| App iOS/Android (+15% pela taxa das lojas) | R$ 22,89 | R$ 228,85 |

Apple e Google exigem a compra dentro do app para assinatura digital vendida no app,
por isso o preço do app é separado. A tela web mostra só o preço do site.

## Como funciona

- O plano mora no identity (`users.premium_until`, migration `V3`) e vai no token
  (claim `plano`). Os outros serviços leem dali, sem chamar o identity.
- Pagamento no site atrás da interface `ProvedorDePagamento`; hoje `StripePagamentos`
  (Checkout + Portal do Cliente, API REST sem SDK). Trocar de provedor é escrever outra
  implementação.
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
