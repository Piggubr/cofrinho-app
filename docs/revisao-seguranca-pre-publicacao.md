# Revisão de segurança antes da publicação

Data: 09/10/2026 · Branch: `feature/nova-arquitetura` · Escopo: backend (8 módulos), front
Angular, `deploy/`, `docker-compose.yml`, CI e histórico do Git.

Resumo: **nenhum bloqueio técnico** para publicar. Ficam **3 decisões suas** (seção 8)
antes de abrir o repositório ou subir em produção.

---

## 1. HTTPS e redirecionamento seguro

| O quê | Situação |
|---|---|
| Certificado | Caddy tira e renova sozinho (Let's Encrypt), volume `caddy-dados` guarda o certificado |
| HTTP → HTTPS | automático no Caddy (308) |
| HSTS | `max-age=31536000; includeSubDomains` |
| Porta pública | só o Caddy (80/443, perfil `producao`); gateway em `127.0.0.1:8080`, Postgres em `127.0.0.1:5432` |
| Conteúdo misto | CSP com `upgrade-insecure-requests` |
| Endereços do site | com `PIGGU_AMBIENTE=producao`, os serviços **não sobem** com `SITE_URL` ou `CORS_ORIGINS` em `http://` |
| Redirecionamento aberto | o front só sai do app para `https://*.stripe.com` (checkout/portal); URLs de volta da Stripe são montadas no servidor a partir de `SITE_URL` |
| Cookie de sessão | refresh em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` |

Commit: `4a8c9ac`.

## 2. Chaves e credenciais

- **Nenhum segredo no código nem no bundle do front.** Toda chave vem de variável de
  ambiente (`.env`, fora do Git): `GEMINI_API_KEY`, `STRIPE_*`, `REVENUECAT_*`,
  `PLUGGY_*`, `TMDB_READ_TOKEN`, `DRIVE_CREDENTIALS`, `JWT_PRIVATE_KEY`, `DB_PASSWORD`.
- O navegador só recebe o `GOOGLE_CLIENT_ID`, que é público por definição.
- **Gemini (D3):** nenhuma `GEMINI_API_KEY` encontrada no repositório, no histórico nem no
  build. A chave só existe no `.env` do servidor e é usada pelo `finance`.
- Histórico: a única chave privada que já passou pelo Git é a de desenvolvimento do início
  do projeto (`2b6c04d`). Ela foi trocada e está **bloqueada** no `TokenConfig`
  (`CHAVE_VAZADA`): não assina token em ambiente nenhum.
- `scripts/procurar-segredos.sh` varre árvore e histórico (usa o gitleaks se houver); o
  CI tem o job **Segredos (gitleaks)** com o histórico inteiro.
- `.gitignore` barra `.env*`, `*.pem`, `*.p12`, `*.jks`, `*.key` e JSON de credenciais.
- Em produção o serviço recusa subir com senha de banco fraca ou padrão, e o identity
  recusa a chave JWT de desenvolvimento.

Commit: `11188bb`.

## 3. Validação dos formulários

- Todo corpo de requisição passa por Bean Validation (`@Valid`), com tamanho máximo em
  todo texto, faixa em todo número, formato em moeda (ISO de 3 letras), mês (`AAAA-MM`),
  tipo de imagem e e-mail. Erro volta **400** com o campo; regra de negócio volta **422**.
- Foto em base64 até 5 MB (e o `media` confere o tipo real pelos primeiros bytes);
  extrato até 2 MB / 2.000 linhas; listas com tamanho máximo.
- Filme vindo do navegador passou a ser validado (antes só era cortado no serviço).
- No front, os campos de texto têm `maxlength` igual ao limite do servidor (o servidor é
  quem manda; o front só evita o erro).
- Consultas ao banco são JPA/JPQL com parâmetros; a busca global tira os curingas do
  `LIKE`. CSV exportado protege contra injeção de fórmula (`=`, `+`, `-`, `@`).

Commit: `93daf35`.

## 4. Spam e abuso

| Limite | Valor |
|---|---|
| Login e renovação, por IP | 20/min (IP lido da direita do `X-Forwarded-For`, não dá para forjar) |
| Rotas abertas (webhooks), por IP | 120/min, e os webhooks conferem assinatura |
| Escrita, por conta | 120/min |
| Envio de foto ou extrato, por conta | 15/min |
| Buscas que custam (TMDB, catálogo, câmbio, busca global, preço médio), por conta | 60/min |
| Corpo de requisição | 256 KB comum, 8 MB foto/extrato; Caddy corta em 10 MB |
| Leitura de nota por IA | 10 por mês por família no gratuito |
| Convites | 10 esperando resposta por família |

Só há login com Google: não existe cadastro por senha para robô criar conta em massa.
O app não manda e-mail, então não pode ser usado para disparar spam.

Limite conhecido: o contador é em memória por instância (com várias réplicas, o limite
real multiplica). Mover para Redis quando houver mais de uma réplica por serviço.

Commit: `667a0dc`.

## 5. Cabeçalhos de segurança

Conferidos com o Caddy de verdade (`caddy validate` e uma subida local servindo o build):

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; script-src 'self' https://accounts.google.com/gsi/client https://cdn.pluggy.ai; ...; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'; upgrade-insecure-requests
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=(), browsing-topics=()
Cross-Origin-Opener-Policy: same-origin-allow-popups
Cross-Origin-Resource-Policy: same-origin
X-Permitted-Cross-Domain-Policies: none
(sem cabeçalho Server)
```

- O `index.html` não tem script inline (a CSP não permite nenhum).
- `style-src 'unsafe-inline'` fica porque o Angular injeta o estilo de cada tela em
  `<style>`; não permite executar código.
- As respostas da API saem com `Cache-Control: no-store` (padrão do Spring Security).
- `preload` no HSTS ficou de fora: só vale depois de ter o domínio definitivo e de
  inscrever em hstspreload.org.

Commit: `cf03b2d`.

## 6. Dependências

- Front: `npm audit` **zerado**; Angular 22.2.2 (último). Majors novos de ferramentas de
  teste (TypeScript 7, Vitest 5, jsdom 30) ficaram para uma atualização própria: são só
  de desenvolvimento.
- Backend: Spring Boot **4.0.8** (último da linha 4.0) + Spring Cloud **2025.1.3** (último).
  O Boot 4.1 espera o Spring Cloud dele sair do milestone. Bibliotecas do Google
  atualizadas (API Client 2.9.1, Drive v3 de 2026-09).
- Imagens: Temurin 21 JRE Alpine rodando como usuário sem privilégio; Postgres 16; Caddy 2.
- Vigilância contínua: Dependabot (Maven e npm semanal, Actions e Docker mensal) e
  `npm audit --audit-level=high` no CI.

Commit: `5537408`.

## 7. Revisão final

### Páginas administrativas
- A única API de administração é `/api/users` (listar e mudar perfil), com
  `@PreAuthorize("hasRole('ADMIN')")` no controller inteiro. Não há tela de admin no front;
  as telas de titular têm guarda de rota, mas quem barra de verdade é o servidor.
- Quem vira ADMIN é definido por `PIGGU_ADMIN_EMAILS` no servidor, nunca pelo app.
- Não há Swagger, console H2, devtools nem rota de teste/debug no código de produção.

### Endpoints desnecessários ou internos
- **Actuator**: só `health` e `info`, sem detalhes (`show-details` padrão `never`). O Caddy
  só repassa `/api/*`, então o actuator não é alcançável de fora.
- `/.well-known/jwks.json`: chave **pública** do JWT; também fica fora do Caddy.
- `/api/meus-dados/**` (exportar e apagar por serviço): não passa pelo gateway; só o
  identity chama, pela rede interna, com token de exclusão de 5 minutos.
- Serviços (8081–8086) não publicam porta no Compose: só a rede interna fala com eles.

### Configurações
- `ddl-auto: validate` em todos os serviços (o esquema só muda por migração Flyway).
- Erro inesperado volta mensagem genérica; detalhe e stack só no log do servidor.
- Logs não gravam e-mail, token nem conteúdo de nota; cada requisição tem id de
  correlação.
- Isolamento entre famílias pelo `@TenantId` do Hibernate, com testes de isolamento em
  todo serviço (gastos, receitas, contas, faturas, regras, divisões, importação, busca).
- Retenção automática: refresh vencido, contas desativadas, fotos órfãs e dados de banco.
- CORS com lista fixa de origens (em produção, só `https://`).

### Dados sensíveis expostos — **atenção**
1. **`Code.gs`** (Apps Script original, mantido como referência) tem **9 e-mails reais** e
   o **ID da planilha** do controle antigo. Não é chave de acesso, mas aponta para dados
   financeiros pessoais.
2. **`V1__esquema_inicial.sql`** (identity) tem **5 e-mails reais** na carga inicial. A
   migração não pode ser editada (o Flyway confere o checksum em bancos já criados).
3. Os dois arquivos também estão no **histórico do Git** (12 commits).

## 8. O que depende de você antes de publicar

1. **Visibilidade do repositório**: enquanto `Code.gs` e a `V1` estiverem no histórico,
   mantenha o repositório **privado**. Para abrir, o caminho é reescrever o histórico
   (`git filter-repo`) trocando os e-mails, avisar quem tem clone e forçar o push — faço
   quando você decidir.
2. **Planilha antiga**: confira no Google Drive se o compartilhamento da planilha do
   `SHEET_ID` está como "Restrito" (não "qualquer pessoa com o link").
3. **Produção**: preencher o `.env` do servidor com `PIGGU_AMBIENTE=producao`, `DOMINIO`,
   `EMAIL_DO_CERTIFICADO`, `SITE_URL`/`CORS_ORIGINS` em `https://`, `DB_PASSWORD` forte e
   par JWT próprio (passo a passo em `docs/implantacao-producao.md`). Os serviços se
   recusam a subir se algum desses estiver inseguro.

Contato de privacidade usado no app: `privacidade@piggu.app` (constante no front). O
domínio `piggu.app` estava **livre** na consulta de 08/10/2026; `piggu.com` e
`piggu.com.br` têm dono.
