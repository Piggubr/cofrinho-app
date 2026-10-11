# Plano de resposta a incidente de segurança

Para quando houver suspeita de que dado pessoal vazou, foi alterado ou ficou acessível a
quem não devia (LGPD art. 46 a 48). Curto de propósito: é para ser seguido sob pressão.

**Canal:** privacidade@piggu.app (o mesmo do aviso de privacidade) recebe relatos de
titulares e de terceiros. Quem opera a instalação (ADMIN) responde.

## 1. Primeira hora: conter

| Situação | O que fazer |
|---|---|
| Chave JWT exposta | Gerar par novo (`docs/implantacao-producao.md`), trocar `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` e reiniciar: todos os tokens e sessões caem. Se a chave vazada é a antiga do Git, o identity já a recusa. |
| Sessão roubada de uma pessoa | ADMIN desativa a conta (`PATCH /api/users/{id}` com `ativo=false`): apaga as sessões dela na hora. |
| Senha do banco exposta | Do superusuário: `ALTER USER piggu PASSWORD '...'` e atualizar `DB_PASSWORD`. De um serviço: trocar a `DB_PASSWORD_<SERVIÇO>` no `.env`, `docker compose up -d postgres`, rodar o `db/init/01-criar-bancos.sh` e reiniciar o serviço. Conferir que a porta 5432 não está publicada. |
| Chave de integração exposta (Gemini, Pluggy, Stripe, TMDB, Drive) | Revogar no painel do provedor, gerar outra, trocar no `.env` e reiniciar. Stripe: também trocar o segredo do webhook. |
| Abuso em massa (scraping, força bruta) | Baixar os limites (`piggu.limite.*`) e, se preciso, bloquear o IP no Caddy/firewall. |
| Falha no código que expõe dado de outra família | Tirar a rota do ar no gateway (ou o serviço inteiro) até o conserto, mesmo que o app fique parcial. |

Não apagar logs nem dados durante a contenção: eles são a prova do que aconteceu.

## 2. Primeiro dia: entender

Responder, por escrito, no registro do incidente (item 5):

- O que aconteceu, quando começou e quando foi contido.
- Quais dados (categorias) e de quantas pessoas/famílias. Dado financeiro (gastos,
  saldos bancários) conta como risco relevante.
- Como entrou (falha de código, credencial, terceiro).
- Os logs ajudam: cada linha tem `requestId` e o id da conta, nunca e-mail.

## 3. Comunicar

A ANPD pede comunicação de incidente que possa causar **risco ou dano relevante** aos
titulares em até **3 dias úteis** do conhecimento (Resolução CD/ANPD nº 15/2024), pelo
formulário no site da ANPD. Na dúvida sobre a relevância, comunicar.

Para os titulares afetados, por e-mail, em linguagem simples:

- o que aconteceu e quais dados dele;
- o que já foi feito;
- o que ele pode fazer (entrar de novo, desconectar o banco e conectar outra vez,
  ficar atento a golpes que usem os dados);
- o canal privacidade@ para dúvidas.

Se envolver Open Finance, avisar também a Pluggy; se envolver pagamento, a Stripe.

## 4. Corrigir e prevenir

- Consertar a causa, com um teste que falhe se ela voltar.
- Rodar a revisão de `docs/revisao-seguranca-pre-publicacao.md` de novo.
- Trocar qualquer segredo que possa ter passado pelo mesmo caminho.

## 5. Registro

Todo incidente, comunicado ou não, fica registrado (LGPD art. 48 §1 e Resolução 15/2024):
data, descrição, dados e pessoas afetadas, medidas, se e quando ANPD e titulares foram
avisados e por quê. Guardar por pelo menos 5 anos, fora do repositório.
