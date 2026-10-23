# Branches e o que cada uma pode fazer

| Branch | Para que serve | CI | Publica no servidor | Como recebe código |
|---|---|---|---|---|
| `main` | Produção (piggu.com.br) | Sim, no push e no PR | **Sim**, depois do CI verde | Só por PR vindo da `develop` ou de `hotfix/...` |
| `develop` | Onde as features se juntam antes de ir para produção | Sim, no push e no PR | Não | Só por PR |
| Qualquer outra (`feature/...`, `claude/...`, `demo/...`) | Trabalho em andamento | Pelo PR, ou na mão em *Actions > CI > Run workflow* | Não | Push direto |

As outras branches podem, no máximo, rodar os testes com cobertura e as verificações de
segurança (gitleaks, `npm audit`) e gerar o `.ipa` de teste: *Actions > iOS demo > Run
workflow*, escolhendo a branch (ou um push numa `demo/...`).

## Caminho de uma mudança

1. Branch a partir da `develop`, PR para a `develop`. O CI tem que passar.
2. Quando a `develop` estiver pronta para sair, PR da `develop` para a `main`. O CI roda de
   novo e o job "Origem do PR para a main" confere que veio da `develop`.
3. Merge na `main`: o CI roda no push e, verde, o job "Publicar no servidor" atualiza
   produção (docs/implantacao-producao.md, seção 8).

Correção urgente: branch `hotfix/<nome>` a partir da `main`, PR direto para a `main`, e
depois um PR da `main` para a `develop` para as duas não divergirem.

## Por que assim

- Só a `main` alcança o servidor: o ambiente `producao` do GitHub (onde ficam a chave SSH
  e o IP) aceita apenas a `main`, então um workflow alterado em outra branch não consegue
  ler esses segredos.
- Na `main` e na `develop` ninguém dá push direto, força push ou apaga a branch.
- Nada entra nelas com o CI vermelho, e a branch do PR tem que estar atualizada.

## Ligar as regras no GitHub (uma vez, por quem é admin)

1. *Settings > Rules > Rulesets > New ruleset > Import a ruleset* e escolha
   `.github/rulesets/main.json`. Confira e clique em *Create*.
2. Repita com `.github/rulesets/develop.json`.
3. *Settings > Environments > producao > Deployment branches and tags*: *Selected
   branches*, só `main`.

As regras pedem 0 aprovações para não travar quem trabalha sozinho. Quando outra pessoa
for revisar, suba *Required approvals* para 1 na regra da `main`.

Os nomes dos checks obrigatórios são os `name:` dos jobs do `.github/workflows/ci.yml`.
Renomeou um job? Atualize a regra, senão o PR fica esperando um check que nunca chega.

Mudou a lista de checks no JSON (por exemplo, quando entraram o Lint, o Monitoramento, o
OSV e as Imagens)? A regra no GitHub não lê o arquivo sozinha: em *Settings > Rules >
Rulesets*, abra a regra e acrescente os checks novos em *Require status checks to pass*
(ou apague a regra e importe o JSON de novo).

Checks que só informam, sem travar o merge: **CodeQL** (análise de segurança do código;
com o repositório privado e sem o GitHub Code Security, os achados ficam como aviso no
próprio job e o SARIF como artefato, veja `.github/workflows/codeql.yml`) e **Qualidade do código (SonarQube Cloud)**
(`docs/qualidade-sonar.md`). Quando os resultados estiverem limpos e confiáveis, dá para
torná-los obrigatórios do mesmo jeito.
