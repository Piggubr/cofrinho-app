# Piggu — Frontend em Angular

Migração do front do Piggu, que era HTML/CSS/JS puro com 21 arquivos de script e
variáveis globais compartilhadas, para uma aplicação Angular 22 com componentes
standalone, signals e rotas com carregamento sob demanda.

## Como rodar

```bash
npm install
npm start          # http://localhost:4200
```

O `proxy.conf.json` encaminha `/api` para o gateway em `http://localhost:8080`,
então **o backend precisa estar no ar** (`cd ../backend && docker compose up`).

```bash
npm run build      # build de produção
npm test           # testes unitários (Vitest)
npm run e2e        # ponta a ponta (Playwright), contra a demo
```

## Telas

Cada tela é uma rota carregada sob demanda (`app.routes.ts`): o app original entregava
tudo num `index.html` de 678 linhas, e o navegador baixava todas as abas mesmo abrindo
só uma.

Abertas, sem login:

| Rota | O que faz |
|---|---|
| `/entrar` | Entrar ou criar conta com e-mail e senha, ou pelo Google |
| `/privacidade` e `/termos` | Aviso de privacidade e termos de uso, para ler antes de criar a conta |
| `/confirmar-email` | Destino do link de confirmação do e-mail, que pode abrir em outro aparelho |

Com login, antes do resto (os guards levam para lá até a pessoa concluir):

| Rota | O que faz |
|---|---|
| `/aceite` | Aceitar termos novos |
| `/sobre-voce` | Como quer ser chamado e data de nascimento |
| `/cpf` | Informar o CPF (opcional, aqui e fora), oferecido pelo Perfil |

Dentro do app:

| Rota | O que faz |
|---|---|
| `/painel` | Cofrinho, limite do mês, gastos por categoria e saldo dos bancos conectados |
| `/gastos` | Lançar, editar, apagar, ler recibo por foto e importar extrato |
| `/receitas` | O que entrou no mês |
| `/relatorios` | Relatório do mês e do ano |
| `/planejar` | Atalhos para Orçamento e Contas do mês |
| `/metas` | Minhas metas: metas pessoais do mês |
| `/orcamentos` | Limite total do mês e limite por categoria |
| `/contas-fixas` | Contas do mês: o que vence todo mês, lançado no dia |
| `/contas-e-cartoes` | Cartões e bancos: contas, cartões e faturas |
| `/calendario` | Grade do mês com gastos e lembretes |
| `/compras` | Listas de compras e desejos, busca no catálogo |
| `/lugares` | Lugares visitados, com foto e marcadores |
| `/filmes` | Lista, busca no TMDB, sorteio e avaliação |
| `/feed` | Fotos do mês com legenda |
| `/premios` | Fofocoins, prêmios e resgates |
| `/girls-math` | O que a pessoa viu, quis e não comprou, e quanto isso deixou no bolso |
| `/familia` | O titular convida pelo e-mail, promove e remove; o membro pode sair |
| `/plano` | Premium (só ADMIN e TITULAR) |
| `/perfil` | Conta, preferências, privacidade e, para o admin, gestão de acessos |

Todo papel da família alcança todas as telas, cada um com os próprios dados; só o
Premium é do titular. O guard é conveniência de navegação: quem decide de verdade é o
backend, que recusa com 403 mesmo se a tela abrir.

## Decisões que valem explicação

### Dois tokens, escondidos do resto do app

O backend trabalha com um access token de 30 minutos e um refresh de 30 dias.
Só o [`AuthService`](src/app/core/auth/auth.service.ts) e o
[`authInterceptor`](src/app/core/auth/auth.interceptor.ts) sabem disso; as telas
só perguntam se há alguém logado e qual o perfil.

O interceptor anexa o token, e num 401 renova e repete a chamada uma vez. Duas
chamadas que vencem juntas compartilham **a mesma** renovação, via `shareReplay`:
o backend rotaciona o refresh a cada uso, então duas renovações em paralelo
invalidariam uma à outra e derrubariam a sessão. Esse caso tem teste.

### Moeda de cada pessoa

O app nasceu em euro, com o símbolo escrito à mão. Agora cada conta escolhe, no
perfil, a moeda em que vê os valores (qualquer ISO 4217 que a fonte de câmbio
conheça), a moeda para a qual a cotação do topo converte, e se essa cotação aparece.
A escolha fica no backend (`PUT /api/auth/me/preferences`) e chega junto com o
usuário no login. O pipe `moeda` e o `MoedaService` leem dali; trocar a moeda muda
só a exibição, os valores lançados não são convertidos.

### Bancos conectados (Open Finance)

O card "Contas bancárias" do painel usa o widget Pluggy Connect. O script da
Pluggy só é baixado quando o usuário clica em "Conectar banco", com versão fixa e
hash de integridade (`pluggy-connect.service.ts`): é ele que recebe a senha do
banco, então o navegador recusa qualquer versão diferente da conferida. A senha
vai direto do widget para a Pluggy; o Piggu só recebe o id da conexão. O card
carrega fora do `forkJoin` do painel, para que a Pluggy fora do ar não derrube o
resto. Cada pessoa vê os bancos que ela mesma conectou.

### Imagens por URL, não em base64

O Apps Script devolvia cada foto como data URL dentro do JSON. Base64 infla o
conteúdo em um terço e impede o navegador de guardar em cache. Agora a API
devolve um identificador e a imagem vem de `/api/assets/{id}/content`, com
`Content-Type` e cache de 30 dias — o `src` da tag `img` aponta direto para lá.

### Recibo passa por conferência

`POST /api/receipts/parse` não grava nada. Os itens que a IA encontrou ficam
editáveis na tela e só viram gasto quando a pessoa confirma. A IA erra, e um
gasto errado contamina a média de preços da memória de produtos.

### Lista de compras marca antes de confirmar

Riscar um item aplica a mudança na hora e desfaz se o servidor recusar. Esperar
a resposta para riscar deixaria a tela lenta justamente onde ela é mais usada:
de pé, no supermercado.

### Datas sem fuso

A API trabalha com dia civil (`2026-09-10`), não com instante. Converter por
`Date` e deixar o navegador aplicar fuso moveria um gasto do dia 1 para o dia 31
do mês anterior, dependendo de onde a pessoa está. As funções em
[`datas.ts`](src/app/core/ui/datas.ts) montam e leem o texto diretamente.

## Estrutura

```
src/app/
├── core/
│   ├── api/          Um serviço por domínio do backend, mais os modelos
│   ├── auth/         Login (senha ou Google), tokens, interceptor e guards
│   ├── banking/      Widget da Pluggy (Open Finance)
│   ├── config/       Configuração injetável (URL da API, client ID)
│   ├── nativo/       Só no app instalado: abas nativas, atalhos, push, ícone
│   ├── offline/      Fila de gastos lançados sem sinal e listas guardadas
│   ├── privacidade/  Versão vigente dos termos e pedido de consentimento
│   └── ui/           Pipes, datas, ícones, leitura de arquivo, mensagem de erro
├── layout/shell/     Cabeçalho, menu, busca global, assistente, lançamento rápido e tour
├── features/         Uma pasta por tela
├── demo/             API falsa em memória do modo demo (build `demo`)
└── testing/          Apoio dos testes: sessão falsa e montagem de tela
```

Os tipos em `core/api/models.ts` espelham os DTOs do backend e mantêm os nomes em
português. Traduzir aqui criaria um dicionário a mais para manter sincronizado a
cada campo que mudasse.

## Configuração

[`app-config.ts`](src/app/core/config/app-config.ts) expõe `APP_CONFIG` como
token injetável, com a URL da API e o client ID do Google. Fica assim, e não em
arquivo de environment, para que os testes troquem os valores sem recompilar.

Para apontar para outro backend, troque o alvo em `proxy.conf.json` (dev) ou
forneça outro `APP_CONFIG` em `app.config.ts` (produção).

## Testes

```bash
npm test                                   # Vitest, sem precisar de backend
npx ng test --watch=false --coverage       # com cobertura, em coverage/
npm run e2e                                # Playwright, contra o modo demo
```

Cada tela e cada serviço tem o seu `.spec.ts` ao lado. Os que guardam as regras mais
delicadas:

| Arquivo | O que protege |
|---|---|
| `auth.interceptor.spec.ts` | Anexa token, renova no 401, uma renovação para chamadas simultâneas, encerra sessão só quando o refresh é recusado (401 ou 403) |
| `token-storage.spec.ts` | Lembrar usa localStorage, não lembrar usa sessionStorage |
| `datas.spec.ts` | Chave de mês, data ISO sem fuso, grade do calendário |
| `mensagem-de-erro.spec.ts` | Mostra a mensagem do backend, nunca um objeto cru |
| `fila-de-gastos.spec.ts` | Gasto lançado sem sinal vai na ordem quando a rede volta; recusado sai da fila; de outra pessoa não é enviado |
| `pluggy-connect.service.spec.ts` | Widget da Pluggy abre com o token do backend; item conectado é registrado; fechar não registra |
| `dashboard.spec.ts` | Card de bancos só aparece com Open Finance ligado; fora do ar não quebra o painel |

A cobertura tem piso em `angular.json` (`coverageThresholds`), que só sobe.

## O que ainda falta

- **Academia.** Vivia só no `localStorage` do app antigo (`js/piggu-features.js`), sem
  backend. Como não há endpoint para ela, ficou de fora; as metas pessoais, que vinham
  do mesmo arquivo, já voltaram como `/metas`.
- **Telas de erro.** Um 403 ou um erro de rede aparece como aviso no topo da
  tela; não há página dedicada.

## Textos e tradução

Os textos ficam prontos para tradução com o i18n do próprio Angular, sem traduzir nada
ainda: nos templates, cada texto leva o atributo `i18n` (e `i18n-placeholder`,
`i18n-aria-label`... nos atributos); no TypeScript, ``$localize`...` ``. O idioma de origem
é `pt-BR` (`angular.json`). `npm run i18n` extrai tudo para um catálogo só,
`src/locale/messages.xlf`. Para um idioma novo: copiar para `messages.<idioma>.xlf`,
traduzir e declarar o locale em `angular.json`. Texto novo entra já marcado.

