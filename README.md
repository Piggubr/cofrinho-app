# Piggu — O cofrinho dos fofos

Aplicação de controle financeiro doméstico, migrada de um Google Apps Script com
planilha como banco de dados para uma plataforma em Java e Angular.

## O que tem aqui

| Pasta | O que é |
|---|---|
| [`backend/`](backend/) | Sete microserviços em Java 21 com Spring Boot 4.0 e PostgreSQL |
| [`frontend/`](frontend/) | Aplicação Angular 22 com componentes standalone e signals |
| [`frontend/ios/`](frontend/ios/) | App iOS (Capacitor), com a barra de abas nativa no estilo Liquid Glass ([detalhes](frontend/ios/LIQUID-GLASS-NATIVO.md)) |
| [`deploy/`](deploy/) e [`docs/`](docs/) | Caddy com HTTPS, guia de produção, plano de incidente, LGPD e planos Premium |
| `Code.gs` | Backend original em Apps Script, mantido como referência das regras de negócio |
| `assets/` | Material de marca (logos, ícones, ilustrações) do front original |

## O que o app faz

- **Família:** cadastro aberto, convite por e-mail e papéis ADMIN, TITULAR e MEMBRO,
  com os dados de cada família isolados no banco.
- **Dinheiro:** gastos (inclusive em outra moeda e divididos na família), receitas,
  contas fixas com lançamento automático, contas e cartões com fatura e parcelamento,
  metas, orçamento por categoria com alerta em 80% e 100%, regras de categoria,
  relatório do mês e do ano, importação de OFX/CSV sem duplicar e exportação em CSV.
- **Bancos:** saldo pelo Open Finance (Pluggy), sempre com consentimento e com
  desconexão grátis.
- **Notas fiscais:** leitura por OCR próprio e, no Premium, pelo Gemini.
- **Estilo de vida:** compras com histórico de preço, filmes, lugares, fotos e prêmios.
- **Premium:** Stripe no site e RevenueCat nas lojas, com 7 dias de teste
  ([planos](docs/planos-premium.md)).
- **Privacidade:** exportar e excluir a conta, aceite dos termos gravado com data e
  versão, retenção automática e sessão com refresh token em cookie HttpOnly.
- **Visual:** paleta Rosé suave com modo escuro, vidro no celular e busca global
  (Ctrl+K). Há um modo demo, sem backend, para mostrar o app
  ([app iOS de demonstração](docs/app-ios-demo.md)).

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
cd backend && mvn verify    # mais de 340 testes (precisa de Docker)
cd frontend && npm test     # 262 testes
cd frontend && npx ng test --watch=false --coverage   # com cobertura, em frontend/coverage/
cd frontend && npm run e2e    # ponta a ponta no navegador (Playwright), contra a demo
bash scripts/procurar-segredos.sh   # segredos no código e no histórico (gitleaks)
bash backend/scripts/teste-do-backup.sh   # backup e restauração num Postgres descartável
```

O CI roda tudo a cada pull request. No front, a cobertura tem um piso no
`angular.json` (`coverageThresholds`) que só sobe: teste novo, piso novo.
