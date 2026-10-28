# Colocar o Piggu em produção

Passo a passo do que precisa existir **fora do repositório** antes de publicar. Os
serviços conferem o essencial sozinhos: com `PIGGU_AMBIENTE=producao` eles **recusam
subir** se algum segredo estiver fraco, em vez de funcionar inseguro sem avisar.

## 1. Ambiente

```env
PIGGU_AMBIENTE=producao
```

Com isso ligado:

| Conferência | Onde | O que faz |
|---|---|---|
| Senha do banco | todos os serviços com banco (`ProtecaoDeProducao`) | recusa senha com menos de 16 caracteres ou conhecida (`piggu`, `postgres`...) |
| Chave JWT | identity (`TokenConfig`) | recusa a chave de desenvolvimento (`piggu-dev-*.pem`) |
| Chave que vazou | identity, em **qualquer** ambiente | recusa o par que esteve no Git até o commit `c5f54e1` |

## 2. Chave JWT nova

A chave privada assina todos os tokens: quem tem ela entra como qualquer pessoa. O par
que esteve no Git é público e está bloqueado no código. Gere um par novo **fora do
repositório**, numa máquina de confiança:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out piggu-prod-private.pem
openssl rsa -in piggu-prod-private.pem -pubout -out piggu-prod-public.pem
```

Passe as duas como segredo do servidor (nunca no Git, nunca no front):

```env
JWT_PRIVATE_KEY=file:/run/secrets/piggu-prod-private.pem
JWT_PUBLIC_KEY=file:/run/secrets/piggu-prod-public.pem
```

Trocar a chave derruba todas as sessões (todo mundo entra de novo). Faça isso se houver
qualquer suspeita de vazamento.

## 2b. Chave do CPF

O CPF de cada conta fica cifrado no banco (e o indice que impede duas contas com o mesmo
CPF e um HMAC). A chave fica fora do banco:

```bash
openssl rand -base64 32
```

```env
PIGGU_CPF_CHAVE=...
```

Sem ela, ou com a de desenvolvimento, o identity recusa subir em producao. **Guarde junto
com a senha do backup**: um backup restaurado sem a mesma chave nao le os CPFs, e trocar
a chave depois quebra o indice (o mesmo CPF poderia abrir outra conta).

## 3. Senha do Postgres

```bash
openssl rand -base64 24
```

Gere sete senhas: uma para `DB_PASSWORD`, a do superusuário `piggu` (backup,
restauração, administração), e uma para cada serviço: `DB_PASSWORD_IDENTITY`,
`DB_PASSWORD_FINANCE`, `DB_PASSWORD_REWARDS`, `DB_PASSWORD_LIFESTYLE`,
`DB_PASSWORD_MEDIA` e `DB_PASSWORD_BANKING`. Cada serviço entra com o próprio usuário
(`piggu_finance` e assim por diante), que só alcança o próprio banco: uma injeção de
SQL num serviço não chega às tabelas dos outros.

Coloque todas no `.env` **antes** da primeira subida: o Postgres grava a senha do
`piggu` no volume ao criar o banco. Para trocar a do `piggu` depois, rode
`ALTER USER piggu PASSWORD '...'` e atualize o `.env`. Para trocar a de um serviço,
mude no `.env`, recrie o postgres (`docker compose up -d postgres`) e rode
`docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh`.

**Instalação que já existia com um usuário só.** Depois de atualizar o código e o
`.env`, antes de subir os serviços:

```bash
docker compose up -d postgres
docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh
docker compose up -d
```

O script cria os usuários e passa para eles os bancos e as tabelas que eram do `piggu`.
Sem ele, os serviços não conseguem entrar no banco.

**Instalação que já tinha dados antes da troca do e-mail pelo id.** Desde esta versão,
os serviços marcam quem lançou pelo id da conta; o e-mail fica só no identity. Antes de
subir a versão nova, com o Postgres de pé:

```bash
backend/scripts/migrar-emails-para-ids.sh
docker compose up -d
```

O script copia o de-para (id e e-mail das contas) do identity para os outros bancos; na
subida, cada serviço troca o e-mail pelo id e apaga o de-para. Se esquecer o script, o
serviço com dados não sobe e o erro diz para rodá-lo; nada é perdido. Banco novo, vazio,
não precisa.

Em produção o banco não fica publicado: tire a linha `ports` do serviço `postgres` no
`docker-compose.yml` (os serviços falam com ele pela rede interna).

## 4. Demais variáveis

| Variável | Para quê |
|---|---|
| `DOMINIO`, `EMAIL_DO_CERTIFICADO` | domínio do site e e-mail de aviso do Let's Encrypt (serviço `web`) |
| `SITE_URL` | endereço do site com `https://` (volta da Stripe) |
| `PIGGU_ADMIN_EMAILS` | e-mails de quem opera a instalação (viram ADMIN ao entrar) |
| `PIGGU_LIMITE_PROXIESNAFRENTE` | `2` com o Caddy na frente do gateway (limite de chamadas por IP lê o IP certo) |
| `CORS_ORIGINS` | só o domínio do site, com `https://` |
| `GOOGLE_CLIENT_ID` | login Google (identificador público, não é segredo) |
| `GOOGLE_CLIENT_ID_ANDROID` | opcional: o ID OAuth do tipo Android do Google Cloud (pacote `br.com.piggu.app` e SHA-1 da chave de assinatura). Entra em `piggu.google.client-ids` ao lado do `GOOGLE_CLIENT_ID`; só é preciso se o app Android pedir o ID token com ele em vez do ID do site (público, não é segredo) |
| `APPLE_CLIENT_IDS` | Entrar com Apple no app nativo: o Bundle ID `br.com.piggu.app` (público, não é segredo); vazio deixa `POST /api/auth/apple` em 501 |
| `PIGGU_OPEN_FINANCE` | `false` (padrao desde 11/10/2026) esconde o card de bancos e o Open Finance da lista do Premium; `true` volta (com `PLUGGY_*` e o perfil `open-finance`) |
| `PIGGU_LEITOR_PROPRIO` | `false` (padrao desde 11/10/2026): recibo lido so pelo Gemini, 10 por mes por familia no gratuito; `true` volta o leitor proprio (Tesseract) primeiro |
| `GEMINI_API_KEY`, `PLUGGY_*`, `STRIPE_*`, `TMDB_READ_TOKEN`, `DRIVE_*` | integrações; vazias, o recurso fica desligado |
| `PIGGU_ASSISTENTE_PERGUNTAS_POR_MES` | perguntas ao assistente de IA por pessoa no mês (padrão 150); o assistente usa a mesma `GEMINI_API_KEY` |
| `PIGGU_EMAIL_API_KEY`, `PIGGU_EMAIL_REMETENTE` | avisos por e-mail (fatura, orçamento, conta do mês), pela API da Resend; o remetente precisa ser de um domínio verificado lá (ex.: `Piggu <nao-responda@piggu.com.br>`; SPF, DKIM e DMARC em [email-do-dominio.md](email-do-dominio.md)). Também levam o link de confirmar e-mail e o de "esqueci minha senha" |
| `APNS_TEAM_ID`, `APNS_KEY_ID`, `APNS_CHAVE`, `APNS_BUNDLE_ID` | push no iPhone: a chave `.p8` de "Apple Push Notifications service" criada em developer.apple.com (Keys), o Team ID, o Key ID e o bundle do app. `APNS_SANDBOX=true` para o app instalado pelo Xcode |
| `FCM_CONTA_DE_SERVICO` | push no Android: o JSON da chave da conta de serviço do projeto do Firebase, em uma linha (`jq -c . conta.json`) e entre aspas simples no `.env`. **Segredo.** A conta só precisa do papel "Firebase Cloud Messaging API Admin" |

Nenhuma delas vai para o Git: o `.env` está no `.gitignore` e o `.env.example` só tem
valores de desenvolvimento.

## 5. HTTPS e cabeçalhos

O Caddy (`deploy/`) é a única porta pública: tira e renova o certificado sozinho, manda
HTTP → HTTPS (308) e põe os cabeçalhos de segurança (HSTS, CSP, X-Frame-Options...).

```bash
cd frontend && npm ci && npm run build && cd ..
cd backend
# .env com DOMINIO=piggu.app, EMAIL_DO_CERTIFICADO=..., SITE_URL=https://piggu.app,
# CORS_ORIGINS=https://piggu.app, PIGGU_AMBIENTE=producao, PIGGU_LIMITE_PROXIESNAFRENTE=2
docker compose --profile producao up -d --build
```

O gateway fica publicado só em `127.0.0.1:8080`; de fora, a API chega pelo Caddy.
Com `PIGGU_AMBIENTE=producao`, os serviços recusam subir com `SITE_URL` ou
`CORS_ORIGINS` em `http://`. Revisão completa em `docs/revisao-seguranca-pre-publicacao.md`.

## 6. Backup diário testado

Sem backup, perder o disco da VPS é perder o dinheiro registrado de todas as famílias
(e a LGPD, art. 46, pede proteção contra perda). Os scripts ficam em `backend/scripts/`
e o CI testa todos eles num Postgres descartável a cada pull request.

| Script | O que faz |
|---|---|
| `backup.sh` | `pg_dump` dos seis bancos e dos papéis, mais as fotos do volume `piggu-fotos`, conferidos, num `piggu-AAAA-MM-DD.tar.gpg` cifrado. Guarda 7 diários e 4 semanais e manda uma cópia para fora da VPS |
| `testar-restauracao.sh` | restaura o backup mais recente em bancos à parte e confere, tabela por tabela, que voltaram as mesmas linhas da hora do backup. Depois apaga os bancos de teste |
| `restaurar.sh` | volta o backup por cima dos bancos de verdade (`--confirmo`) ou em bancos com prefixo (`--prefixo`) |

**Senha da cifra.** Gere uma vez e guarde também **fora da VPS** (no gerenciador de
senhas). Sem ela, o backup não abre:

```bash
sudo install -d -m 700 /etc/piggu
openssl rand -base64 32 | sudo tee /etc/piggu/backup.senha > /dev/null
sudo chmod 600 /etc/piggu/backup.senha
```

**Cópia fora da VPS.** O destino é qualquer remoto do [rclone](https://rclone.org). O
padrão sugerido é um bucket do **Backblaze B2** (barato, com cobrança por GB e sem taxa
de saída até 3x o armazenado). O Cloudflare R2 ou o S3 servem do mesmo jeito. Crie o
bucket com uma chave que só escreve nele e configure o remoto com `rclone config`
(nome `b2-piggu` no exemplo). O script só envia arquivo cifrado, recusa enviar sem
senha e nunca usa `rclone sync`: se o disco da VPS voltar vazio, a cópia de fora fica.

**Agendamento** (`sudo crontab -e`):

```cron
PIGGU_BACKUP_SENHA_ARQUIVO=/etc/piggu/backup.senha
PIGGU_BACKUP_DESTINO=b2-piggu:piggu-backups
# Todo dia às 3h15, e o teste de restauração todo domingo às 3h45.
15 3 * * *  /opt/piggu/backend/scripts/backup.sh >> /var/log/piggu-backup.log 2>&1
45 3 * * 0  /opt/piggu/backend/scripts/testar-restauracao.sh >> /var/log/piggu-backup.log 2>&1
```

As demais opções (pasta, quantos guardar, dia do semanal) estão no cabeçalho do
`backup.sh`. Vale ligar um aviso para quando o cron falhar, por exemplo um
[healthchecks.io](https://healthchecks.io) chamado no fim do comando com `&& curl -fsS ...`.

**Restaurar de verdade:**

```bash
cd /opt/piggu/backend
docker compose stop gateway identity finance rewards lifestyle media banking
PIGGU_BACKUP_SENHA_ARQUIVO=/etc/piggu/backup.senha \
  scripts/restaurar.sh /var/backups/piggu/diario/piggu-2026-10-10.tar.gpg --confirmo \
  --fotos "$(docker volume ls -q | grep -E '(^|_)piggu-fotos$')"
docker compose up -d
```

Se a VPS sumiu, baixe o arquivo do bucket (`rclone copy b2-piggu:piggu-backups/diario/piggu-AAAA-MM-DD.tar.gpg .`),
suba só o `postgres` numa máquina nova e rode o mesmo comando.

A restauração por cima devolve cada banco ao usuário do serviço (roda o
`01-criar-bancos.sh` no fim).

**Banco novo depois da primeira subida.** O `db/init/01-criar-bancos.sh` só roda
sozinho com o volume vazio. Ele é idempotente (só cria o que falta e acerta donos e
permissões), então, se um serviço novo ganhar banco, rode de novo:
`docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh`.

## 7. Logs

Cada container escreve no próprio stdout, e o Docker guarda com rotação (`x-logs` no
`docker-compose.yml`): no máximo 5 arquivos de 10 MB por serviço, 50 MB no total. Quando
o limite enche, o arquivo mais velho sai sozinho. Com o movimento de hoje, isso cobre
algumas semanas; com mais uso, cobre menos tempo, nunca mais espaço.

O que há nos logs: id da requisição, id da conta (o UUID, nunca e-mail) e o que deu
errado. Conteúdo de recibo, nome de item e e-mail não vão para o log (ver
`docs/relatorio-lgpd-seguranca.md`, item 5).

```bash
docker compose logs --since 2h finance          # um serviço
docker compose logs --since 30m | grep <requestId>  # uma requisição em todos
```

Com o monitoramento ligado (seção 9), os logs também vão para o Loki, no próprio
servidor, com retenção de **30 dias**, e dá para ler e procurar no Grafana. A rotação
local continua como está. Em um incidente, copie os arquivos antes de qualquer reinício
(`docker compose up --force-recreate` apaga os logs do container): eles são a prova do
que aconteceu.

## 8. Deploy automático pelo GitHub Actions

Depois da primeira subida feita à mão, cada merge na `main` atualiza o servidor
sozinho (as regras das branches estão em `docs/branches.md`): o CI roda e, se **todos** os jobs passarem, o job "Publicar no
servidor" entra por SSH e roda `deploy/atualizar.sh` com o commit do merge. O script
confere que o commit está na branch, baixa as imagens daquele commit, sobe os serviços e
espera todos ficarem saudáveis.

**Imagens prontas.** O job "Imagens Docker (Trivy e GHCR)" compila as imagens uma vez no
CI, passa o Trivy nelas e, no push da `main`, publica em
`ghcr.io/piggubr/cofrinho-app/<serviço>:<sha do commit>`. O servidor só baixa: não
compila mais o Java nem o site, e o que vai ao ar é exatamente o que o CI conferiu. O
token para baixar vai do job para o script pela entrada do SSH e morre quando o job
acaba. Se a imagem não existir no registro (um commit anterior a esta mudança, ou um
deploy à mão sem token com o pacote privado), o script compila no servidor, como antes.
As imagens são montadas para o processador do servidor, ARM (`linux/arm64`) na Oracle, e
não para o do CI, que é Intel. Se o servidor um dia for Intel, crie a variável
`PIGGU_PLATAFORMA` = `linux/amd64` em *Settings > Secrets and variables > Actions >
Variables*. Imagem de outro processador baixa, mas não roda; por isso o script confere e,
se não bater, compila no servidor em vez de subir.

**Rollback automático.** Se algum serviço não ficar saudável em 5 minutos, o script volta
sozinho para o commit que estava no ar (as imagens dele continuam no servidor) e o job
fica vermelho com "Rollback feito" no log. O banco não volta: o Flyway da versão anterior
ignora as migrações novas e sobe normalmente quando elas só acrescentam (tabela, coluna,
índice). Migração que apaga ou renomeia coluna precisa ser feita em dois deploys:
primeiro a versão que para de usar a coluna, depois a que a remove.

**Versões.** Depois de publicar, o job "Versao e notas" cria uma versão em *Releases*
(`v2026.10.22`, `v2026.10.22.2` se sair outra no mesmo dia) com as notas geradas a partir
dos PRs que entraram. É o histórico do que foi ao ar e quando.

A chave que o Actions usa só consegue rodar esse script (`command=` no
`authorized_keys`): quem a tivesse não abriria um terminal no servidor, só pediria para
publicar um commit que já está na branch.

**Antes de tudo, confira no servidor** (o script roda lá, então tudo isto precisa existir):

```bash
cd /opt/piggu && git remote -v      # um clone do repositório (não uma cópia de arquivos)
git fetch origin main               # precisa funcionar sem pedir senha (ver abaixo)
node -v && npm -v                   # só para compilar aqui quando não há imagem no registro
docker compose version              # e sobe os serviços com o perfil producao
whoami                              # na Oracle: "ubuntu" na imagem Ubuntu, "opc" na Oracle Linux
```

- **`git fetch` pediu usuário/senha?** O repositório é privado (D-069 em
  `docs/DECISIONS.md`), então o servidor precisa de uma *Deploy key* só de leitura,
  cadastrada em *Settings > Deploy keys*, com o `origin` por SSH:
  ```bash
  git -C /opt/piggu remote set-url origin git@github.com:Piggubr/cofrinho-app.git
  git -C /opt/piggu fetch origin main
  ```
- **`/opt/piggu` não é um clone?** Faça o clone
  (`git clone git@github.com:Piggubr/cofrinho-app.git /opt/piggu`, com a deploy key) e copie para dentro
  dele o que fica fora do Git: `backend/.env`, `backend/keys/` e o
  `docker-compose.override.yml`, se houver.
- **Porta 22:** o Actions entra de IPs que mudam. Na Oracle Cloud, a *Security List* (ou o
  NSG) da VCN precisa aceitar TCP 22 de `0.0.0.0/0`; com senha desligada no SSH
  (`PasswordAuthentication no`) só entra quem tem chave. Se o SSH estiver fechado só para o
  seu IP, o job falha com timeout.

**No servidor** (uma vez):

```bash
ssh-keygen -t ed25519 -f ~/actions-deploy -N "" -C github-actions
echo "command=\"/opt/piggu/deploy/atualizar.sh\",restrict $(cat ~/actions-deploy.pub)" >> ~/.ssh/authorized_keys
cat ~/actions-deploy                                          # segredo DEPLOY_SSH_KEY
echo "IP_DO_SERVIDOR $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)"   # segredo DEPLOY_HOST_KEY
rm ~/actions-deploy ~/actions-deploy.pub
```

(Troque `IP_DO_SERVIDOR` pelo IP público. A chave privada só existe no segredo do GitHub
depois do `rm`.)

**No GitHub:**

1. *Settings > Environments > New environment* `producao`. Em *Deployment branches and
   tags*, deixe só `main`. Se quiser aprovar cada publicação com um
   clique, marque *Required reviewers* e ponha você.
2. Nesse ambiente, três segredos: `DEPLOY_HOST` (o IP público), `DEPLOY_SSH_KEY` (o
   arquivo inteiro do `cat ~/actions-deploy`, com as linhas BEGIN e END) e
   `DEPLOY_HOST_KEY` (a linha do `echo`).
3. *Settings > Secrets and variables > Actions > Variables*: `DEPLOY_LIGADO` = `true`.
   Sem ela o job fica cinza (pulado), nunca vermelho.
4. Se o usuário do servidor não for `ubuntu` (na Oracle Linux é `opc`), crie também a
   variável `DEPLOY_USUARIO` com o nome dele.

**Testar a ligação sem publicar nada** (do seu computador, com a chave do
`cat ~/actions-deploy` salva num arquivo antes do `rm`). Um commit que não existe faz o
script buscar a `main` e parar sem mexer em nada:

```bash
ssh -i actions-deploy ubuntu@IP_DO_SERVIDOR 0000000000000000000000000000000000000000
```

Tem que aparecer "==> Buscando main" e depois "O commit ... nao esta em main; nada foi
feito.". Isso prova que o SSH entra, que a chave só roda o script e que o servidor busca o
código. Não teste com um commit de verdade da `main` enquanto ela ainda tiver o app antigo:
o script trocaria o servidor para ele.

**A primeira vez que a nova arquitetura chega na `main`:** o servidor hoje roda a
`feature/nova-arquitetura`. O caminho é PR da feature para a `develop`, depois PR da
`develop` para a `main` (o job "Origem do PR para a main" só aceita a `develop` ou
`hotfix/...`). No merge, o CI roda e o job publica sozinho; acompanhe em *Actions*. As
migrações de banco rodam na subida dos serviços: faça um backup antes desse primeiro
merge (seção 6).

Publicar de novo o mesmo commit (por exemplo, depois de mudar o `.env`): no Actions, abra
a última execução do CI na branch e use *Re-run all jobs*. Para publicar à mão, no
servidor: `/opt/piggu/deploy/atualizar.sh <sha>`.

Para voltar a uma versão anterior à mão: `/opt/piggu/deploy/atualizar.sh <sha antigo>` (o
sha precisa estar na branch; o de cada versão está em *Releases*). À mão não há os digests
que o CI manda, então ele usa as imagens daquele commit que já estão no servidor (as da
versão no ar e da anterior ficam guardadas) ou compila no servidor; a tag do registro nunca
é baixada, porque pode ter sido reescrita depois do CI. As migrações de banco não voltam
sozinhas; se a versão nova mudou o banco de um jeito que a antiga não entende, restaure o
backup da noite (seção 6).

**Pacotes no GitHub (uma vez, depois do primeiro push na `main` com o job de imagens):**
em *github.com/orgs/Piggubr/packages*, cada imagem (`cofrinho-app/identity`, `.../web`...)
aparece ligada ao repositório. O deploy funciona com elas privadas (usa o token do job).
Deixe as imagens privadas: o repositório é privado, e a imagem leva o código compilado.
Para baixar à mão, faça `docker login ghcr.io` antes.

O servidor guarda só as imagens da versão no ar e da anterior; as outras são apagadas a
cada deploy.

## 9. Monitoramento, alertas e painel analítico

Grafana, Prometheus e Loki rodam no próprio servidor, no perfil `observabilidade` do
Compose, sem porta pública: o Grafana só escuta em `127.0.0.1:3000` e abre por túnel SSH.
Como funciona por dentro: `docs/observabilidade.md`. Passo a passo para ligar e
acompanhar: o guia `guias/monitoramento-e-painel.md` da pasta do projeto.

No `.env`:

```env
PIGGU_OBSERVABILIDADE=true            # o deploy passa a subir o monitoramento junto
GRAFANA_SENHA=...                     # openssl rand -base64 18; sem ela o Grafana não sobe
DB_PASSWORD_ANALITICO=...             # usuário do painel analítico, só lê contagens
PIGGU_ALERTAS_EMAIL=voce@exemplo.com  # para onde vão os alertas
PIGGU_ALERTAS_REMETENTE=alertas@piggu.com.br   # domínio verificado na Resend
```

Depois, uma vez: `docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh`
(cria o usuário `piggu_analitico`) e
`docker compose --profile producao --profile observabilidade up -d`. São cerca de 1,4 GB de
memória a mais no total (tetos em `PIGGU_MEMORIA_<SERVIÇO>`).

