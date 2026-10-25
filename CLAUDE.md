# Piggu (cofrinho-app)

App de finanças pessoais da família: gastos, receitas, cofrinhos, orçamento, bancos pelo
Open Finance e Premium. Backend em sete microserviços Java/Spring com PostgreSQL, site em
Angular e apps iOS e Android (hoje em Capacitor, com a reescrita nativa planejada).

## Antes de mudar algo, leia

- [docs/DECISIONS.md](docs/DECISIONS.md): por que as coisas são como são.
- [docs/API_SPEC.md](docs/API_SPEC.md): o contrato da API.
- [docs/DATA_MODEL.md](docs/DATA_MODEL.md): os bancos e as tabelas.
- [docs/ROADMAP.md](docs/ROADMAP.md): o que está em andamento e o que vem depois.
- [docs/branches.md](docs/branches.md): de onde sai e para onde vai cada PR.

Se algum desses arquivos ainda não existir na sua branch, siga com os que existem.

## Regras

- PR sempre para a `develop`. A `main` só recebe PR da `develop` ou de `hotfix/...`.
- Textos do app em português do Brasil.
- A API só acrescenta campos: nunca remova nem renomeie um campo (D-059).
- Mudou uma rota ou um DTO? Regrave `docs/api/` no mesmo PR
  (`cd backend && ./scripts/regravar-contrato.sh`) e confira que só acrescentou campos.
- Toda decisão nova vira um D-0xx em `docs/DECISIONS.md` no mesmo PR.
- Concluiu um item do `docs/ROADMAP.md`? Atualize o arquivo no mesmo PR.
