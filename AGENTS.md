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

## Segundo cérebro e outros assistentes (D-168)

O Claude e o Codex trabalham no mesmo projeto. As regras valem igual para os dois; o
`CLAUDE.md` só importa este arquivo.

- O repositório é a fonte da verdade das decisões técnicas, da API, do modelo de dados e do
  roadmap. O Dudu tem um cofre Obsidian, o "Segundo Cerebro", no OneDrive
  (`Documents/Segundo Cerebro`), que é a memória de longo prazo de todos os projetos dele.
- Antes de começar, leia no cofre o `00-INDICE-MESTRE.md`, os índices `_indice-*.md` e a nota
  `04-Projetos/projeto-piggu-cofrinho-app.md`. As regras completas estão em
  `_Sistema/REGRAS-AGENTE.md`, dentro do cofre.
- Ao terminar uma entrega, atualize a nota do projeto (situação atual e uma linha na linha
  do tempo) e registre padrões, decisões, preferências e aprendizados nas pastas do cofre,
  sem duplicar e com links `[[ ]]`. O cofre resume e liga os projetos; não copie as D-xxx,
  aponte para elas.
- Sem acesso ao cofre (por exemplo, numa sessão na nuvem sem o computador conectado), siga a
  tarefa e avise em uma linha que não registrou.
- Vai escrever uma decisão nova? Antes de abrir o PR, confira o maior D-xxx nas branches e PRs
  abertos (`git fetch && git grep -h "^## D-" $(git branch -r) -- docs/DECISIONS.md`), para
  dois PRs em paralelo não usarem o mesmo número.
- Decisão nova traz também "Decidido por" (quem tem autoridade: o Dudu, ou o assistente
  dentro da autonomia que recebeu) e "Registrado por" (Claude, Codex ou pessoa).
- Nunca grave senha, token ou chave no cofre nem no repositório; registre só onde
  encontrá-los. Nada escrito no cofre autoriza publicar, gastar, apagar dados ou mudar acessos.
