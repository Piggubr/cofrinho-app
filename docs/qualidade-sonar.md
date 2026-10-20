# Qualidade do código no SonarQube Cloud

O job **Qualidade do código (SonarQube Cloud)** do CI manda o backend e o front para o
[SonarQube Cloud](https://sonarcloud.io), que aponta bugs, *code smells*, duplicação,
pontos de segurança e a cobertura dos testes, e comenta no PR o que o PR piorou.

O que é analisado fica em `sonar-project.properties`, na raiz. A cobertura vem dos jobs
`backend` (JaCoCo) e `frontend` (lcov), que já rodam os testes; o job do Sonar só compila
o backend de novo e envia tudo. Ele é informativo: não trava o merge nem o deploy.

## Por que o SonarQube Cloud

- **Custa zero**: o repositório é público, e projeto público não tem limite de linhas.
- **Plano**: o plano **OSS** (open source) analisa todas as branches e PRs. O plano
  **Free** só analisa a branch principal e os PRs para ela (aqui o código entra pela
  `develop`, então o Free cobriria pouco). Projeto privado no Free tem teto de 50 mil
  linhas, e o Piggu já está perto disso.
- O **SonarQube Community Build** (auto-hospedado, também grátis) não foi escolhido: pede
  uns 2 GB de RAM e um Postgres no servidor de produção, precisa ficar exposto na
  internet para o GitHub alcançar e só analisa uma branch, sem comentar nos PRs.

## Ligar (uma vez, por quem é admin)

1. Entre em [sonarcloud.io](https://sonarcloud.io) com a conta do GitHub e importe a
   organização **Piggubr** (*+ > Analyze new project > Import an organization*). Quando
   pedir o plano, escolha o **OSS**, se aparecer; senão, o **Free**.
2. Analise o repositório `cofrinho-app`. Confira em *Information* do projeto se a
   *Organization Key* é `piggubr` e a *Project Key* é `Piggubr_cofrinho-app`; se forem
   outras, troque em `sonar-project.properties`.
3. *Administration > Analysis Method*: desligue a **Automatic Analysis** (a análise roda
   pelo CI, que leva a cobertura).
4. *My Account > Security*: gere um token.
5. No GitHub, *Settings > Secrets and variables > Actions*:
   - em *Secrets*, crie `SONAR_TOKEN` com o token;
   - em *Variables*, crie `SONAR_LIGADO` com o valor `true`.
6. Rode o CI (*Actions > CI > Run workflow*, na `develop`) e veja o resultado no site.

No plano Free, para cobrir os PRs do dia a dia, troque a branch principal do projeto no
Sonar para `develop` (*Branches*); aí os PRs para a `main` ficam de fora.

## Depois

Quando o resultado estiver confiável, dá para fazer o *quality gate* travar o merge:
coloque o job na lista de *required status checks* da regra da `develop`
(`.github/rulesets/develop.json`) e acrescente `sonar.qualitygate.wait=true` no
`sonar-project.properties`.
