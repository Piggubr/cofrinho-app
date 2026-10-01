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
npm test           # 30 testes
```

## Telas

As onze abas do app antigo viraram rotas. Cada uma carrega sob demanda: o app
original entregava tudo num `index.html` de 678 linhas, e o navegador baixava as
onze mesmo abrindo só uma.

| Rota | O que faz | Perfis |
|---|---|---|
| `/entrar` | Login com Google | — |
| `/painel` | Cofrinho, meta do mês, gastos por categoria e saldo dos bancos conectados | todos |
| `/gastos` | Lançar, editar, apagar e ler recibo por foto | ADMIN, BEATRIZ |
| `/calendario` | Grade do mês com gastos e lembretes | ADMIN, BEATRIZ |
| `/compras` | Listas de compras e desejos, busca no catálogo | ADMIN, BEATRIZ |
| `/lugares` | Lugares visitados, com foto e marcadores | ADMIN, BEATRIZ |
| `/filmes` | Lista, busca no TMDB, sorteio e avaliação | ADMIN, BEATRIZ |
| `/feed` | Fotos do mês com legenda | ADMIN, BEATRIZ |
| `/premios` | Fofocoins, prêmios e resgates | ADMIN, BEATRIZ |
| `/metas` | Limite de gasto por mês | ADMIN, BEATRIZ |
| `/perfil` | Conta e, para o admin, gestão de acessos | todos |

O perfil `FAMILIAR` só enxerga `/painel` e `/perfil`; o menu esconde o resto.
Isso é conveniência de navegação — quem decide de verdade é o backend, que
recusa com 403 mesmo se a tela abrir.

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

### Bancos conectados (Open Finance)

O card "Contas bancárias" do painel usa o widget Pluggy Connect. O script da
Pluggy só é baixado quando o usuário clica em "Conectar banco", com versão fixa e
hash de integridade (`pluggy-connect.service.ts`): é ele que recebe a senha do
banco, então o navegador recusa qualquer versão diferente da conferida. A senha
vai direto do widget para a Pluggy; o Piggu só recebe o id da conexão. O card
carrega fora do `forkJoin` do painel, para que a Pluggy fora do ar não derrube o
resto. O perfil `FAMILIAR` não vê o card.

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
│   ├── auth/         Login Google, tokens, interceptor e guards
│   ├── config/       Configuração injetável (URL da API, client ID)
│   └── ui/           Pipes, datas, leitura de arquivo, mensagem de erro
├── layout/shell/     Cabeçalho, menu e área de conteúdo
└── features/         Uma pasta por tela
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
npm test
```

30 testes em Vitest, sem precisar de backend:

| Arquivo | O que protege |
|---|---|
| `auth.interceptor.spec.ts` | Anexa token, renova no 401, uma renovação para chamadas simultâneas, encerra sessão quando o refresh falha |
| `token-storage.spec.ts` | Lembrar usa localStorage, não lembrar usa sessionStorage |
| `datas.spec.ts` | Chave de mês, data ISO sem fuso, grade do calendário |
| `mensagem-de-erro.spec.ts` | Mostra a mensagem do backend, nunca um objeto cru |
| `moeda.pipe.spec.ts` | Formatação em euro |
| `pluggy-connect.service.spec.ts` | Widget da Pluggy abre com o token do backend; item conectado é registrado; fechar não registra |

## O que ainda falta

- **Testes de componente.** A cobertura está no núcleo (interceptor, storage,
  utilitários). As telas em si não têm teste.
- **PWA.** O app antigo tinha `manifest.webmanifest` e era instalável no iPhone.
  Isso não foi portado ainda.
- **Academia e metas pessoais.** Duas funcionalidades do app antigo viviam só no
  `localStorage`, sem backend (`js/piggu-features.js`). Como não há endpoint para
  elas, ficaram de fora — precisam primeiro existir na API.
- **Telas de erro.** Um 403 ou um erro de rede aparece como aviso no topo da
  tela; não há página dedicada.
