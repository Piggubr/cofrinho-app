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
| `PIGGU_ADMIN_EMAILS` | e-mails de quem opera a instalação (viram ADMIN ao entrar) |
| `PIGGU_LIMITE_PROXIESNAFRENTE` | `2` com o Caddy na frente do gateway (limite de chamadas por IP lê o IP certo) |
| `CORS_ORIGINS` | só o domínio do site, com `https://` |
| `GOOGLE_CLIENT_ID` | login Google (identificador público, não é segredo) |
| `GEMINI_API_KEY`, `PLUGGY_*`, `STRIPE_*`, `TMDB_READ_TOKEN`, `DRIVE_*` | integrações; vazias, o recurso fica desligado |

Nenhuma delas vai para o Git: o `.env` está no `.gitignore` e o `.env.example` só tem
valores de desenvolvimento.

## 5. HTTPS e cabeçalhos

Ver `deploy/` (Caddy na frente do gateway, HTTPS automático, HTTP → HTTPS, cabeçalhos de
segurança) e `docs/revisao-seguranca-pre-publicacao.md`.
