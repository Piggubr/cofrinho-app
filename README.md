# Piggu — O cofrinho dos fofos

Aplicação de controle financeiro doméstico, migrada de um Google Apps Script com
planilha como banco de dados para uma plataforma em Java e Angular.

## O que tem aqui

| Pasta | O que é |
|---|---|
| [`backend/`](backend/) | Sete microserviços em Java 21 com Spring Boot 4.0 e PostgreSQL |
| [`frontend/`](frontend/) | Aplicação Angular 22 com componentes standalone e signals |
| [`frontend/ios/`](frontend/ios/) | App iOS (Capacitor), com a barra de abas nativa no estilo Liquid Glass ([detalhes](frontend/ios/LIQUID-GLASS-NATIVO.md)) |
| [`frontend/android/`](frontend/android/) | App Android (Capacitor); o APK da demo sai do workflow `android-demo` |
| [`deploy/`](deploy/) e [`docs/`](docs/) | Caddy com HTTPS, guia de produção, plano de incidente, LGPD e planos Premium |
| [`backend/observabilidade/`](backend/observabilidade/) | Grafana, Prometheus e Loki no próprio servidor: performance, logs, erros, alertas e painel de uso ([como funciona](docs/observabilidade.md)) |
| `assets/` | Material de marca (logos, ícones, ilustrações) do front original |

## O que o app faz

- **Família:** cadastro aberto, convite por e-mail e papéis ADMIN, TITULAR, PARCEIRO e
  MEMBRO, com os dados de cada família isolados no banco.
- **Dinheiro:** gastos (inclusive em outra moeda e divididos na família), receitas,
  contas fixas com lançamento automático, contas e cartões com fatura e parcelamento,
  metas, orçamento por categoria com alerta em 80% e 100%, regras de categoria,
  relatório do mês e do ano, importação de OFX/CSV sem duplicar e exportação em CSV.
- **Cofrinhos por objetivo:** nome, quanto juntar e até quando; o Piggu diz quanto
  guardar por mês. Um no gratuito, quantos quiser no Premium.
- **Bancos:** saldo pelo Open Finance (Pluggy) e os débitos dos últimos 30 dias
  trazidos como gastos, revistos antes de gravar e sem repetir; sempre com
  consentimento e com desconexão grátis.
- **Avisos:** fatura chegando, orçamento em 80% e 100% e conta do mês vencendo, por
  e-mail ou notificação no iPhone, uma vez por dia e sem repetir; cada canal é escolha
  da pessoa no perfil.
- **Sem sinal:** instala como app pelo navegador (PWA) e, com "manter conectado", abre
  sem internet; o gasto lançado sem sinal fica guardado no aparelho e vai sozinho
  quando a conexão volta.
- **Assistente de IA (Premium):** perguntas como "quanto gastei com mercado?" ou
  "vou estourar algum orçamento?", respondidas com os números da família pelo Gemini.
  Só lê, pede autorização no primeiro uso, não guarda a conversa e tem limite mensal.
- **Atalhos:** "Lançar gasto" no toque longo do ícone (iPhone e Android), pela Siri
  ("Lançar gasto no Piggu", com ou sem o valor) e por links `piggu://gastos?rapido=1`.
- **Notas fiscais:** leitura por OCR próprio e, no Premium, pelo Gemini.
- **Estilo de vida:** compras com histórico de preço, filmes, lugares, fotos e prêmios.
- **Premium:** Stripe no site e RevenueCat nas lojas, com 7 dias de teste
  ([planos](docs/planos-premium.md)).
- **Privacidade:** exportar e excluir a conta, aceite dos termos gravado com data e
  versão, retenção automática, sessão com refresh token em cookie HttpOnly e aviso de
  cookies no site (medição de uso só com permissão).
- **Senha esquecida:** link de 1 hora no e-mail ("Esqueci minha senha" no login); trocar
  derruba as sessões abertas. O e-mail do domínio (SPF, DKIM, DMARC) está em
  [docs/email-do-dominio.md](docs/email-do-dominio.md).
- **Monitoramento:** painéis de saúde e performance, erros (do servidor e do app) e uso
  do app no Grafana, alertas por e-mail, logs por 30 dias e limite de chamadas por IP,
  tudo no próprio servidor e sem dado pessoal ([detalhes](docs/observabilidade.md)).
- **Visual:** paleta Rosé suave com modo escuro, vidro no celular e busca global
  (Ctrl+K). Telas próprias para página não encontrada (404), erro (500) e sem internet,
  listas vazias que dizem o que fazer e carregamento com aviso de conexão lenta. Há um modo demo, sem backend, para mostrar o app
  ([app iOS de demonstração](docs/app-ios-demo.md); no Android, o APK do workflow
  `android-demo`).

## Subindo tudo

```bash
# 1. Backend (Postgres + os sete serviços)
cd backend
cp .env.example .env        # preencha GOOGLE_CLIENT_ID
./scripts/gerar-chaves-dev.sh   # par RSA local para assinar os tokens
docker compose up --build

# 2. Frontend, em outro terminal
cd frontend
npm install
npm start                   # http://localhost:4200
```

A API fica em `http://localhost:8080`; o front usa proxy para ela em
desenvolvimento.

## De onde veio

O sistema era um arquivo `Code.gs` de 1542 linhas, sem classes, com um `doPost`
que despachava 32 ações numa cadeia de `else if`, e uma planilha Google com 14
abas fazendo as vezes de banco. O front eram 21 arquivos JS compartilhando
variáveis globais e manipulando o DOM diretamente.

As regras de negócio foram preservadas, inclusive as menos óbvias — o saldo do
cofrinho que só desconta gastos posteriores ao primeiro depósito, a normalização
do nome de produto que junta "Leite Mimosa 1L" e "leite mimosa 1 l" no mesmo
histórico de preço, e a nota com valor que cria um gasto vinculado.

Os detalhes de cada parte estão nos READMEs de [`backend/`](backend/README.md)
e [`frontend/`](frontend/README.md).

## Testes

```bash
cd backend && mvn verify    # testes e o piso de cobertura (precisa de Docker)
cd frontend && npm test     # testes unitários, sem backend
cd frontend && npx ng test --watch=false --coverage   # com cobertura, em frontend/coverage/
cd frontend && npm run e2e    # ponta a ponta no navegador (Playwright), contra a demo
bash scripts/smoke.sh         # smoke e regressão com a stack inteira no Docker (precisa de Docker)
bash scripts/conferir-acentos.sh   # nenhum texto com acento quebrado (UTF-8 lido como Latin-1)
bash scripts/procurar-segredos.sh   # segredos no código e no histórico (gitleaks)
bash backend/scripts/teste-do-backup.sh   # backup e restauração num Postgres descartável
```

O `scripts/smoke.sh` sobe o backend como vai para o servidor (imagens do Dockerfile,
Postgres novo com as migrações) num projeto do Compose separado (`piggu-smoke`, sem mexer
no banco de desenvolvimento) e roda `frontend/stack/`:

- **Smoke** (`npm run smoke`): cada serviço no ar e pedindo login, cadastro, confirmação do
  e-mail pelo link, entrada, sessão renovada pelo cookie e encerrada ao sair, troca de senha,
  um gasto lançado e listado, e o mesmo caminho pela tela até o painel. O "esqueci minha
  senha" está marcado como pendente: o fluxo ainda não existe.
- **Regressão** (`npm run regressao`): um teste por problema que já aconteceu, com a data
  (o 401 no login de 14/10, o cumprimento pelo e-mail de 16/10, o client do Google, a
  versão dos Termos, enumeração de contas, cookie do refresh, CORS e rotas internas).
  Corrigiu um bug que a stack pega? Um teste lá, com a data e o que quebrou.

Sem e-mail configurado, o identity escreve no log o link que mandaria; os testes leem dali.
Com a stack já de pé (`PIGGU_MANTER_STACK=1 bash scripts/smoke.sh`), `npm run smoke` e
`npm run regressao` rodam de novo sem subir nada.

O CI roda tudo a cada pull request. A cobertura tem piso que só sobe: teste novo,
piso novo. No front fica no `angular.json` (`coverageThresholds`); no backend, nas
propriedades `cobertura.linhas` e `cobertura.ramos` do `pom.xml` de cada módulo
(JaCoCo, relatório em `backend/<módulo>/target/site/jacoco/`). O módulo `piggu-cobertura`
junta todos num relatório só (`target/site/jacoco-aggregate/`), que é o que vai para o Sonar.

## Branches

Todo trabalho sai da `develop` e volta para ela por PR; a `main` é produção e só recebe
PR da `develop` (ou de `hotfix/...`). O merge na `main` com CI verde publica no servidor.
O fluxo completo e as regras estão em [`docs/branches.md`](docs/branches.md).
