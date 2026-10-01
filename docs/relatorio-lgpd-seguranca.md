# Relatório de LGPD e segurança — Piggu (branch `feature/nova-arquitetura`)

Data: 2026-10-01 · Escopo: `backend/` (7 serviços Spring Boot), `frontend/` (Angular),
`docker-compose.yml`, configuração e dependências.

Severidade: **Crítica** (exploração direta, sem login) · **Alta** (vazamento ou acesso
indevido provável) · **Média** (risco real, exige condição extra) · **Baixa** (endurecimento).

Os itens marcados **Corrigido** foram resolvidos no commit "Corrige achados de segurança e
LGPD de severidade alta" desta branch. Os demais são recomendações.

## Resumo

| # | Severidade | Achado | Status |
|---|---|---|---|
| 1 | Crítica | Atuador `gateway` exposto sem autenticação na porta pública | Corrigido |
| 2 | Alta | Chave do Gemini na URL, que vai para o log em erros de rede | Corrigido |
| 3 | Alta | PostgreSQL publicado em todas as interfaces com senha padrão | Corrigido |
| 4 | Alta | Perfil FAMILIAR lê qualquer foto por id e envia arquivos | Corrigido |
| 5 | Alta | Dados pessoais em log (e-mail, conteúdo de recibo, nome de item) | Corrigido |
| 6 | Alta | Dependências com falhas conhecidas (Spring Boot 3.5.3, Angular 22.1) | Corrigido |
| 7 | Alta | Sem exportação nem exclusão dos dados pelo titular (LGPD art. 18) | Recomendação |
| 8 | Alta | Sem aviso de privacidade e sem base legal registrada para terceiros (Gemini, Pluggy, Google) | Recomendação |
| 9 | Média | Chave privada JWT antiga no histórico do Git | Recomendação |
| 10 | Média | Refresh token de 30 dias em `localStorage` | Recomendação |
| 11 | Média | Sem limite de tentativas em `/api/auth/google` e `/api/auth/refresh` | Recomendação |
| 12 | Média | E-mails reais de pessoas na migration versionada | Recomendação |
| 13 | Média | Sem política de retenção (contas desativadas, sessões, fotos, saldos) | Recomendação |
| 14 | Média | Fonte do Google carregada no `<head>` envia IP de todo visitante ao Google | Recomendação |
| 15 | Média | Sem cabeçalhos de segurança do front (CSP, HSTS, frame-ancestors) | Recomendação |
| 16 | Média | Token v3 do TMDB vai na URL | Recomendação |
| 17 | Média | Spring Boot 3.5 fora do suporte open source | Recomendação |
| 18 | Baixa | Tipo da imagem enviada não é conferido pelo conteúdo | Recomendação |
| 19 | Baixa | CORS com `allowCredentials: true` e `allowedHeaders: '*'` sem necessidade | Recomendação |
| 20 | Baixa | URI de erro 500 pode conter e-mail | Recomendação |
| 21 | Baixa | Imagens de lugares por URL externa revelam IP a terceiros | Recomendação |

---

## Corrigidos

### 1. Atuador `gateway` exposto sem autenticação — Crítica
- **Onde:** `backend/piggu-gateway/src/main/resources/application.yml:58` (antes `include: health,info,gateway`).
- **Problema:** o endpoint `/actuator/gateway` do Spring Cloud Gateway lista as rotas internas
  e aceita `POST /actuator/gateway/routes/{id}` + `/refresh` para **criar rotas novas**. O gateway
  é a única porta publicada (8080) e não tem autenticação. Qualquer pessoa na internet poderia
  criar uma rota para `http://postgres:5432`, para o actuator dos serviços internos ou para
  qualquer host (SSRF/proxy aberto).
- **Correção:** removido `gateway` da exposição (`include: health,info`). Se for preciso
  inspecionar rotas, expor só numa porta de gerência interna (`management.server.port`).

### 2. Chave do Gemini na URL e no log — Alta
- **Onde:** `backend/piggu-finance/src/main/java/com/piggu/finance/integration/GeminiReceiptReader.java:91`.
- **Problema:** a chave ia como `?key=` na URL. Em erro de rede, a mensagem da exceção do
  `RestClient` contém a URL completa, e a linha `log.error("Nao foi possivel falar com o Gemini", erro)`
  gravava a chave em texto claro no log.
- **Correção:** a chave vai no cabeçalho `x-goog-api-key`. **Ação:** se algum log de produção
  já registrou esse erro, gere uma chave nova no Google AI Studio.

### 3. PostgreSQL aberto na rede — Alta
- **Onde:** `backend/docker-compose.yml:18`.
- **Problema:** `"5432:5432"` publica o banco em todas as interfaces da máquina, com usuário
  `piggu` e senha padrão `piggu`. Num servidor com IP público, os seis bancos (gastos, e-mails,
  contas bancárias) ficam acessíveis.
- **Correção:** `"127.0.0.1:5432:5432"`. Em produção, remover a publicação e usar senha forte
  em `DB_PASSWORD`.

### 4. FAMILIAR alcançava qualquer foto — Alta
- **Onde:** `backend/piggu-media/src/main/java/com/piggu/media/api/AssetController.java:36`.
- **Problema:** o `AssetController` não tinha restrição de perfil. O feed é só de ADMIN/BEATRIZ,
  mas `GET /api/assets/{id}/content` entregava a foto a qualquer conta logada, e
  `POST /api/assets` aceitava upload do FAMILIAR.
- **Correção:** `@PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")` na classe. Teste em
  `piggu-media/.../api/PermissoesDaApiTest`.

### 5. Dado pessoal em log — Alta (LGPD art. 6º, III e VII)
- **Onde:**
  - `backend/piggu-identity/src/main/java/com/piggu/identity/domain/AuthService.java:147` — gravava o e-mail ao criar conta.
  - `backend/piggu-finance/src/main/java/com/piggu/finance/integration/GeminiReceiptReader.java:188` — gravava a resposta inteira do Gemini (itens, valores e loja do recibo).
  - `backend/piggu-finance/src/main/java/com/piggu/finance/domain/ProductMemoryService.java:57` — gravava o nome do item comprado.
- **Problema:** log é cópia do dado pessoal fora do banco, sem controle de acesso, sem
  exclusão quando o titular pede e frequentemente enviada a ferramentas de terceiros.
- **Correção:** os logs passam a usar id (conta, gasto) e tamanho do texto.

### 6. Dependências com falhas conhecidas — Alta
- **Onde:** `backend/pom.xml:10` e `:37`; `frontend/package.json`.
- **Problema:** Spring Boot 3.5.3 e Spring Cloud 2025.0.0 estavam 13 e 3 versões de correção
  atrás. `npm audit` apontava 1 alta (`@angular/router` < 22.2.0, GHSA-ff3f-86qr-9cv3) e 2
  moderadas (`fast-uri`, `ip-address`).
- **Correção:** Spring Boot 3.5.16, Spring Cloud 2025.0.3, Angular 22.2.1. `npm audit`: 0
  vulnerabilidades. Suíte inteira verde depois da troca.

---

## Recomendações

### 7. Direitos do titular: exportar e apagar — Alta (LGPD art. 18, II, V e VI)
- **Onde:** não existe endpoint. `UserAdminService.revogar` (`backend/piggu-identity/.../UserAdminService.java:80`)
  só desativa a conta; gastos, notas, fotos, lugares, Fofocoins e contas bancárias continuam
  apontando para o e-mail.
- **Problema:** a pessoa não consegue obter uma cópia nem pedir a eliminação dos próprios dados.
- **Correção:** `GET /api/me/export` em cada serviço (JSON do que é dela) agregado no identity,
  e `DELETE /api/me` com exclusão em cascata entre serviços. O Piggu Kids já tem o padrão
  "cascata com o token do titular" que pode ser copiado. Registros compartilhados (gasto
  lançado por um e visto por todos) devem ser anonimizados, não apagados.

### 8. Aviso de privacidade e base legal para terceiros — Alta (LGPD art. 7º, 9º e 33)
- **Onde:** não existe aviso de privacidade no front. Terceiros que recebem dado pessoal:
  - Google (login: e-mail, nome, foto; Drive: fotos);
  - Google Gemini (foto do recibo, `GeminiReceiptReader.java:111`) — dado financeiro e transferência internacional;
  - Pluggy (contas e saldos bancários) — dado financeiro sensível no sentido prático;
  - TMDB e OpenFoodFacts (termos de busca), Frankfurter (nenhum dado pessoal).
- **Problema:** sem aviso, o titular não sabe quem recebe o quê; Gemini e Pluggy envolvem
  dados financeiros e não há registro de consentimento.
- **Correção:** página de privacidade linkada no login; consentimento explícito na hora do ato
  (primeira leitura de recibo; antes de abrir o widget da Pluggy), gravado com data e versão do
  texto. Avaliar se o plano do Gemini usado não treina com os dados enviados.

### 9. Chave privada JWT antiga no histórico do Git — Média
- **Onde:** `backend/piggu-identity/src/main/resources/keys/piggu-dev-private.pem` (removida no commit `c5f54e1`, mas no histórico).
- **Problema:** qualquer pessoa com acesso ao repositório consegue assinar tokens válidos para
  qualquer conta de um ambiente que use essa chave.
- **Correção:** já não há fallback para ela. Gerar par novo para produção (`JWT_PRIVATE_KEY`/
  `JWT_PUBLIC_KEY`) e nunca reutilizar a antiga. Opcional: reescrever o histórico.

### 10. Refresh token em `localStorage` — Média
- **Onde:** `frontend/src/app/core/auth/token-storage.ts:27-28`.
- **Problema:** com "lembrar acesso", o refresh de 30 dias fica em `localStorage`. Qualquer XSS
  (inclusive via script de terceiro comprometido) leva uma sessão de um mês.
- **Correção:** refresh em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, emitido
  pelo identity; o front guarda só o access token em memória. Enquanto isso, CSP (item 15)
  reduz a superfície.

### 11. Sem limite de tentativas no login e na renovação — Média
- **Onde:** `backend/piggu-identity/src/main/java/com/piggu/identity/api/AuthController.java:39` e `:45`.
- **Problema:** força bruta em refresh é inviável (256 bits), mas os endpoints públicos aceitam
  volume ilimitado — custo de CPU (verificação RS256) e de banco, e base para DoS.
- **Correção:** `RequestRateLimiter` do gateway (precisa de Redis) ou um limitador em memória
  por IP de origem no identity, com `trusted-proxies` configurado para o `X-Forwarded-For`
  (padrão já registrado no Piggu Kids).

### 12. E-mails reais na migration — Média (LGPD art. 6º, III)
- **Onde:** `backend/piggu-identity/src/main/resources/db/migration/V1__esquema_inicial.sql:49-54`.
- **Problema:** cinco e-mails pessoais e seus perfis estão versionados no Git e iguais em todo
  ambiente (dev, teste, produção).
- **Correção:** não editar a V1 (quebra o checksum do Flyway). Criar V3 que apaga essas linhas
  e passar a semear via variável (`PIGGU_ADMIN_EMAILS`) na subida, só onde for preciso.

### 13. Sem política de retenção — Média (LGPD art. 15 e 16)
- **Onde:** contas desativadas (`UserAdminService.java:86`), `refresh_sessions.user_agent`
  (`V1__esquema_inicial.sql:39`), fotos no Drive, `bank_accounts.balance`.
- **Problema:** nada é apagado por tempo; conta revogada guarda tudo indefinidamente.
- **Correção:** definir prazos (ex.: conta desativada há 12 meses → anonimizar; banco
  desconectado → apagar contas na hora) e um job agendado que aplica.

### 14. Google Fonts no front — Média
- **Onde:** `frontend/src/styles.scss:7`.
- **Problema:** cada abertura do app entrega IP e navegador de quem usa ao Google, antes de
  qualquer login ou aviso. Há decisão judicial europeia (2022) condenando exatamente isso.
- **Correção:** servir a fonte Sora do próprio app (um arquivo variável `woff2`, recorte latin)
  em `public/fonts/` e trocar o `@import` por `@font-face` local.

### 15. Cabeçalhos de segurança do front — Média
- **Onde:** o build do Angular não define cabeçalhos; não há servidor web no repositório.
- **Problema:** sem CSP, um XSS executa qualquer script e envia tokens a qualquer host.
- **Correção:** no servidor que entregar o front, `Content-Security-Policy` com `default-src 'self'`,
  `script-src 'self' https://accounts.google.com https://cdn.pluggy.ai`, `frame-src` para Google
  e Pluggy, `connect-src 'self'`; mais `Strict-Transport-Security`, `Referrer-Policy: no-referrer`
  e `frame-ancestors 'none'`.

### 16. Token v3 do TMDB na URL — Média
- **Onde:** `backend/piggu-lifestyle/src/main/java/com/piggu/lifestyle/integration/TmdbClient.java:120`.
- **Problema:** mesmo caso do item 2: com a chave curta (v3), ela vai como `api_key=` e aparece
  no log em erro de rede.
- **Correção:** usar o token longo (v4) em `TMDB_READ_TOKEN`, que já vai no cabeçalho
  `Authorization`; documentar no `.env.example` e, se quiser, recusar a chave v3.

### 17. Spring Boot 3.5 fora do suporte open source — Média
- **Onde:** `backend/pom.xml:10`.
- **Problema:** a linha 3.5 não recebe mais correções públicas; a atual é a 4.1.
- **Correção:** planejar a migração para Spring Boot 4.x / Spring Cloud 2025.1. Manter o
  Dependabot (ou Renovate) ligado para Maven e npm.

### 18. Tipo da imagem não conferido pelo conteúdo — Baixa
- **Onde:** `backend/piggu-media/src/main/java/com/piggu/media/domain/AssetService.java:48`.
- **Problema:** o tipo vem do cliente; bytes que não são imagem são gravados como `.jpg`. O
  Spring Security já manda `X-Content-Type-Options: nosniff`, o que impede o navegador de
  executar o arquivo.
- **Correção:** conferir os primeiros bytes (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`).

### 19. CORS mais aberto que o necessário — Baixa
- **Onde:** `backend/piggu-gateway/src/main/resources/application.yml:18-19`.
- **Problema:** a API usa `Authorization: Bearer`, não cookies; `allowCredentials: true` e
  `allowedHeaders: '*'` não são necessários. As origens já são lista fixa por `CORS_ORIGINS`.
- **Correção:** `allowCredentials: false` (ou manter só se o item 10 for feito com cookie) e
  `allowedHeaders: [Authorization, Content-Type, X-Request-Id]`.

### 20. URI no log de erro 500 — Baixa
- **Onde:** `backend/piggu-common/src/main/java/com/piggu/common/error/ApiExceptionHandler.java:94`.
- **Problema:** `DELETE /api/users/authorized-emails/{email}` leva o e-mail no caminho; se der
  500, ele vai para o log.
- **Correção:** registrar o padrão da rota (`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`)
  em vez da URI concreta.

### 21. Imagens de lugares por URL externa — Baixa
- **Onde:** lugares aceitam endereço `https://` de imagem (lifestyle).
- **Problema:** quem cadastra escolhe o host; os outros membros, ao abrir a tela, entregam IP a
  esse host.
- **Correção:** baixar e guardar a imagem no `piggu-media` no momento do cadastro.

---

## O que está bem

- Login Google conferido localmente (emissor, audiência, expiração, `email_verified`).
- Access token RS256 de 30 min; refresh opaco de 30 dias, guardado só como SHA-256 e rotacionado.
- Desativar conta derruba as sessões; validação de entrada com Bean Validation e erros sem
  detalhe interno.
- Open Finance: senha do banco nunca passa pelo Piggu; o dono do item é conferido pelo
  `clientUserId`; widget com versão fixa e SRI; desligado por padrão.
- Identity recusa subir sem chave JWT própria; segredos só por variável de ambiente.

## Logs e correlação

- O gateway gera ou repassa `X-Request-Id` (até 64 caracteres `[A-Za-z0-9-]`; qualquer outra
  coisa é trocada, para ninguém injetar linha falsa no log) e o devolve na resposta.
- Cada serviço põe `requestId` e o id do usuário (`userId`, UUID) no MDC; o padrão de log
  mostra os dois em toda linha. Nunca e-mail, nome ou token. O lifestyle repassa o mesmo id
  ao chamar o media.
- Log de acesso por requisição com o **padrão** da rota (`/api/expenses/{id}`), não o caminho
  concreto, status e duração.
- Logs de negócio novos, só com ids e quantidades: login e login recusado, conta criada,
  alterada e revogada, e-mail liberado (sem o e-mail), gastos lançados/editados/apagados,
  depósito, meta, Fofocoins, resgate, prêmio desativado, banco sincronizado e item Pluggy de
  outra conta recusado. Falhas de integração externa já eram registradas.
