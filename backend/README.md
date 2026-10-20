# Piggu — Backend em Java

Migração do backend do Piggu, que era um único arquivo Google Apps Script
(`Code.gs`, 1542 linhas) usando uma planilha Google como banco de dados, para
uma plataforma de microserviços em Java 21 com Spring Boot 4.0 e PostgreSQL.

## Os sete serviços

| Serviço | Porta | Banco | Responsabilidade |
|---|---|---|---|
| `piggu-gateway` | 8080 | — | Porta de entrada única, roteamento e CORS |
| `piggu-identity` | 8081 | `piggu_identity` | Login por e-mail e senha ou Google, contas, famílias, sessões, JWKS, Premium (Stripe e RevenueCat), avisos por e-mail e push, exportar e apagar a conta |
| `piggu-finance` | 8082 | `piggu_finance` | Gastos, metas, cofrinho, notas, memória de preços, câmbio, recibos |
| `piggu-rewards` | 8083 | `piggu_rewards` | Fofocoins, prêmios, resgates |
| `piggu-lifestyle` | 8084 | `piggu_lifestyle` | Lugares, filmes, listas de compras, metas do mês e Girls Math (pessoais) |
| `piggu-media` | 8085 | `piggu_media` | Fotos do feed, dos lugares e do Girls Math |
| `piggu-banking` | 8086 | `piggu_banking` | Open Finance via Pluggy: bancos conectados, contas e saldos |

Mais o módulo `piggu-common`, uma biblioteca (não um serviço) com o tratamento
de erros, os tipos de segurança e os utilitários de texto que todos compartilham.

## Como subir

```bash
cd backend
cp .env.example .env     # preencha GOOGLE_CLIENT_ID
./scripts/gerar-chaves-dev.sh   # par RSA local, em backend/keys/ (fora do Git)
docker compose up --build
```

A API fica em `http://localhost:8080`. O Compose cria o Postgres, os seis
bancos e sobe os sete processos na ordem certa.

Para não começar com tudo vazio, entre uma vez pelo app com a sua conta Google (isso
cria a conta e a família) e grave os dados de exemplo nela:

```bash
./scripts/dados-de-exemplo.sh voce@gmail.com            # três meses de gastos, receitas, cofrinho...
./scripts/dados-de-exemplo.sh --premium voce@gmail.com  # e 30 dias de Premium
```

O script só roda numa família sem gastos e recusa `PIGGU_AMBIENTE=producao`. Os
dados ficam em `db/exemplo/`.

Para rodar um serviço isolado durante o desenvolvimento:

```bash
mvn install -DskipTests
SPRING_PROFILES_ACTIVE=dev java -jar piggu-identity/target/piggu-identity-1.0.0.jar
```

## Decisões que valem explicação

### Por que um banco por serviço

Cada serviço tem o próprio banco, não apenas o próprio schema. É o que impede
um serviço de ler as tabelas do outro por atalho — a forma mais comum de um
conjunto de microserviços virar um monolito distribuído sem ninguém perceber.

### Por que quase não há chamadas entre serviços

Nenhuma requisição comum passa de um serviço para outro, com uma exceção: `lifestyle`
chama `media` para guardar a foto de um lugar (ou de um item do Girls Math). As outras
chamadas saem do `identity`, fora do caminho do dia a dia:

- **Exportar e apagar a conta** (`CascataDeDados`): o identity chama `/api/meus-dados` em
  cada serviço com um token da própria pessoa. Qualquer falha aborta antes de a conta
  sair do identity.
- **Avisos do dia** (`AvisosDoFinanceiro`): o job diário pergunta ao finance o que avisar
  (fatura, orçamento, conta vencendo) antes de mandar e-mail ou push.

O endereço de cada serviço fica em `piggu.dados.servicos` no `application.yml` do
identity. Isso não é acaso, é o critério que definiu as fronteiras: as operações que
precisam de transação ficaram juntas.

O exemplo mais claro é a nota com valor, que cria um gasto vinculado. No Apps
Script eram duas escritas em abas diferentes, sem transação; uma falha no meio
deixava um gasto órfão. Como notas e gastos ficaram no mesmo serviço, hoje é
uma transação só. Se tivessem sido separados em "notas" e "gastos", seria
preciso uma saga para garantir a mesma coisa.

### Autenticação: dois tokens no lugar de um

O Apps Script emitia um único `session_token` de 30 dias, guardado como
propriedade do script, e o enviava no corpo de toda requisição. Expirar sessões
significava varrer todas as propriedades do script a cada login.

Agora são dois tokens com papéis distintos:

- **Access token**: JWT assinado em RS256, válido por 30 minutos, enviado no
  cabeçalho `Authorization`. Carrega id, e-mail e perfil.
- **Refresh token**: opaco, válido por 30 dias, usado só para renovar. Guardado
  no banco apenas como hash SHA-256, e rotacionado a cada uso.

Os serviços de domínio validam o access token sozinhos, contra a chave pública
publicada pelo identity em `/.well-known/jwks.json`. Nenhuma chamada ao identity
acontece no caminho de uma requisição comum.

O ID token do Google também passou a ser conferido localmente, contra o JWKS
público do Google, em vez de uma chamada HTTP ao endpoint `tokeninfo` a cada
login.

### Chaves de assinatura

O identity **recusa subir** sem `JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY` (PEM inteiro
ou caminho `file:`). Não existe valor padrão no `application.yml`: esquecer a
variável num deploy derruba a subida em vez de assinar tokens com uma chave
conhecida. O `TokenConfigTest` quebra se alguém reintroduzir um padrão.

No ambiente local, `./scripts/gerar-chaves-dev.sh` cria um par em `backend/keys/`
(ignorado pelo Git). O Compose monta essa pasta e o perfil `dev` aponta para ela.
Os testes usam um par próprio em `src/test/resources/keys/`, que nunca sai do
classpath de teste.

> A chave privada que ficou versionada em `src/main/resources/keys/` até esta
> mudança continua no histórico do Git e deve ser tratada como **vazada**: nunca a
> use em nenhum ambiente real. Gere o par de produção fora do repositório.

### Famílias e papéis

Cada pessoa tem conta própria e pertence a uma **família** (`households`). Cadastro
aberto: o primeiro login cria a conta e uma família nova, com a pessoa como
`TITULAR`. Quem foi convidado pelo titular (`POST /api/family/invites`, vale 7 dias)
entra na família dele como `MEMBRO`, e o titular pode promovê-lo a `PARCEIRO`.

### Entrar com e-mail e senha

A mesma conta entra por duas portas: e-mail e senha (`POST /api/auth/cadastro` e
`POST /api/auth/entrar`) ou Google (`POST /api/auth/google`). Quem já tem conta Google
cria uma senha no Perfil (`PUT /api/auth/me/senha`), que também serve para trocá-la
(aí a senha atual é pedida e as outras sessões caem).

- A senha fica em BCrypt (custo 12): pelo menos 8 caracteres, com letra e número, até
  72 bytes. Cinco erros seguidos travam a entrada por senha da conta por 15 minutos; o
  Google continua entrando. O limite por IP do login vale também para senha e cadastro.
- O cadastro pede o país onde a pessoa mora (lista ISO 3166, coluna `country`) e o
  documento de lá, conferido pelo dígito verificador e único entre as contas: no Brasil o
  CPF; na Argentina, Bolívia, Chile, Colômbia, Equador, Espanha, Itália, México, Paraguai,
  Peru, Portugal, Uruguai e Venezuela o número nacional de cada um
  (`DocumentosDeIdentidade`, colunas `id_doc_*`, cifrado como o CPF). Nos outros países
  não existe um número que dê para conferir e não seja sigiloso (o SSN americano, por
  exemplo), então a conta segue sem documento. Pelo Google, a tela do documento depois da
  entrada pede o país e o documento (`PUT /api/me/pais`).
- O cadastro por senha ainda não confirma o e-mail (`email_verified` falso). Enquanto
  isso, a conta não vira `ADMIN` pela `PIGGU_ADMIN_EMAILS`, não usa convite de família e
  não aceita convite. O primeiro login Google nesse e-mail confirma; se a conta tinha
  senha, ela é apagada e as sessões caem, para que quem cadastrou o e-mail de outra
  pessoa não fique dentro da conta dela.

| Papel | O que faz |
|---|---|
| `ADMIN` | Opera a instalação (`/api/users`); dentro da própria família vale como titular |
| `TITULAR` | Dono da família: convida, remove membros, assina o Premium, gerencia tudo dela |
| `PARCEIRO` | Promovido pelo titular: lança e edita como ele e os dois veem os dados um do outro, mas não mexe no plano, nos convites nem tira pessoas |
| `MEMBRO` | Convidado: usa todas as telas, cada um com os próprios dados |

Todo papel alcança todas as rotas de domínio; o que cada um vê é filtrado por dono
(`DonoAtual`, `CurrentUser.visiveis()`). Ficam restritos o Premium e a gestão da família
(titular) e o ajuste de Fofocoins e prêmios (titular e parceiro). `PermissoesDaApiTest` e
`CadaUmVeOSeuTest` provam essas regras.

O id da família vai no token (claim `familia`). Toda entidade de domínio tem a coluna
`household_id` marcada com `@TenantId`: o Hibernate filtra **toda** consulta pela
família do token e preenche a coluna ao gravar (`FamiliaAtual` no `piggu-common`).
Sem token, a sessão cai numa família inexistente e não vê nada; job que precisa
gravar diz para quem com `FamiliaAtual.como(familia, ...)`. Consulta nativa (SQL
puro) **não** passa pelo filtro. Os testes `IsolamentoEntreFamiliasTest` de cada
serviço provam que uma família nunca lê nem altera dado de outra.

O Premium é da família (`households.premium_until`): o titular assina e todos usam.

### Concorrência

O Apps Script usava `LockService`, que serializava o script inteiro. Aqui cada
caso é tratado onde importa:

- **Fofocoins e resgate de prêmios** rodam em isolamento `SERIALIZABLE`. São as
  únicas operações em que ler um saldo e gravar com base nele pode dar errado
  sob concorrência.
- **Memória de preços** virou uma linha por produto, então o próprio banco
  resolve. O original relia e reescrevia a planilha inteira a cada compra.
- **Saldo de Fofocoins** nunca é materializado; é sempre a soma do livro-razão.
  Corrigir um erro significa lançar o oposto, não editar o passado.

### Imagens

Os bytes continuam no Google Drive. O que mudou é que existe um registro de cada
arquivo (`assets`), com tipo, tamanho e dono — antes o id do Drive ficava solto
numa célula da planilha.

O acesso ao Drive está atrás da interface `StoragePort`. Trocar por S3 ou MinIO
é escrever um adaptador; nenhuma regra de negócio muda. Existe também um
adaptador local, usado automaticamente quando não há credenciais do Google, para
que o ambiente de desenvolvimento suba sem depender de conta de serviço.

As imagens são entregues como bytes, com `Content-Type` e cache de 30 dias, em
vez da data URL em base64 dentro do JSON que o Apps Script devolvia. Base64
inflava cada foto em um terço e impedia o navegador de guardar em cache.

### Logs

O gateway dá a cada chamada um `X-Request-Id`, repassado aos serviços e devolvido ao
front. Toda linha de log traz `req=` e `user=` (o UUID da conta, nunca o e-mail). Há uma
linha de acesso por requisição com o padrão da rota, e logs de negócio só com ids.
Detalhes e o porquê em [`docs/relatorio-lgpd-seguranca.md`](../docs/relatorio-lgpd-seguranca.md).

No Compose os serviços escrevem em JSON (ECS), que o Loki separa em campos. Métricas
(`/actuator/prometheus`), logs, erros do app, alertas e o painel analítico estão em
[`docs/observabilidade.md`](../docs/observabilidade.md).

### Erros

`BusinessException` carrega texto escrito para o usuário e vai inteiro para a
resposta. Qualquer outra exceção vira 500 com mensagem genérica, e o detalhe
real fica só no log. O corpo de erro é sempre o mesmo:

```json
{ "erro": "...", "codigo": "...", "campos": [], "momento": "..." }
```

## Integrações externas

As quatro primeiras foram portadas do Apps Script. Todas degradam com aviso claro em vez de quebrar:

| Integração | Variável | Sem ela |
|---|---|---|
| Gemini (leitura de recibos) | `GEMINI_API_KEY` | Avisa para adicionar os itens à mão |
| TMDB (filmes) | `TMDB_READ_TOKEN` | Avisa que a busca não está configurada |
| OpenFoodFacts (produtos) | — | Funciona sem credencial |
| Frankfurter (câmbio) | — | Cai para a última cotação conhecida |
| Pluggy (Open Finance) | `PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET` | Avisa que a conexão com bancos não está configurada |

### Open Finance (Pluggy)

**É opcional e vem desligado.** O serviço só sobe com `docker compose --profile open-finance up`,
e `GET /api/banking/status` só responde `habilitado: true` com `PLUGGY_CLIENT_ID` e
`PLUGGY_CLIENT_SECRET` preenchidos. O painel consulta esse status e esconde o card de
bancos quando está desligado ou quando o serviço nem está no ar.

O usuário conecta o banco no widget Pluggy Connect; a senha vai direto para a
Pluggy e nunca passa pelo Piggu. O fluxo:

1. `POST /api/banking/connect-token` — o backend troca `clientId`/`clientSecret`
   por uma apiKey (guardada por quase duas horas) e pede um connect token com o
   id do usuário como `clientUserId`.
2. O widget conecta o banco e devolve o id do item.
3. `POST /api/banking/items` — o backend busca o item na Pluggy e **confere que o
   `clientUserId` é do usuário logado** antes de gravar: o id vem do navegador e
   não prova nada sozinho. Em seguida grava as contas com saldo.
4. `GET /api/banking/accounts` lista do banco local; `POST /api/banking/sync`
   relê item e contas na Pluggy e remove as contas que sumiram.

A Pluggy atualiza os dados com o banco uma vez por dia. Forçar a ida ao banco na
hora (`PATCH /items/{id}` + webhook) ficou de fora. `PLUGGY_SANDBOX=true` mostra
os bancos de teste da Pluggy no widget, para desenvolver sem conta real.

A cotação aceita qualquer par de moedas ISO 4217 que a Frankfurter cubra; a
moeda de exibição e a de conversão são preferências de cada conta, gravadas no
identity (`PUT /api/auth/me/preferences`). A cotação mantém a escada de três degraus do original: cache de uma hora, depois
o último valor guardado marcado como desatualizado, depois um valor fixo de
emergência. A tela nunca fica sem um número.

## Mapa de migração

Cada ação do `Code.gs` e onde ela foi parar:

| Ação antiga | Agora |
|---|---|
| `auth` | `POST /api/auth/google` (ou `POST /api/auth/entrar`, por senha) |
| `getData` | Substituída por endpoints por recurso |
| `save` | `POST /api/expenses` |
| `updateExpense` | `PUT /api/expenses/{id}` |
| `deleteExpense` | `DELETE /api/expenses/{id}` |
| `setMonthlyGoal` | `PUT /api/monthly-goals` |
| `addDeposit` | `POST /api/piggy-bank/deposits` |
| `deleteDeposit` | `DELETE /api/piggy-bank/deposits/{id}` |
| `saveNote` / `deleteNote` | `POST` / `DELETE /api/notes` |
| `addCategory` | `POST /api/categories` |
| `parse` | `POST /api/receipts/parse` |
| `getExchangeRate` | `GET /api/exchange-rate?de=EUR&para=BRL` (qualquer par) e `GET /api/exchange-rate/currencies` |
| `adjustCoins` | `POST /api/coins/adjustments` |
| `savePrize` | `POST` / `PUT /api/prizes` |
| `redeemPrize` | `POST /api/prizes/{id}/redemptions` |
| `savePlace` / `deletePlace` | `POST` / `PUT` / `DELETE /api/places` |
| `addPlaceTag` | `POST /api/places/tags` |
| `getPlacePhoto` | `GET /api/assets/{id}/content` |
| `searchMovies` | `GET /api/movies/search` |
| `randomMovie` | `GET /api/movies/random` |
| `saveMovie` | `POST /api/movies` |
| `toggleMovieWatched` | `PATCH /api/movies/{id}/watched` |
| `rateMovie` | `PUT /api/movies/{id}/rating` |
| `deleteMovie` | `DELETE /api/movies/{id}` |
| `saveShoppingItem` | `POST /api/shopping/items` |
| `toggleShoppingItem` | `PATCH /api/shopping/items/{id}/purchased` |
| `deleteShoppingItem` | `DELETE /api/shopping/items/{id}` |
| `searchProducts` | `GET /api/shopping/catalog` |
| `saveFeed` | `POST /api/feed` |
| `updateFeed` | `PATCH /api/feed/{id}/caption` |
| `deleteFeed` | `DELETE /api/feed/{id}` |
| `getFeedPhoto` | `GET /api/assets/{id}/content` |

Seis dessas ações (`saveShoppingItem`, `updateExpense`, `deleteExpense`,
`adjustCoins`, `savePrize`, `redeemPrize`) existiam no `Code.gs` mas nenhum
arquivo JS do front as chamava. Foram portadas mesmo assim, porque o front
Angular vai precisar delas.

## Testes

```bash
mvn test          # a suíte inteira
mvn -pl piggu-finance test
```

Os testes que precisam de banco sobem um PostgreSQL de verdade via
Testcontainers e deixam o Flyway aplicar as migrations reais — então **é preciso
ter Docker rodando**. Não usamos H2: as migrations dependem de `jsonb`, arrays de
texto, índice GIN e `gen_random_uuid`, e um banco em memória fingindo ser Postgres
esconderia justamente os erros que queremos pegar.

O container é estático e compartilhado pelas classes de teste do módulo: só a
primeira paga os ~10s de startup, as demais rodam em milissegundos.

### O que a suíte protege

| Onde | Regra que não pode quebrar |
|---|---|
| `piggu-common` | Erro de negócio chega ao usuário; falha interna nunca vaza detalhe |
| `piggu-identity` | Rotação de refresh, revogação ao desativar conta, claims do token, recusa subir sem chave, preferências de moeda, perfis na API |
| `piggu-finance` | Saldo do cofrinho, média de preços, nota↔gasto, perfis na API, câmbio de qualquer par, chave do Gemini fora da URL |
| `piggu-rewards` | Saldo nunca negativo, resgate atômico, sem gasto duplo |
| `piggu-lifestyle` | Imagem só por HTTPS, notas por pessoa, marcadores válidos |
| `piggu-media` | Limite de 5 MB, base64 tolerante, arquivo não fica órfão, cada um vê as próprias fotos e nunca as de outra família |
| `piggu-banking` | Item só de quem o conectou, sincronização sem duplicar, apiKey reaproveitada, cada membro com os próprios bancos |
| `piggu-gateway` | Toda rota existe, atuador de rotas fechado, X-Request-Id validado, limite por IP |

### Três testes que merecem atenção

**`MigrationsTest`** existe por causa de um defeito real: três colunas foram
escritas como `CHAR` no SQL enquanto as entidades esperavam `VARCHAR`, e o
serviço só quebrava ao subir. O perfil de teste mantém `ddl-auto: validate`, então
qualquer divergência nova entre migration e entidade derruba o teste em vez do
serviço em produção.

**`ApiExceptionHandlerTest`** cobre o outro defeito encontrado: um `PATCH` num
endereço que só aceita `GET` voltava 500, porque o catch-all de `Exception`
engolia as exceções de protocolo do Spring MVC. Isso escondia do front que o erro
estava na chamada, e não no servidor.

**`ResgateConcorrenteTest`** é a justificativa do isolamento `SERIALIZABLE` em
`RewardsService`. Dois resgates simultâneos com saldo para apenas um: exatamente
um passa. Vale saber que este teste tem dentes — baixando o isolamento para
`READ_COMMITTED`, ele falha com "expected: 1 but was: 2", que é o gasto duplo
acontecendo de verdade.

### Tokens nos testes

`TokensDeTeste`, no módulo `piggu-testing`, monta o objeto `Jwt` direto, sem
assinar nada. Assim os testes dos serviços de domínio não dependem do serviço de
identidade nem de chaves em disco:

```java
mockMvc.perform(get("/api/expenses").with(TokensDeTeste.familiar()))
       .andExpect(status().isForbidden());
```

### Docker antigo demais

O cliente Docker que o Testcontainers usa negocia por padrão uma versão de API
anterior à 1.40, recusada por daemons recentes. O POM pai fixa `1.43` em
`docker.api.version` e injeta via Surefire, então isso já está resolvido.

## O que ainda falta

- **Importação dos dados da planilha.** O banco sobe vazio. O histórico que já
  existe na planilha precisa de um importador.
