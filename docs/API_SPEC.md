# Contrato da API do Piggu

Guia para quem escreve o cliente do app nativo (iOS em SwiftUI e Android em Kotlin). O app
fala direto com a API, então a regra é uma só: **a API nunca remove nem renomeia campo, só
acrescenta**. O contrato de cada serviço fica em [`docs/api/`](api/), gerado do código (nunca
escrito à mão) e conferido pelos testes a cada PR.

## Endereços

| Ambiente | Endereço base |
|---|---|
| Produção | `https://piggu.com.br` (o Caddy repassa só `/api/*` ao gateway) |
| Local | `http://localhost:8080` (gateway; `cd backend && docker compose up`) |

Todas as rotas começam com `/api`. O gateway manda cada prefixo para um serviço
(`backend/piggu-gateway/src/main/resources/application.yml`):

| Prefixo de rota | Serviço | Contrato |
|---|---|---|
| `/api/auth`, `/api/me`, `/api/users`, `/api/family`, `/api/billing`, `/api/app` | identity | [`api/identity.yaml`](api/identity.yaml) |
| `/api/expenses`, `/api/monthly-goals`, `/api/piggy-bank`, `/api/notes`, `/api/products`, `/api/categories`, `/api/receipts`, `/api/exchange-rate`, `/api/incomes`, `/api/reports`, `/api/bills`, `/api/budgets`, `/api/accounts`, `/api/history`, `/api/assistant` | finance | [`api/finance.yaml`](api/finance.yaml) |
| `/api/coins`, `/api/prizes` | rewards | [`api/rewards.yaml`](api/rewards.yaml) |
| `/api/places`, `/api/movies`, `/api/shopping`, `/api/personal-goals`, `/api/girls-math` | lifestyle | [`api/lifestyle.yaml`](api/lifestyle.yaml) |
| `/api/assets`, `/api/feed` | media | [`api/media.yaml`](api/media.yaml) |
| `/api/banking` (Open Finance, opcional) | banking | [`api/banking.yaml`](api/banking.yaml) |

Ficam fora do contrato as rotas internas, que o app não alcança: `/.well-known/jwks.json`,
`/api/meus-dados` e `/api/alerts`. O `ContratoNoGatewayTest` confere que toda rota do contrato
passa pelo gateway e que `/v3/api-docs` e o Swagger UI não passam.

## Entrar e manter a sessão

São dois tokens. O **access token** é um JWT (RS256) curto, de 5 minutos (`expiresIn` diz
quantos segundos), e vai em toda chamada: `Authorization: Bearer <accessToken>`. O **refresh
token** é opaco, vale 30 dias, serve só para renovar e muda a cada renovação. No site ele mora
no cookie `piggu_refresh` (HttpOnly, Secure, SameSite=Strict, caminho `/api/auth`), e o
JavaScript nunca o vê.

**E-mail e senha**

1. Conta nova: `POST /api/auth/cadastro` responde `202` sem sessão. A pessoa confirma o
   e-mail pelo link (`POST /api/auth/confirmar-email`) e depois entra.
2. Entrar: `POST /api/auth/entrar`.

```http
POST /api/auth/entrar
Content-Type: application/json

{ "email": "ana@exemplo.com", "senha": "segredo123", "lembrar": true }
```

```http
HTTP/1.1 200 OK
Set-Cookie: piggu_refresh=...; Max-Age=2592000; Path=/api/auth; Secure; HttpOnly; SameSite=Strict
Content-Type: application/json

{ "accessToken": "<JWT>", "expiresIn": 300,
  "usuario": { "id": "…", "nome": "Ana", "role": "TITULAR", "plano": "PREMIUM", "…": "…" } }
```

**Google**: o app consegue um ID token do Google e manda em `POST /api/auth/google`
(`{ "idToken": "...", "versaoDosTermos": "...", "lembrar": true }`); a resposta é a mesma do
login por senha. O servidor só aceita ID tokens cuja audiência esteja em
`piggu.google.client-ids` (`GOOGLE_CLIENT_ID`): com o SDK nativo, peça o token para o client
ID web (`serverClientID` no iOS e no Credential Manager do Android) ou acrescente o client ID do
app nessa lista, separado por vírgula. O app atual, que abre o site num WebView, usa o fluxo pelo navegador
(`POST /api/auth/google/app` e `/api/auth/google/app/troca`, ver `LoginPeloNavegador`).

**Renovar**: `POST /api/auth/refresh` com o cookie devolve um par novo. Renove quando
faltar menos de um minuto para `expiresIn` acabar, ou quando uma chamada voltar `401`, e
repita a chamada uma vez. Se a renovação também der `401`, a sessão acabou: volte ao login.
**Sair**: `POST /api/auth/logout` (responde `204` e apaga o cookie).

**No app nativo** (D-058, D-072) não há cookie: o refresh viaja no corpo e o app o guarda no
Keychain (iOS) ou no Keystore (Android).

1. No login (`/api/auth/entrar`, `/api/auth/google` ou `/api/auth/apple`), mande
   `"refreshNoCorpo": true`. A resposta traz `refreshToken` no corpo e não tem `Set-Cookie`.
2. Para renovar, mande o refresh no corpo; o novo volta no corpo e o antigo deixa de valer:

```http
POST /api/auth/refresh
Content-Type: application/json

{ "refreshToken": "<refresh guardado>" }
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "accessToken": "<JWT>", "refreshToken": "<refresh novo>", "expiresIn": 300, "usuario": { "…": "…" } }
```

3. Para sair, `POST /api/auth/logout` com `{ "refreshToken": "..." }`. Ao criar ou trocar a
   senha (`PUT /api/auth/me/senha`), mande também `refreshToken`, senão a sessão do próprio
   aparelho cai junto com as outras.

O refresh que chega pelo cookie nunca volta no corpo, mesmo que o pedido traga `refreshNoCorpo`:
assim o site continua sem ver o refresh (D-014).

**Entrar com Apple** (`POST /api/auth/apple`): o app gera um nonce aleatório, passa o SHA-256
dele (em hexadecimal) para o `ASAuthorizationAppleIDRequest` e manda o nonce original ao servidor:

```json
{ "idToken": "<identityToken>", "nonce": "<nonce original>", "nome": "Ana Souza",
  "codigo": "<authorizationCode>", "versaoDosTermos": "...", "refreshNoCorpo": true }
```

Mande também o `authorizationCode` em `codigo`: o servidor o troca por um refresh token da Apple
e o revoga quando a conta for excluída, como a Apple exige (D-142). Sem `codigo`, o login funciona
igual, mas excluir a conta não revoga nada na Apple.

A Apple só entrega o nome na primeira autorização: mande `nome` nessa vez, que vira o apelido
da conta nova. A conta é achada pelo e-mail, como no Google; quem oculta o e-mail na Apple
ganha uma conta separada. Enquanto o servidor não tiver o Bundle ID em `APPLE_CLIENT_IDS`, a
rota responde `501` com o código `EM_BREVE`.

| Item | Situação |
|---|---|
| Refresh token no corpo da resposta e aceito no corpo de `/api/auth/refresh`, `/api/auth/logout` e `/api/auth/me/senha` | **Pronto** (26/10/2026) |
| Entrar com Apple | **Pronto** (26/10/2026). Revoga o token na Apple ao apagar a conta desde 29/10/2026 (D-142), quando o servidor tem `APPLE_TEAM_ID`, `APPLE_KEY_ID` e `APPLE_CHAVE` |
| Push no Android | **Pronto** (27/10/2026). `POST /api/auth/me/devices` aceita `plataforma: "ANDROID"` com o token do FCM (guardado como veio), e o lembrete diário envia pela API HTTP v1 do FCM quando o servidor tem `FCM_CONTA_DE_SERVICO`. Token que o FCM diz não existir mais é apagado, como no iPhone (D-139) |

## Erros

Todo erro dos serviços tem o mesmo corpo (`ApiError`), que no contrato aparece como a
resposta `default` de cada operação:

```json
{ "erro": "Confira o e-mail.", "codigo": "VALIDACAO",
  "campos": [ { "campo": "email", "mensagem": "Confira o e-mail." } ],
  "momento": "2026-10-25T23:09:08.123Z" }
```

`erro` já vem escrito para mostrar na tela. Decida pelo **status** e, quando precisar tratar
um caso específico, pelo **`codigo`**, nunca pelo texto. `campos` só vem preenchido em `VALIDACAO`.
Exceção: o `401` de token ausente, vencido ou inválido vem do Spring Security **sem corpo**
(só o cabeçalho `WWW-Authenticate`). E um `502`, `503` ou `504` do próprio gateway (serviço
fora do ar) pode vir com outro corpo: trate pelo status.

| Status | Quando |
|---|---|
| 200, 201, 202, 204 | Deu certo; o contrato diz qual cada rota usa |
| 400 | Corpo ou parâmetro inválido (`VALIDACAO`, `PEDIDO_INVALIDO`) |
| 401 | Sem sessão ou sessão vencida (renove), ou login recusado (`UnauthorizedException`) |
| 403 | Perfil sem permissão (`SEM_PERMISSAO`, `ForbiddenException`), CPF pendente, e-mail não confirmado |
| 404 | Registro ou endereço não existe (`NotFoundException`, `PEDIDO_INVALIDO`) |
| 405, 415 | Método ou tipo de conteúdo errado (`PEDIDO_INVALIDO`) |
| 409 | Conflito: já feito, ou outra alteração chegou antes (`CONFLITO`) |
| 413 | Corpo grande demais (`CORPO_GRANDE_DEMAIS`) |
| 422 | Regra de negócio recusou (padrão do `BusinessException`) |
| 429 | Muitas chamadas; espere os segundos do cabeçalho `Retry-After` |
| 500 | Falha interna (`ERRO_INTERNO`); o detalhe fica só no log |
| 501 | Ainda não disponível (`EM_BREVE`) |
| 502 | Serviço de fora falhou: Gemini, TMDB, Pluggy, câmbio (`UpstreamException`) |

Valores de `codigo` que existem hoje (procure por `BusinessException` no código):

| `codigo` | Status | O que fazer no app |
|---|---|---|
| `VALIDACAO` | 400 | Mostrar `campos` junto de cada campo |
| `PEDIDO_INVALIDO` | 400, 404, 405, 415 | Erro do cliente: corrigir a chamada |
| `SEM_PERMISSAO` | 403 | Esconder a ação para esse papel |
| `CPF_PENDENTE` | 403 | Levar para a tela de CPF (`PUT /api/me/cpf` ou `/api/me/pais`) |
| `CADASTRO_PENDENTE` | 403 | Falta apelido, nascimento, CPF ou celular (`usuario.cadastroCompleto` falso): levar para "Sobre você" (`PUT /api/me/sobre-voce`) e renovar o token depois (D-088) |
| `EMAIL_NAO_CONFIRMADO` | 403 | Pedir para confirmar o e-mail |
| `PLANO_PREMIUM` | 422 | Mostrar a oferta do Premium |
| `TERMOS_NECESSARIOS`, `AVISO_DESATUALIZADO` | 422, 409 | Mostrar os termos novos e aceitar (`PUT /api/me/termos`) |
| `CONSENTIMENTO_NECESSARIO` | 422 | Pedir a autorização (recibos e assistente usam o Gemini) |
| `CPF_NECESSARIO`, `CPF_EM_USO`, `PAIS_INVALIDO`, `TELEFONE_INVALIDO`, `IDADE_MINIMA` | 422, 409 | Cadastro e dados da conta |
| `SENHA_FRACA`, `SENHA_ATUAL_ERRADA`, `SENHA_TRAVADA` | 422, 429 | Senha |
| `LINK_INVALIDO`, `LINK_RECENTE` | 422, 429 | Links por e-mail (confirmar e-mail, redefinir senha) |
| `JA_E_DA_FAMILIA`, `JA_TEM_PARCEIRO`, `SEM_PARCEIRO`, `LIMITE_DE_CONVITES`, `LIMITE_DO_FAMILIA`, `ASSINATURA_ATIVA` | 409, 422 | Família e convites |
| `PRAZO_DE_REEMBOLSO`, `EM_BREVE` | 409, 501 | Premium |
| `JA_PAGA`, `JA_RECEBIDO` | 409 | Conta fixa e valor a receber já lançados |
| `CONFLITO` | 409 | Recarregar e tentar de novo |
| `CORPO_GRANDE_DEMAIS` | 413 | Reduzir a foto ou o arquivo |
| `LIMITE_DE_CHAMADAS` | 429 | Esperar o `Retry-After` |
| `ERRO_INTERNO` | 500 | Mensagem genérica |
| `BusinessException`, `NotFoundException`, `ForbiddenException`, `UnauthorizedException`, `UpstreamException` | 422, 404, 403, 401, 502 | Sem código próprio: vale o nome da classe; trate pelo status |

Código novo pode aparecer a qualquer momento: o app trata pelo status o que não conhece.

## Convenções

- **JSON** em UTF-8, nomes de campo em camelCase e em português (`valor`, `data`, `categoria`).
- **Dinheiro**: número JSON decimal, em reais (não em centavos), vindo de `BigDecimal`:
  `"valor": 12.5`. Leia como `Decimal` no Swift e `BigDecimal` no Kotlin, nunca `Double`.
  Outra moeda vem em `valorOriginal` e `moedaOriginal` (ISO 4217).
- **Data** sem hora: `"2026-10-25"` (`format: date`). **Instante**: ISO 8601 em UTC,
  `"2026-10-25T23:09:08.123Z"` (`format: date-time`). **Mês**: texto `AAAA-MM` nos
  parâmetros (`?mes=2026-10`).
- **Fuso**: o banco grava em UTC. "Este mês" e "hoje", quando o parâmetro falta, são os de
  Brasília (`America/Sao_Paulo`).
- **Ids**: UUID em texto. **Paginação**: não há. As listas vêm inteiras, quase sempre
  filtradas por mês; o histórico aceita `?limite=` (padrão 100, máximo 200).
- **Cabeçalhos**: `Authorization: Bearer ...` em toda rota que não é de login e
  `Content-Type: application/json` em todo corpo. Opcionais: `User-Agent` (fica na sessão,
  ex.: `Piggu-iOS/1.4.0`) e `X-Request-Id` (até 64 letras, números e `-`; volta na resposta).
- **Reenvio sem duplicar**: `POST /api/expenses` aceita `idDoEnvio` (UUID que o app gera uma
  vez por envio e guarda com a fila). Se a resposta se perder, mande de novo com o mesmo id: na
  mesma família nada é lançado de novo, e a resposta é o mesmo `201` com os gastos da primeira
  vez. Sem o campo, cada envio lança.
- **Limite de chamadas**: 600 por minuto por IP na API inteira (gateway). Nas rotas de
  login, cadastro e renovação, 20 por minuto por IP; nas outras rotas abertas, 120. Com
  sessão, por conta: 120 escritas, 15 envios de foto ou extrato e 60 buscas que custam por
  minuto. Corpo de até 256 KB, ou 8 MB nos envios de foto e extrato.

## Compatibilidade

1. Nunca remover nem renomear campo, rota ou valor de enum que o app já usa.
2. Campo novo é sempre opcional: no pedido, o servidor funciona sem ele; na resposta, o app
   antigo ignora. O cliente deve ignorar campo e valor de enum desconhecidos (no Kotlin,
   `ignoreUnknownKeys = true`).
3. Quando precisar quebrar, cria-se uma rota nova (ex.: `/api/expenses/v2`) e a antiga
   continua respondendo enquanto houver versão do app que a use.
4. **Aviso de "atualize o app"** (planejado, ainda não existe no código): a proposta é o app
   mandar a versão no `User-Agent` e, abaixo da versão mínima, a API responder `426` com o
   `codigo` `ATUALIZE_O_APP`. O app já pode tratar esse status com a tela de atualizar.

## Regravar o contrato

Mudou uma rota ou um DTO? O `ContratoTest` de cada serviço quebra com "o contrato da API
mudou". Regrave (precisa de Docker) e confira no diff que só entrou coisa nova:

```bash
cd backend
./scripts/regravar-contrato.sh            # todos os serviços
./scripts/regravar-contrato.sh finance    # só um
git diff ../docs/api
```

No PowerShell, o mesmo sem o script:
`mvn test "-Dtest=ContratoTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dpiggu.regravar-contrato=true"`.
Para ver o Swagger UI, suba o serviço com
`SPRING_PROFILES_ACTIVE=dev` e abra `http://localhost:<porta>/swagger-ui.html` (direto na
porta do serviço; em produção ele fica desligado e o gateway não roteia).

## Gerar o cliente (opcional)

**Swift** ([swift-openapi-generator](https://github.com/apple/swift-openapi-generator)):
copie o `.yaml` do serviço para `Sources/<Alvo>/openapi.yaml`, ao lado de um
`openapi-generator-config.yaml` com `generate: [types, client]` e
`accessModifier: internal`, e ligue o plugin `OpenAPIGenerator` no alvo. Use o
`swift-openapi-urlsession` como transporte e um middleware que põe o `Authorization`.

**Kotlin** ([openapi-generator](https://openapi-generator.tech)):

```bash
openapi-generator-cli generate -i docs/api/finance.yaml -g kotlin \
  --library jvm-retrofit2 --additional-properties=serializationLibrary=kotlinx_serialization \
  --package-name br.com.piggu.api.finance -o build/piggu-finance
```

Cada operação tem um `operationId` estável (recurso + ação, como `expenseListar`), que vira o
nome do método no cliente. São seis arquivos, um por serviço; todos usam o mesmo `ApiError`.
