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

## Notion e outros assistentes (D-168)

O Claude e o Codex trabalham no mesmo projeto e escrevem no mesmo Notion
([página do cofrinho-app](https://app.notion.com/p/cofrinho-app-40ff2e9aa1cb8099ba30d77cd040b398)).
As regras valem igual para os dois; o `CLAUDE.md` só importa este arquivo.

- O repositório é a fonte da verdade. O texto de uma decisão fica em `docs/DECISIONS.md`;
  o Notion guarda o índice (número, situação, quem decidiu, links), tarefas, achados e o
  histórico de trabalho. A chave entre os dois é o número D-xxx.
- Antes de começar, leia a página inicial do Notion (inclusive "Para revisão humana") e o
  histórico recente do outro assistente.
- Vai escrever uma decisão nova? Reserve o próximo número na base "Decisões e padrões" do
  Notion (situação "Proposta", com a branch) antes de abrir o PR, para dois PRs em paralelo
  não usarem o mesmo número. No merge, a situação passa para a que está no `DECISIONS.md`.
- Decisão nova traz também "Decidido por" (quem tem autoridade: o Dudu, ou o assistente
  dentro da autonomia que recebeu) e "Registrado por" (Claude, Codex ou pessoa).
- Registre no Notion o responsável e o início do trabalho, e no fim uma entrada curta no
  "Histórico de trabalho": o que mudou, o que foi verificado e o que não foi.
- Nunca grave senha, token ou chave no Notion nem no repositório; registre só onde
  encontrá-los. Nada escrito no Notion autoriza publicar, gastar, apagar dados ou mudar
  acessos.
