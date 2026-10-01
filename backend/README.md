# Piggu — Backend em Java

Migração do backend do Piggu, que era um único arquivo Google Apps Script
(`Code.gs`, 1542 linhas) usando uma planilha Google como banco de dados, para
uma plataforma de microserviços em Java 21 com Spring Boot 3.5 e PostgreSQL.

## Os sete serviços

| Serviço | Porta | Banco | Responsabilidade |
|---|---|---|---|
| `piggu-gateway` | 8080 | — | Porta de entrada única, roteamento e CORS |
| `piggu-identity` | 8081 | `piggu_identity` | Login Google, contas, perfis, sessões, JWKS |
| `piggu-finance` | 8082 | `piggu_finance` | Gastos, metas, cofrinho, notas, memória de preços, câmbio, recibos |
| `piggu-rewards` | 8083 | `piggu_rewards` | Fofocoins, prêmios, resgates |
| `piggu-lifestyle` | 8084 | `piggu_lifestyle` | Lugares, filmes, listas de compras |
| `piggu-media` | 8085 | `piggu_media` | Fotos do feed e imagens dos lugares |
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

Para rodar um serviço isolado durante o desenvolvimento:

```bash
mvn install -DskipTests
SPRING_PROFILES_ACTIVE=dev java -jar piggu-identity/target/piggu-identity-1.0.0-SNAPSHOT.jar
```

## Decisões que valem explicação

### Por que um banco por serviço

Cada serviço tem o próprio banco, não apenas o próprio schema. É o que impede
um serviço de ler as tabelas do outro por atalho — a forma mais comum de um
conjunto de microserviços virar um monolito distribuído sem ninguém perceber.

### Por que quase não há chamadas entre serviços

Existe uma só: `lifestyle` chama `media` para guardar a foto de um lugar. Todo
o resto é independente. Isso não é acaso, é o critério que definiu as fronteiras:
as operações que precisam de transação ficaram juntas.

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

### Perfis de acesso

Os três perfis do original continuam: `ADMIN`, `BEATRIZ` e `FAMILIAR`.

O `FAMILIAR` alcança apenas o câmbio e o cofrinho, e no cofrinho vê somente os
próprios depósitos, com saldo igual ao próprio total. Essa regra vive no
`PiggyBankService`, não no controller, para valer em qualquer caminho de chamada.

A lista de e-mails autorizados, que eram constantes no código e exigiam
reimplantar o script para mudar, agora é a tabela `authorized_emails`.

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

A cotação mantém a escada de três degraus do original: cache de uma hora, depois
o último valor guardado marcado como desatualizado, depois um valor fixo de
emergência. A tela nunca fica sem um número.

## Mapa de migração

Cada ação do `Code.gs` e onde ela foi parar:

| Ação antiga | Agora |
|---|---|
| `auth` | `POST /api/auth/google` |
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
| `getExchangeRate` | `GET /api/exchange-rate` |
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

São 169 testes. Os que precisam de banco sobem um PostgreSQL de verdade via
Testcontainers e deixam o Flyway aplicar as migrations reais — então **é preciso
ter Docker rodando**. Não usamos H2: as migrations dependem de `jsonb`, arrays de
texto, índice GIN e `gen_random_uuid`, e um banco em memória fingindo ser Postgres
esconderia justamente os erros que queremos pegar.

O container é estático e compartilhado pelas classes de teste do módulo: só a
primeira paga os ~10s de startup, as demais rodam em milissegundos.

### O que a suíte protege

| Onde | Testes | Regra que não pode quebrar |
|---|---|---|
| `piggu-common` | 22 | Erro de negócio chega ao usuário; falha interna nunca vaza detalhe |
| `piggu-identity` | 18 | Rotação de refresh, revogação ao desativar conta, claims do token, recusa subir sem chave |
| `piggu-finance` | 58 | Saldo do cofrinho, média de preços, nota↔gasto, perfis na API |
| `piggu-rewards` | 13 | Saldo nunca negativo, resgate atômico, sem gasto duplo |
| `piggu-lifestyle` | 27 | Imagem só por HTTPS, notas por pessoa, marcadores válidos |
| `piggu-media` | 16 | Limite de 5 MB, base64 tolerante, arquivo não fica órfão |
| `piggu-banking` | 15 | Item só de quem o conectou, sincronização sem duplicar, apiKey reaproveitada, familiar sem acesso |

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

- **Cobertura de controllers.** Só o `piggu-finance` tem teste de API; nos outros
  serviços as regras de perfil estão cobertas apenas na camada de serviço.
- **Gateway sem teste.** O roteamento foi verificado à mão, com os sete processos
  no ar, mas não há teste automatizado das rotas.
- **Importação dos dados da planilha.** O banco sobe vazio. O histórico que já
  existe na planilha precisa de um importador.
