# Piggu — O cofrinho dos fofos

Aplicação de controle financeiro doméstico, migrada de um Google Apps Script com
planilha como banco de dados para uma plataforma em Java e Angular.

## O que tem aqui

| Pasta | O que é |
|---|---|
| [`backend/`](backend/) | Sete microserviços em Java 21 com Spring Boot 3.5 e PostgreSQL |
| [`frontend/`](frontend/) | Aplicação Angular 22 com componentes standalone e signals |
| `Code.gs` | Backend original em Apps Script, mantido como referência das regras de negócio |
| `assets/` | Material de marca (logos, ícones, ilustrações) do front original |

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
cd backend && mvn verify    # 169 testes (precisa de Docker)
cd frontend && npm test     # 30 testes
```
