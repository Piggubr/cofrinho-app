# E-mail do domínio piggu.com.br (SPF, DKIM, DMARC e caixa de entrada)

O Piggu manda três e-mails: o link de confirmar o e-mail, o link de "esqueci minha senha" e
os avisos do dia (fatura, orçamento, conta do mês). Todos saem pela [Resend](https://resend.com)
com remetente `@piggu.com.br`. Para chegarem na caixa de entrada, e não no spam ou em lugar
nenhum, o domínio precisa provar que a Resend pode falar em nome dele.

## Como estava em 19/10/2026

Consulta pública ao DNS do `piggu.com.br` (servidores da Cloudflare):

| Registro | Nome | Resultado |
|---|---|---|
| SPF (TXT) | `piggu.com.br` e `send.piggu.com.br` | nenhum |
| DKIM (TXT) | `resend._domainkey.piggu.com.br` | nenhum |
| DMARC (TXT) | `_dmarc.piggu.com.br` | nenhum |
| MX | `piggu.com.br` | nenhum |

Ou seja: **o domínio não usa SPF, DKIM nem DMARC**, a Resend não tem como verificar o domínio e
nenhum e-mail do Piggu sai em produção. Sem MX, o domínio também não recebe e-mail, então o
contato do encarregado (`privacidade@piggu.com.br`) precisa da parte 3 para funcionar.

O que cada um faz:

- **SPF**: lista quem pode mandar e-mail pelo domínio. A Resend usa o subdomínio `send.`.
- **DKIM**: assinatura em cada e-mail; a chave pública fica no DNS e o Gmail confere.
- **DMARC**: diz ao Gmail o que fazer quando SPF e DKIM falham (nada, spam ou recusar) e para
  onde mandar o relatório.

## 1. Verificar o domínio na Resend

1. Entre em [resend.com](https://resend.com) (crie a conta se ainda não tiver).
2. **Domains > Add Domain**, digite `piggu.com.br` e escolha a região **São Paulo (sa-east-1)**.
3. A Resend mostra uma tabela com 3 registros. Deixe a página aberta; os valores exatos vêm dela.
   Eles têm este formato:

| Tipo | Nome | Valor | Prioridade |
|---|---|---|---|
| MX | `send` | `feedback-smtp.sa-east-1.amazonses.com` | 10 |
| TXT | `send` | `v=spf1 include:amazonses.com ~all` | |
| TXT | `resend._domainkey` | `p=MIGfMA0...` (chave longa, copie inteira) | |

4. No painel da Cloudflare: **piggu.com.br > DNS > Records > Add record**. Crie os três, copiando
   tipo, nome e valor da Resend. Na Cloudflare o nome é só a parte antes do domínio (`send`,
   `resend._domainkey`). Se aparecer a nuvem laranja, deixe cinza (**DNS only**).
5. Volte na Resend e clique em **Verify DNS Records**. Costuma ficar **Verified** em minutos.

## 2. DMARC

Comece só observando (`p=none`): nada é recusado e você recebe relatórios.

**Jeito mais fácil, pela Cloudflare:** **piggu.com.br > Email > DMARC Management > Enable**. Ela
cria o registro `_dmarc` com o endereço de relatório dela e mostra os relatórios num painel.

**Ou na mão:** **DNS > Add record**

| Tipo | Nome | Valor |
|---|---|---|
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@piggu.com.br; adkim=r; aspf=r` |

(o `dmarc@` precisa existir: crie a rota na parte 3).

Depois de 2 a 4 semanas com os relatórios mostrando só a Resend passando, endureça trocando
`p=none` por `p=quarantine` (vai para o spam) e, mais adiante, por `p=reject` (recusado).

## 3. Receber e-mail no domínio (privacidade@, contato@)

O Aviso de Privacidade e os Termos mandam escrever para `privacidade@piggu.com.br`. A LGPD
(art. 41) exige que esse canal funcione. O Cloudflare Email Routing encaminha de graça para o
seu Gmail:

1. **piggu.com.br > Email > Email Routing > Get started / Enable**.
2. Aceite os registros que ela propõe (**Add records and enable**). Ela cria três MX
   (`route1/2/3.mx.cloudflare.net`) e um TXT de SPF na raiz
   (`v=spf1 include:_spf.mx.cloudflare.net ~all`). Esse SPF é da raiz e o da Resend é do
   `send.`, então os dois convivem. Não crie um segundo SPF na raiz: só pode haver um por nome.
3. **Destination addresses**: adicione o seu Gmail e confirme pelo e-mail que chega lá.
4. **Routing rules > Create address**: `privacidade` → seu Gmail. Repita para `contato` e, se
   usou o DMARC manual, `dmarc`.

## 4. Ligar o e-mail no servidor

```bash
ssh -i ~/.ssh/piggu-oracle.key ubuntu@IP_DO_SERVIDOR
cd /opt/piggu/backend
nano .env
```

Na Resend, **API Keys > Create API Key** (permissão **Sending access**, domínio `piggu.com.br`).
A chave aparece uma vez só. No `.env`, preencha:

```env
PIGGU_EMAIL_API_KEY=re_xxxxxxxxxxxxxxxx
PIGGU_EMAIL_REMETENTE=Piggu <nao-responda@piggu.com.br>
SITE_URL=https://piggu.com.br
```

Salve (**Ctrl+O**, **Enter**, **Ctrl+X**) e recrie o serviço que manda os e-mails:

```bash
docker compose --profile producao up -d identity
docker compose logs --tail 50 identity
```

Enquanto a chave estiver vazia, o log mostra `E-mail desligado em producao; link ... nao enviado`
a cada cadastro ou "esqueci minha senha". Depois de ligar, essa linha some.

## 5. Conferir

1. **DNS**: abra cada link; a resposta precisa ter `"Answer"` com o valor que você cadastrou.
   - SPF da Resend: https://dns.google/resolve?name=send.piggu.com.br&type=TXT
   - DKIM: https://dns.google/resolve?name=resend._domainkey.piggu.com.br&type=TXT
   - DMARC: https://dns.google/resolve?name=_dmarc.piggu.com.br&type=TXT
   - MX (recebimento): https://dns.google/resolve?name=piggu.com.br&type=MX
2. **Envio de verdade**: em https://piggu.com.br/entrar, clique em **Esqueci minha senha** e use
   o seu e-mail. No Gmail, abra o e-mail, **⋮ > Mostrar original**: as linhas **SPF**, **DKIM** e
   **DMARC** precisam dizer **PASS**.
3. **Recebimento**: de outro e-mail, escreva para `privacidade@piggu.com.br`; precisa chegar no
   seu Gmail.

## Resumo do que fica no DNS

| Tipo | Nome | Para quê |
|---|---|---|
| MX | `send` | retorno de e-mails da Resend |
| TXT | `send` | SPF da Resend |
| TXT | `resend._domainkey` | DKIM da Resend |
| TXT | `_dmarc` | DMARC |
| MX ×3 | `@` | receber e-mail (Cloudflare Email Routing) |
| TXT | `@` | SPF do Email Routing |
