# Qualidade do código no SonarQube Cloud

O job **Qualidade do código (SonarQube Cloud)** do CI manda o backend e o front para o
[SonarQube Cloud](https://sonarcloud.io), que aponta bugs, *code smells*, duplicação,
pontos de segurança e a cobertura dos testes, e comenta no PR o que o PR piorou.

O que é analisado fica em `sonar-project.properties`, na raiz. A cobertura vem dos jobs
`backend` (JaCoCo) e `frontend` (lcov), que já rodam os testes; o job do Sonar só compila
o backend de novo e envia tudo. Ele é informativo: não trava o merge nem o deploy.

No backend o Sonar recebe um relatório só, montado pelo módulo `piggu-cobertura` (sem
código, só o `report-aggregate` do JaCoCo). O relatório de cada módulo conta apenas os
testes dele, e o `piggu-common` é exercitado sobretudo pelos testes dos serviços: sem
juntar, o código comum aparecia como não testado. Os pisos continuam por módulo.

## Plano e limites

- O repositório é **privado**, então o projeto está no plano **Free** do SonarQube Cloud:
  até 50 mil linhas (em 23/10/2026 eram 42,6 mil, contando só o código, sem os testes).
  Se passar disso, a análise para e o Sonar pede o plano pago (Team).
- O Free só analisa a branch principal do projeto, que no Sonar é a `develop`, e os PRs
  para ela. Por isso o job roda só no push da `develop` e nos PRs para a `develop`; a
  `main` recebe o mesmo código depois, pelo PR da `develop`.
- O **SonarQube Community Build** (auto-hospedado, também grátis) não foi escolhido: pede
  uns 2 GB de RAM e um Postgres no servidor de produção, precisa ficar exposto na
  internet para o GitHub alcançar e só analisa uma branch, sem comentar nos PRs.

## Regras desligadas

Ficam em `sonar.issue.ignore.multicriteria`, no `sonar-project.properties`:

- `shelldre:S7688` (usar `[[` em vez de `[` nos scripts): estilo; os scripts já passam
  no shellcheck do job "Backup e restauracao".
- `plsql:DeleteOrUpdateWithoutWhereCheck` nas migrations do Flyway: o UPDATE sem WHERE
  corrige todas as linhas de propósito, e migration aplicada não pode mudar (o Flyway
  confere o checksum e para a subida em produção).

## Ligar (uma vez, por quem é admin)

1. Entre em [sonarcloud.io](https://sonarcloud.io) com a conta do GitHub e importe a
   organização **Piggubr** (*+ > Analyze new project > Import an organization*). Quando
   pedir o plano, escolha o **Free** (repositório privado).
2. Analise o repositório `cofrinho-app`. Confira em *Information* do projeto se a
   *Organization Key* é `piggubr` e a *Project Key* é `Piggubr_cofrinho-app`; se forem
   outras, troque em `sonar-project.properties`.
3. *Administration > Analysis Method*: desligue a **Automatic Analysis** (a análise roda
   pelo CI, que leva a cobertura).
4. *My Account > Security*: gere um token.
5. No GitHub, *Settings > Secrets and variables > Actions*:
   - em *Secrets*, crie `SONAR_TOKEN` com o token;
   - em *Variables*, crie `SONAR_LIGADO` com o valor `true`.
6. Em *Branches*, deixe a `develop` como branch principal do projeto.
7. Rode o CI (*Actions > CI > Run workflow*, na `develop`) e veja o resultado no site.

## Depois

Quando o resultado estiver confiável, dá para fazer o *quality gate* travar o merge:
coloque o job na lista de *required status checks* da regra da `develop`
(`.github/rulesets/develop.json`) e acrescente `sonar.qualitygate.wait=true` no
`sonar-project.properties`.
