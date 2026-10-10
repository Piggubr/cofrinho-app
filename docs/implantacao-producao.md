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

## 3. Senha do Postgres

```bash
openssl rand -base64 24
```

Use o resultado em `DB_PASSWORD` **antes** da primeira subida: o Postgres grava a senha
no volume ao criar o banco. Para trocar depois, rode `ALTER USER piggu PASSWORD '...'`
no banco e atualize o `.env`.

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
| `GEMINI_API_KEY`, `PLUGGY_*`, `STRIPE_*`, `TMDB_READ_TOKEN`, `DRIVE_*` | integrações; vazias, o recurso fica desligado |

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

**Banco novo depois da primeira subida.** O `db/init/01-criar-bancos.sh` só roda
sozinho com o volume vazio. Ele é idempotente (só cria o que falta), então, se um
serviço novo ganhar banco, rode de novo:
`docker compose exec postgres bash /docker-entrypoint-initdb.d/01-criar-bancos.sh`.
