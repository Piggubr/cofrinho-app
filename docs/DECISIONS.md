# Decisões do Piggu

Registro das decisões de arquitetura e de produto: o que foi escolhido, por quê e o que
isso obriga. Leia antes de mudar algo que pareça estranho, porque muitas vezes há um motivo.

**Como acrescentar uma decisão:** use o próximo número livre (D-083, D-084...) e nunca
apague uma decisão. Se uma decisão mudar, escreva a nova e troque a situação da antiga para
"Substituída por D-0xx". A decisão entra no mesmo PR da mudança que ela explica.

Situações possíveis: **Valendo**, **Substituída por D-0xx** ou **Em aberto**. Quando a fonte
não registra o porquê, a decisão fica nas [Perguntas para o Dudu](#perguntas-para-o-dudu),
no fim, em vez de ganhar um motivo inventado.

## Índice

| Nº | Decisão | Situação |
|---|---|---|
| **Backend** | | |
| [D-001](#d-001-sair-do-apps-script-para-java-spring-boot-e-postgresql) | Sair do Apps Script para Java, Spring Boot e PostgreSQL | Valendo |
| [D-002](#d-002-um-banco-por-serviço) | Um banco por serviço | Valendo |
| [D-003](#d-003-fronteiras-pela-transação-quase-sem-chamadas-entre-serviços) | Fronteiras pela transação, quase sem chamadas entre serviços | Valendo |
| [D-004](#d-004-erros-num-formato-só) | Erros num formato só | Valendo |
| [D-005](#d-005-concorrência-tratada-caso-a-caso) | Concorrência tratada caso a caso | Valendo |
| [D-006](#d-006-imagens-com-registro-próprio-entregues-por-url) | Imagens com registro próprio, entregues por URL | Valendo |
| [D-007](#d-007-integração-fora-do-ar-avisa-em-vez-de-quebrar) | Integração fora do ar avisa em vez de quebrar | Valendo |
| [D-008](#d-008-open-finance-pela-pluggy-sem-a-senha-passar-pelo-piggu) | Open Finance pela Pluggy, sem a senha passar pelo Piggu | Valendo |
| [D-009](#d-009-recibo-lido-pela-ia-só-vira-gasto-depois-de-conferido) | Recibo lido pela IA só vira gasto depois de conferido | Valendo |
| [D-010](#d-010-datas-como-dia-sem-fuso) | Datas como dia, sem fuso | Valendo |
| [D-011](#d-011-nomes-em-português-do-backend-ao-front) | Nomes em português do backend ao front | Valendo |
| [D-073](#d-073-gasto-lançado-leva-um-id-do-envio) | Gasto lançado leva um id do envio | Valendo |
| [D-074](#d-074-nenhuma-transação-do-banco-em-volta-de-chamada-a-serviço-de-fora) | Nenhuma transação do banco em volta de chamada a serviço de fora | Valendo |
| [D-082](#d-082-corrida-perdida-no-banco-responde-409) | Corrida perdida no banco responde 409 | Valendo |
| [D-085](#d-085-fatura-paga-só-sai-do-a-pagar) | Fatura paga só sai do "a pagar" | Valendo |
| [D-087](#d-087-salário-em-duas-parcelas-numa-receita-só) | Salário em duas parcelas numa receita só | Valendo |
| **Segurança e privacidade** | | |
| [D-012](#d-012-dois-tokens-access-curto-e-refresh-longo) | Dois tokens: access curto e refresh longo | Valendo |
| [D-013](#d-013-serviço-recusa-subir-com-segredo-fraco) | Serviço recusa subir com segredo fraco | Valendo |
| [D-014](#d-014-refresh-em-cookie-httponly-no-site) | Refresh em cookie HttpOnly no site | Valendo |
| [D-015](#d-015-famílias-papéis-e-isolamento-no-banco) | Famílias, papéis e isolamento no banco | Valendo |
| [D-016](#d-016-cada-um-vê-o-seu-ver-tudo-do-parceiro-só-com-consentimento) | Cada um vê o seu; ver tudo do parceiro só com consentimento | Valendo |
| [D-017](#d-017-entrar-com-e-mail-e-senha-google-como-opção) | Entrar com e-mail e senha, Google como opção | Valendo |
| [D-018](#d-018-cpf-obrigatório-único-e-cifrado) | CPF obrigatório, único e cifrado | Valendo |
| [D-019](#d-019-documento-de-identidade-de-cada-país) | Documento de identidade de cada país | Substituída por D-020 |
| [D-020](#d-020-só-brasil-cpf-para-todos) | Só Brasil: CPF para todos | Valendo |
| [D-021](#d-021-logs-sem-dado-pessoal) | Logs sem dado pessoal | Valendo |
| [D-022](#d-022-exportar-e-apagar-a-conta-em-todos-os-serviços) | Exportar e apagar a conta em todos os serviços | Valendo |
| [D-023](#d-023-consentimento-na-hora-de-usar-ia-e-bancos) | Consentimento na hora de usar IA e bancos | Valendo |
| [D-024](#d-024-dados-apagados-sozinhos-depois-de-um-prazo) | Dados apagados sozinhos depois de um prazo | Valendo |
| [D-025](#d-025-limite-de-chamadas-em-três-camadas) | Limite de chamadas em três camadas | Valendo |
| [D-026](#d-026-caddy-como-única-porta-pública) | Caddy como única porta pública | Valendo |
| [D-027](#d-027-login-do-google-no-app-pelo-navegador-do-celular) | Login do Google no app pelo navegador do celular | Valendo |
| [D-080](#d-080-e-mail-da-conta-sai-depois-do-commit-fora-da-requisição) | E-mail da conta sai depois do commit, fora da requisição | Valendo |
| [D-086](#d-086-no-plano-casal-o-convidado-entra-como-parceiro) | No plano casal, o convidado entra como parceiro | Valendo |
| [D-088](#d-088-entrar-pelo-google-pede-o-mesmo-cadastro-do-e-mail) | Entrar pelo Google pede o mesmo cadastro do e-mail | Valendo |
| **Dados** | | |
| [D-028](#d-028-fora-do-identity-só-o-id-da-conta) | Fora do identity, só o id da conta | Valendo |
| [D-029](#d-029-migration-aplicada-nunca-muda) | Migration aplicada nunca muda | Valendo |
| [D-030](#d-030-apagar-ou-renomear-coluna-em-dois-deploys) | Apagar ou renomear coluna em dois deploys | Valendo |
| [D-031](#d-031-painel-analítico-só-lê-contagens) | Painel analítico só lê contagens | Valendo |
| [D-032](#d-032-backup-diário-cifrado-fora-do-servidor-e-testado) | Backup diário cifrado, fora do servidor e testado | Valendo |
| [D-077](#d-077-a-família-nova-de-quem-sai-tem-id-previsível) | A família nova de quem sai tem id previsível | Valendo |
| **Infraestrutura e deploy** | | |
| [D-033](#d-033-um-servidor-oracle-cloud-always-free-com-docker-compose) | Um servidor Oracle Cloud Always Free, com Docker Compose | Valendo |
| [D-034](#d-034-só-a-main-publica-com-chave-ssh-presa-ao-script) | Só a `main` publica, com chave SSH presa ao script | Valendo |
| [D-035](#d-035-imagens-montadas-no-ci-para-arm-com-rollback-automático) | Imagens montadas no CI, para ARM, com rollback automático | Valendo |
| [D-036](#d-036-monitoramento-no-próprio-servidor) | Monitoramento no próprio servidor | Valendo |
| [D-037](#d-037-versão-de-produção-pela-data) | Versão de produção pela data | Valendo |
| [D-069](#d-069-repositório-privado) | Repositório privado | Valendo |
| [D-070](#d-070-e-mails-pela-resend) | E-mails pela Resend | Valendo |
| **Pagamentos e Premium** | | |
| [D-038](#d-038-o-backend-aplica-os-limites-do-premium) | O backend aplica os limites do Premium | Valendo |
| [D-039](#d-039-premium-da-família-inteira) | Premium da família inteira | Substituída por D-040 |
| [D-040](#d-040-três-planos-individual-casal-e-família) | Três planos: individual, casal e família | Valendo |
| [D-041](#d-041-stripe-no-site-revenuecat-nas-lojas) | Stripe no site, RevenueCat nas lojas | Valendo |
| [D-042](#d-042-premium-só-muda-pelo-webhook-assinado) | Premium só muda pelo webhook assinado | Valendo |
| [D-043](#d-043-cobrar-pelo-open-finance-sim-pela-desconexão-nunca) | Cobrar pelo Open Finance sim, pela desconexão nunca | Valendo |
| [D-044](#d-044-sete-dias-de-teste-e-sete-de-arrependimento) | Sete dias de teste e sete de arrependimento | Valendo |
| [D-045](#d-045-open-finance-desligado-por-enquanto) | Open Finance desligado por enquanto | Valendo |
| [D-046](#d-046-leitor-próprio-de-nota-primeiro-gemini-de-reserva) | Leitor próprio de nota primeiro, Gemini de reserva | Substituída por D-047 |
| [D-047](#d-047-nota-fiscal-direto-no-gemini) | Nota fiscal direto no Gemini | Valendo |
| [D-048](#d-048-preço-do-premium-no-iphone) | Preço do Premium no iPhone | Em aberto |
| [D-078](#d-078-quem-paga-o-premium-não-muda-de-família-com-ele-valendo) | Quem paga o Premium não muda de família com ele valendo | Valendo |
| [D-079](#d-079-cartão-recusado-dá-7-dias-de-premium) | Cartão recusado dá 7 dias de Premium | Valendo |
| [D-081](#d-081-stripe-e-revenuecat-sem-interface) | Stripe e RevenueCat sem interface | Valendo |
| **Apps (site, iOS e Android)** | | |
| [D-049](#d-049-site-em-angular-com-telas-sob-demanda) | Site em Angular, com telas sob demanda | Valendo |
| [D-050](#d-050-só-o-login-conhece-os-tokens-uma-renovação-por-vez) | Só o login conhece os tokens; uma renovação por vez | Valendo |
| [D-051](#d-051-textos-em-português-do-brasil-prontos-para-tradução) | Textos em português do Brasil, prontos para tradução | Valendo |
| [D-052](#d-052-lista-de-compras-risca-antes-da-resposta) | Lista de compras risca antes da resposta | Valendo |
| [D-053](#d-053-apps-em-capacitor-abrindo-o-site-no-ar) | Apps em Capacitor abrindo o site no ar | Valendo |
| [D-054](#d-054-barra-de-abas-nativa-com-liquid-glass-no-capacitor) | Barra de abas nativa com Liquid Glass no Capacitor | Valendo |
| [D-055](#d-055-ios-reescrito-em-swiftui) | iOS reescrito em SwiftUI | Valendo |
| [D-056](#d-056-android-reescrito-em-kotlin-com-material-3) | Android reescrito em Kotlin, com Material 3 | Valendo |
| [D-057](#d-057-nativo-entra-como-versão-200-no-mesmo-app-das-lojas) | Nativo entra como versão 2.0.0 no mesmo app das lojas | Valendo |
| [D-058](#d-058-tokens-no-app-nativo) | Tokens no app nativo | Valendo |
| [D-059](#d-059-a-api-só-acrescenta-campos) | A API só acrescenta campos | Valendo |
| [D-060](#d-060-features-novas-no-site-durante-a-migração) | Features novas no site durante a migração | Em aberto |
| [D-075](#d-075-só-sessão-recusada-encerra-a-sessão-só-dado-inválido-tira-gasto-da-fila) | Só sessão recusada encerra a sessão; só dado inválido tira gasto da fila | Valendo |
| [D-076](#d-076-o-campo-de-valor-do-site-lê-igual-ao-app-nativo) | O campo de valor do site lê igual ao app nativo | Valendo |
| [D-071](#d-071-chiclete-como-cor-padrão-e-porquinho-neon-como-ícone) | Chiclete como cor padrão e Porquinho Neon como ícone | Valendo |
| [D-072](#d-072-refresh-no-corpo-só-para-quem-pede-e-apple-pelo-e-mail) | Refresh no corpo só para quem pede, e Apple pelo e-mail | Valendo |
| **Processo** | | |
| [D-061](#d-061-develop-junta-main-é-produção) | `develop` junta, `main` é produção | Valendo |
| [D-062](#d-062-dependabot-abre-pr-na-develop) | Dependabot abre PR na `develop` | Valendo |
| [D-063](#d-063-ci-completo-em-todo-pr) | CI completo em todo PR | Valendo |
| [D-064](#d-064-sonarqube-cloud-grátis-analisando-a-develop) | SonarQube Cloud grátis, analisando a `develop` | Valendo |
| [D-065](#d-065-cobertura-com-piso-que-só-sobe) | Cobertura com piso que só sobe | Valendo |
| [D-066](#d-066-testes-com-postgres-de-verdade) | Testes com Postgres de verdade | Valendo |
| [D-067](#d-067-bug-corrigido-vira-teste-de-regressão-com-data) | Bug corrigido vira teste de regressão com data | Valendo |
| [D-068](#d-068-toda-decisão-nova-vira-um-d-0xx-no-mesmo-pr) | Toda decisão nova vira um D-0xx no mesmo PR | Valendo |

---

# Backend

## D-001: Sair do Apps Script para Java, Spring Boot e PostgreSQL
- Data: 14/09/2026 (primeiro commit do `backend/`)
- Situação: Valendo
- Contexto: o sistema era um `Code.gs` de 1542 linhas com 32 ações num `else if` e uma planilha Google de 14 abas como banco. Sem transação, com uma trava (`LockService`) que parava o script inteiro e um front de 21 arquivos JS com variáveis globais.
- Decisão: sete microserviços em Java 21 com Spring Boot (hoje 4.0) e PostgreSQL, mais a biblioteca `piggu-common`. As regras de negócio do Apps Script foram mantidas, inclusive as menos óbvias.
- Alternativas: um monolito ou outras linguagens. Segundo o Dudu (26/10/2026), os microserviços facilitam a manutenção do código, e Java e Spring são as tecnologias que ele conhece.
- Consequências: cada ação antiga tem um endereço novo no mapa de migração. O histórico da planilha ainda precisa de um importador.
- Onde ver: [backend/README.md](../backend/README.md), [Mapa de migração](../backend/README.md#mapa-de-migração), [README.md, "De onde veio"](../README.md#de-onde-veio)

## D-002: Um banco por serviço
- Data: 14/09/2026
- Situação: Valendo
- Contexto: um serviço que lê a tabela de outro por atalho transforma os microserviços num monolito distribuído sem ninguém perceber.
- Decisão: cada serviço tem o próprio banco (não só o próprio schema) e, desde 11/10/2026, o próprio usuário no Postgres, que só alcança aquele banco.
- Alternativas: um banco com schemas separados ou um usuário só. Ficaram de fora porque não impedem o atalho, e uma injeção de SQL num serviço chegaria às tabelas dos outros.
- Consequências: nada de JOIN entre serviços. Banco novo exige rodar de novo o `db/init/01-criar-bancos.sh`.
- Onde ver: [Por que um banco por serviço](../backend/README.md#por-que-um-banco-por-serviço), [implantação, seção 3](implantacao-producao.md#3-senha-do-postgres)

## D-003: Fronteiras pela transação, quase sem chamadas entre serviços
- Data: 14/09/2026
- Situação: Valendo
- Contexto: no Apps Script, a nota com valor gravava a nota e o gasto em abas diferentes, sem transação, e uma falha no meio deixava um gasto órfão.
- Decisão: o que precisa de transação fica no mesmo serviço (notas e gastos no `finance`). Requisição comum não passa de um serviço para outro, com uma exceção: o `lifestyle` chama o `media` para guardar fotos. As outras chamadas saem do `identity` (exportar e apagar a conta, avisos do dia).
- Alternativas: separar notas e gastos, o que exigiria uma saga.
- Consequências: antes de criar uma chamada entre serviços, reveja as fronteiras. Os endereços ficam em `piggu.dados.servicos`.
- Onde ver: [Por que quase não há chamadas entre serviços](../backend/README.md#por-que-quase-não-há-chamadas-entre-serviços)

## D-004: Erros num formato só
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o front precisa mostrar uma mensagem útil sem nunca exibir detalhe interno do servidor.
- Decisão: `BusinessException` leva um texto escrito para o usuário, que vai inteiro na resposta. Qualquer outra exceção vira 500 com mensagem genérica, e o detalhe fica só no log. O corpo é sempre `{ erro, codigo, campos, momento }`.
- Alternativas: não registradas.
- Consequências: erro de protocolo do Spring (método errado e afins) não pode virar 500, e o `ApiExceptionHandlerTest` cobre isso.
- Onde ver: [Erros](../backend/README.md#erros), [Três testes que merecem atenção](../backend/README.md#três-testes-que-merecem-atenção)

## D-005: Concorrência tratada caso a caso
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o `LockService` do Apps Script serializava o script inteiro.
- Decisão: só Fofocoins e o resgate de prêmios rodam em `SERIALIZABLE`. O saldo de Fofocoins nunca é guardado: é sempre a soma do livro-razão, e um erro se corrige lançando o oposto. A memória de preços virou uma linha por produto.
- Alternativas: travar tudo, como antes.
- Consequências: não baixe o isolamento do `RewardsService`. O `ResgateConcorrenteTest` falha com "expected: 1 but was: 2" se alguém baixar.
- Onde ver: [Concorrência](../backend/README.md#concorrência)

## D-006: Imagens com registro próprio, entregues por URL
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o id do Drive ficava solto numa célula, e a foto ia em base64 dentro do JSON, um terço maior e sem cache no navegador.
- Decisão: cada arquivo tem um registro em `assets` (tipo, tamanho e dono). O acesso ao armazenamento fica atrás da interface `StoragePort`, e a imagem sai de `/api/assets/{id}/content` com `Content-Type` e 30 dias de cache.
- Alternativas: S3 ou MinIO cabem num adaptador novo, sem mudar regra de negócio.
- Consequências: o `src` da imagem aponta direto para a API. Sem credenciais do Google, o adaptador local é usado. Em produção as fotos ficam nesse adaptador, no volume `piggu-fotos` do servidor, que entra no backup (D-032); o Google Drive não é usado lá (Dudu, 26/10/2026).
- Onde ver: [Imagens (backend)](../backend/README.md#imagens), [Imagens por URL (frontend)](../frontend/README.md#imagens-por-url-não-em-base64)

## D-007: Integração fora do ar avisa em vez de quebrar
- Data: 14/09/2026
- Situação: Valendo
- Contexto: Gemini, TMDB, OpenFoodFacts, Frankfurter e Pluggy podem estar sem chave ou fora do ar.
- Decisão: cada integração degrada com um aviso claro. O câmbio tem uma escada de três degraus (cache de uma hora, último valor marcado como desatualizado, valor fixo de emergência), para a tela nunca ficar sem número.
- Alternativas: não registradas.
- Consequências: integração nova precisa dizer o que acontece sem a chave dela.
- Onde ver: [Integrações externas](../backend/README.md#integrações-externas)

## D-008: Open Finance pela Pluggy, sem a senha passar pelo Piggu
- Data: 01/10/2026
- Situação: Valendo (desligado por padrão, veja D-045)
- Contexto: mostrar saldo e trazer débitos dos bancos sem guardar a senha de ninguém.
- Decisão: a pessoa conecta o banco no widget Pluggy Connect, e a senha vai direto para a Pluggy. O backend confere que o `clientUserId` do item é de quem está logado antes de gravar. O script do widget tem versão fixa e hash de integridade.
- Alternativas: forçar a ida ao banco na hora (webhook) ficou de fora. A Pluggy foi sugestão do Claude, sem comparação registrada com outros agregadores; o Dudu pediu (26/10/2026) para avaliar opções mais baratas.
- Consequências: o serviço `piggu-banking` só sobe no perfil `open-finance`. Cada pessoa vê os bancos que ela mesma conectou.
- Onde ver: [Open Finance (Pluggy)](../backend/README.md#open-finance-pluggy), [Bancos conectados (frontend)](../frontend/README.md#bancos-conectados-open-finance)

## D-009: Recibo lido pela IA só vira gasto depois de conferido
- Data: 14/09/2026
- Situação: Valendo
- Contexto: a IA erra, e um gasto errado contamina a média de preços da memória de produtos.
- Decisão: `POST /api/receipts/parse` não grava nada. Os itens encontrados ficam editáveis na tela e só viram gasto quando a pessoa confirma.
- Alternativas: gravar direto o que a IA leu.
- Consequências: qualquer leitor novo de nota (OCR próprio ou outra IA) segue o mesmo caminho.
- Onde ver: [Recibo passa por conferência](../frontend/README.md#recibo-passa-por-conferência)

## D-010: Datas como dia, sem fuso
- Data: 14/09/2026
- Situação: Valendo
- Contexto: converter por `Date` com o fuso do navegador movia um gasto do dia 1 para o dia 31 do mês anterior.
- Decisão: a API trabalha com o dia (`2026-09-10`), não com um instante. O front monta e lê esse texto direto, nas funções de `datas.ts`.
- Alternativas: instante com fuso.
- Consequências: não use `new Date('2026-09-10')` para data de lançamento. O `datas.spec.ts` protege isso.
- Onde ver: [Datas sem fuso](../frontend/README.md#datas-sem-fuso)

## D-011: Nomes em português do backend ao front
- Data: 14/09/2026
- Situação: Valendo
- Contexto: os DTOs do backend têm nomes em português.
- Decisão: os tipos em `core/api/models.ts` espelham os DTOs com os mesmos nomes em português.
- Alternativas: traduzir no front, o que criaria um dicionário a mais para manter a cada campo novo.
- Consequências: campo novo na API entra com o mesmo nome no front.
- Onde ver: [Estrutura (frontend)](../frontend/README.md#estrutura)

## D-073: Gasto lançado leva um id do envio
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: com sinal fraco, o servidor gravava o gasto, a resposta se perdia (status 0) e a fila sem sinal mandava de novo: gasto em dobro.
- Decisão: o app gera um `idDoEnvio` (UUID) por toque em "Lançar" e a fila reenvia o mesmo. O finance grava o id em `expense_submissions` na mesma transação do gasto; o mesmo id na mesma família não lança de novo e devolve os gastos da primeira vez, com o mesmo 201. Sem o campo, nada muda.
- Alternativas: reaproveitar o `reciboId`, que já agrupa os itens de uma nota e mudaria de sentido.
- Consequências: a tabela cresce uma linha por envio e ainda não tem retenção; limpar depois de 30 dias quando pesar.
- Onde ver: [API_SPEC.md, "Reenvio sem duplicar"](API_SPEC.md)

## D-074: Nenhuma transação do banco em volta de chamada a serviço de fora
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: o assistente, a leitura de nota e o câmbio esperavam o Gemini ou a cotação com uma das 10 conexões do banco presa; dez pessoas ao mesmo tempo travavam o finance inteiro.
- Decisão: essas chamadas rodam fora de transação; consentimento, cota e uso são instruções únicas que confirmam sozinhas. A pergunta ao assistente é reservada na cota antes de chamar a IA, numa instrução só, e conta mesmo se a resposta falhar (as rodadas já custaram). A leitura de nota, ao contrário, devolve a reserva se falhar (como já era).
- Alternativas: aumentar o pool de conexões, que só adia o mesmo travamento.
- Consequências: as ferramentas do assistente leem cada uma na própria transação, sem foto única do banco; para quem só lê, sem efeito.
- Onde ver: `AssistenteService`, `CotaDeLeituras`

## D-082: Corrida perdida no banco responde 409
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: no rewards (SERIALIZABLE), quem perdia a corrida recebia 500 e contava como falha do servidor; pagar a conta fixa ou receber o "a receber" com dois toques lançava duas vezes.
- Decisão: `ConcurrencyFailureException` responde como restrição violada: 409 `CONFLITO`, "Outra alteração chegou antes. Tente de novo." Pagar a conta fixa e receber o "a receber" passam por um UPDATE condicional, e o segundo toque recebe o 409 que já existia.
- Alternativas: não registradas.
- Consequências: o aviso "Restrição do banco violada" no log passa a contar também essas corridas.
- Onde ver: `ApiExceptionHandler`, `ContasFixasService.pagar`

## D-085: Fatura paga só sai do "a pagar"
- Data: 27/10/2026 ([PR #95](https://github.com/Piggubr/cofrinho-app/pull/95))
- Situação: Valendo
- Contexto: a tela de cartões mostrava o "a pagar" e o painel avisava a fatura vencendo mesmo depois de paga. Cada compra no cartão já conta nos Gastos e na Sobra no dia em que foi feita.
- Decisão: "Marcar como paga" (`PUT /api/accounts/{id}/statement/paid?mes=AAAA-MM`) grava em `payment_accounts.last_paid_month` a última fatura paga, e as anteriores contam como pagas também, como em `recurring_bills`. Não lança gasto. A fatura ganha `paga`, e a paga sai do aviso do painel e do lembrete por e-mail e push. Desfazer (`DELETE`, mesma rota) volta a marca para o mês anterior, então as faturas seguintes também voltam a ficar em aberto. Marcar de novo dá 204, e uma fatura que ainda não começou dá 422.
- Alternativas: lançar o pagamento como gasto, que conta as compras duas vezes; uma tabela com cada mês pago, que precisaria entrar na exportação, na exclusão e na mudança de família (LGPD), para um caso raro (pagar março sem ter pago fevereiro).
- Consequências: não registra pagamento parcial nem a conta de onde saiu o dinheiro. O iOS e o Android nativos mostram o mesmo botão na tela de cartões (etapa 5 e A5).
- Onde ver: `ContasECartoes.pagarFatura`, [DATA_MODEL.md, "Fatura de cartão"](DATA_MODEL.md#fatura-de-cartão)

## D-087: Salário em duas parcelas numa receita só
- Data: 27/10/2026 ([PR #95](https://github.com/Piggubr/cofrinho-app/pull/95))
- Situação: Valendo
- Contexto: no Brasil, muita gente recebe o salário em duas vezes (adiantamento no dia 20 e o resto no dia 5), com valores diferentes.
- Decisão: a receita recorrente pode ter uma segunda parcela com dia e valor próprios (`recurring_bills.second_due_day`, `second_amount`), digitados pela pessoa. O valor e o dia de sempre são a 1ª parcela. A segunda tem o próprio `second_paid_month`, e o job diário lança cada parcela no dia dela, uma vez por mês, a segunda como "<descrição> (2ª parcela)". Ela começa na próxima vez que o dia dela chegar: se já passou no mês do cadastro, só no mês seguinte.
- Alternativas: duas receitas recorrentes separadas, que funcionam hoje sem mudar o backend, mas aparecem como dois salários na lista.
- Consequências: só a receita lançada pelo formulário de receita tem segunda parcela; a conta fixa de gasto não usa. Editar a receita recorrente ainda não muda a segunda parcela: para trocar, pare de repetir e lance de novo.
- Onde ver: `ContasFixasService.receberSegundaParcela`, [DATA_MODEL.md, `recurring_bills`](DATA_MODEL.md)

# Segurança e privacidade

## D-012: Dois tokens: access curto e refresh longo
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o Apps Script emitia um token de 30 dias no corpo de toda requisição e varria as propriedades do script a cada login para expirar sessões.
- Decisão: access token JWT RS256 de 30 minutos no cabeçalho `Authorization`, e refresh opaco de 30 dias, guardado só como hash SHA-256 e trocado a cada uso. Os serviços conferem o access sozinhos, pela chave pública em `/.well-known/jwks.json`. O ID token do Google também é conferido localmente.
- Alternativas: um token só, como antes.
- Consequências: nenhuma chamada ao identity no caminho de uma requisição comum. Renovar duas vezes em paralelo derruba a sessão (veja D-050).
- Onde ver: [Autenticação: dois tokens no lugar de um](../backend/README.md#autenticação-dois-tokens-no-lugar-de-um)

## D-013: Serviço recusa subir com segredo fraco
- Data: 01/10/2026 (chave JWT); 08/10/2026 (produção)
- Situação: Valendo
- Contexto: um valor padrão esquecido num deploy assinaria tokens com uma chave conhecida, e a chave de desenvolvimento antiga ficou no histórico do Git.
- Decisão: o identity não sobe sem `JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY`. Com `PIGGU_AMBIENTE=producao`, os serviços recusam senha de banco fraca, chave JWT de desenvolvimento, `PIGGU_CPF_CHAVE` ausente e endereços em `http://`. A chave que vazou é recusada em qualquer ambiente.
- Alternativas: valor padrão no `application.yml`.
- Consequências: o `TokenConfigTest` quebra se alguém reintroduzir um padrão. Trocar a chave JWT derruba todas as sessões.
- Onde ver: [Chaves de assinatura](../backend/README.md#chaves-de-assinatura), [implantação, seções 1 a 3](implantacao-producao.md)

## D-014: Refresh em cookie HttpOnly no site
- Data: 08/10/2026 (B7)
- Situação: Valendo
- Contexto: com "lembrar acesso", o refresh de 30 dias ficava no `localStorage`, e qualquer XSS levaria uma sessão de um mês.
- Decisão: no site, o refresh vai num cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, e o access fica só na memória.
- Alternativas: `localStorage`, como antes.
- Consequências: o JavaScript nunca lê o refresh. O app nativo segue outra regra (D-058).
- Onde ver: [relatório de LGPD, item 10](relatorio-lgpd-seguranca.md#10-refresh-token-em-localstorage--média)

## D-015: Famílias, papéis e isolamento no banco
- Data: 09/10/2026
- Situação: Valendo
- Contexto: cada pessoa tem conta própria, mas a casa divide dados e o plano.
- Decisão: cada conta pertence a uma família, com os papéis `ADMIN`, `TITULAR`, `PARCEIRO` e `MEMBRO`. O id da família vai no token, e toda entidade tem `household_id` com `@TenantId`, então o Hibernate filtra toda consulta pela família.
- Alternativas: não registradas.
- Consequências: SQL nativo não passa pelo filtro. Job que grava usa `FamiliaAtual.como(...)`. Os testes `IsolamentoEntreFamiliasTest` provam o isolamento.
- Onde ver: [Famílias e papéis](../backend/README.md#famílias-e-papéis)

## D-016: Cada um vê o seu; ver tudo do parceiro só com consentimento
- Data: 22/10/2026 ([PR #57](https://github.com/Piggubr/cofrinho-app/pull/57))
- Situação: Valendo
- Contexto: casal e família moram juntos, mas nem tudo o que um lança é da casa.
- Decisão: gasto, receita, conta fixa, conta, cartão e objetivo têm a marca "compartilhado". Sem ela, só quem lançou vê. Ver tudo do parceiro depende de um consentimento dado a uma pessoa (`users.shows_all_to`), que cai se ela deixa de ser parceira. O titular só vê tudo de quem é menor de idade.
- Alternativas: não registradas.
- Consequências: compartilhar é grátis em qualquer plano. Num item compartilhado, todos editam, mas só quem criou apaga.
- Onde ver: [planos Premium, "Compartilhamento"](planos-premium.md#compartilhamento-entre-o-casal-e-a-família)

## D-086: No plano casal, o convidado entra como parceiro
- Data: 27/10/2026 ([PR #95](https://github.com/Piggubr/cofrinho-app/pull/95))
- Situação: Valendo
- Contexto: quem assina o plano casal convida o marido ou a esposa. A pessoa entrava como membro, e o titular ainda precisava convidar de novo para a parceria.
- Decisão: com o plano casal vigente, quem aceita o convite da família entra direto como parceiro, se a casa ainda não tem um (nem convite de parceria esperando) e a pessoa não é menor de idade. Aceitar o convite vale como o aceite da parceria. Quem entra depois entra como membro. A conta nova ainda não tem data de nascimento; se depois ela informar uma de menor, volta a membro.
- Alternativas: o titular escolher "convidar como parceiro" no convite, que precisaria de uma coluna nova em `household_invites`.
- Consequências: ver tudo do outro continua dependendo do consentimento de cada um no perfil (D-016). No plano família e no individual, nada muda.
- Onde ver: `FamiliaService.entraComoParceiro`, `UserAccount.informarNascimento`

## D-088: Entrar pelo Google pede o mesmo cadastro do e-mail
- Data: 27/10/2026 ([PR #95](https://github.com/Piggubr/cofrinho-app/pull/95))
- Situação: Valendo
- Contexto: o cadastro por e-mail pede apelido, nascimento, CPF e celular. Quem entrava pelo Google (ou pela Apple) só via "Sobre você" com apelido e nascimento, e nunca informava CPF nem celular. Desde 13/10 o token já não levava a claim `cpf`, então nada barrava a conta sem CPF, ao contrário do que diz D-018.
- Decisão: `UserAccount.cadastroIncompleto()` falta apelido, nascimento, CPF (ou documento de conta antiga) ou celular. Com o cadastro incompleto, o access token leva `cadastro=false` e os serviços de dados respondem 403 `CADASTRO_PENDENTE` (o identity não barra: é nele que se completa, exporta ou apaga a conta). O perfil ganha `cadastroCompleto` e `celularInformado`, e "Sobre você" (`PUT /api/me/sobre-voce`) passa a pedir também CPF e celular quando faltam. Depois de salvar, o app renova o token. Vale para as contas que já existem: no próximo acesso, quem não tem caem na tela.
- Alternativas: pedir os dados só para contas novas; voltar a claim `cpf`, que só cobria o CPF.
- Consequências: os tokens internos (exclusão da conta, mudança de família, lembrete diário) não levam a claim, para funcionarem com o cadastro pela metade. O iOS nativo precisa pedir CPF e celular na `SobreVoceView` antes de ir para o TestFlight; até lá, a conta incompleta recebe 403 nos dados. O celular ainda não é confirmado por SMS.
- Onde ver: `UserAccount.cadastroIncompleto`, `TokenService.gerarAccessToken`, `CpfObrigatorio`, `frontend/src/app/features/sobre-voce`

## D-017: Entrar com e-mail e senha, Google como opção
- Data: 14/10/2026 ([PR #22](https://github.com/Piggubr/cofrinho-app/pull/22))
- Situação: Valendo
- Contexto: só existia o botão do Google, e quem não tinha conta Google não entrava.
- Decisão: a mesma conta entra por e-mail e senha ou pelo Google. Senha em BCrypt custo 12, com 8 caracteres ou mais, letra e número. Cinco erros travam a senha por 15 minutos. Senha errada e e-mail sem conta dão a mesma mensagem.
- Alternativas: confirmar o e-mail no cadastro ficou para depois. Enquanto não confirma, a conta não vira ADMIN nem usa convite, e o primeiro login Google no e-mail apaga a senha de quem cadastrou antes.
- Consequências: o limite por IP do login vale também para senha e cadastro.
- Onde ver: [Entrar com e-mail e senha](../backend/README.md#entrar-com-e-mail-e-senha)

## D-018: CPF obrigatório, único e cifrado
- Data: 12/10/2026 ([PR #11](https://github.com/Piggubr/cofrinho-app/pull/11))
- Situação: Valendo
- Contexto: qualquer pessoa podia abrir várias contas.
- Decisão: toda conta informa o CPF, e o mesmo CPF não abre outra conta. O número fica cifrado (AES-256-GCM), com um índice HMAC único. Depois de informado, não muda pelo app. Sem CPF, os serviços respondem 403 `CPF_PENDENTE`.
- Alternativas: não registradas.
- Consequências: `PIGGU_CPF_CHAVE` é obrigatória em produção e deve ser guardada junto com a senha do backup. Trocar a chave quebra o índice.
- Onde ver: [implantação, seção 2b](implantacao-producao.md#2b-chave-do-cpf)

## D-019: Documento de identidade de cada país
- Data: 14/10/2026 ([PR #22](https://github.com/Piggubr/cofrinho-app/pull/22))
- Situação: Substituída por D-020
- Contexto: quem mora fora do Brasil não tinha como se cadastrar.
- Decisão: o cadastro pedia o país e o documento dele (NIF, DNI, CURP e mais 10), conferido pelo dígito, guardado como o CPF.
- Alternativas: um identificador mundial não existe. EUA, Reino Unido e outros ficaram sem documento (o SSN é sensível demais).
- Consequências: as colunas `id_doc_*` continuam no banco para as contas antigas.
- Onde ver: [PR #22](https://github.com/Piggubr/cofrinho-app/pull/22)

## D-020: Só Brasil: CPF para todos
- Data: 22/10/2026 ([PR #56](https://github.com/Piggubr/cofrinho-app/pull/56))
- Situação: Valendo
- Contexto: o lançamento é só no Brasil, mas brasileiro que mora fora continua podendo usar o app.
- Decisão: o cadastro pede o CPF em qualquer país, e o catálogo de documentos estrangeiros saiu. Moeda, fuso, país e DDI ficam como estão. A leitura de recibo lê a moeda impressa na nota, e não mais euro.
- Alternativas: manter o catálogo de D-019.
- Consequências: conta antiga com documento estrangeiro continua "em dia". Apagar as colunas `country`, `id_doc_*`, `timezone` e `locale` ficou para depois de conferir produção.
- Onde ver: [PR #56](https://github.com/Piggubr/cofrinho-app/pull/56), [Entrar com e-mail e senha](../backend/README.md#entrar-com-e-mail-e-senha)

## D-021: Logs sem dado pessoal
- Data: 01/10/2026
- Situação: Valendo
- Contexto: log é uma cópia do dado fora do banco, sem controle de acesso e sem exclusão quando a pessoa pede (LGPD art. 6º).
- Decisão: toda linha traz `req=` (o `X-Request-Id` do gateway) e `user=` (o UUID da conta, nunca o e-mail). A linha de acesso usa o padrão da rota, não o caminho. Conteúdo de recibo, nome de item e e-mail não vão para o log. O Alloy ainda troca e-mail, CPF e token que escaparem.
- Alternativas: não registradas.
- Consequências: log novo só com ids e quantidades.
- Onde ver: [Logs](../backend/README.md#logs), [relatório de LGPD, "Logs e correlação"](relatorio-lgpd-seguranca.md#logs-e-correlação)

## D-022: Exportar e apagar a conta em todos os serviços
- Data: 08/10/2026 (B1)
- Situação: Valendo
- Contexto: a pessoa não conseguia pegar uma cópia dos dados nem pedir a exclusão (LGPD art. 18).
- Decisão: `GET /api/me/export` junta tudo num JSON, e `DELETE /api/me` apaga em cascata pelos serviços, chamados pelo identity com um token da própria pessoa. Qualquer falha aborta antes de a conta sair. O que é compartilhado com a família fica sem dono ("Ex-membro") em vez de sumir.
- Alternativas: só desativar a conta, como antes.
- Consequências: serviço novo com dado pessoal precisa responder `/api/meus-dados`. Exportar e apagar são sempre grátis.
- Onde ver: [relatório de LGPD, item 7](relatorio-lgpd-seguranca.md), [Por que quase não há chamadas entre serviços](../backend/README.md#por-que-quase-não-há-chamadas-entre-serviços)

## D-023: Consentimento na hora de usar IA e bancos
- Data: 08/10/2026 (B2)
- Situação: Valendo
- Contexto: Gemini e Pluggy recebem dado financeiro, e não havia aviso de privacidade nem registro de consentimento.
- Decisão: `/privacidade` e `/termos` abertos, aceite no cadastro gravado com data e versão, e consentimento pedido no próprio ato (primeira leitura de recibo, antes de abrir o widget da Pluggy, primeiro uso do assistente).
- Alternativas: não registradas.
- Consequências: mudou o texto do aviso ou dos termos? Suba a versão, e quem já tem conta passa pelo reaceite.
- Onde ver: [relatório de LGPD, item 8](relatorio-lgpd-seguranca.md)

## D-024: Dados apagados sozinhos depois de um prazo
- Data: 08/10/2026 (B8)
- Situação: Valendo
- Contexto: nada era apagado por tempo, e conta revogada guardava tudo para sempre (LGPD art. 15 e 16).
- Decisão: jobs diários apagam sessões vencidas, contas sem acesso há 24 meses ou desativadas há 12, fotos órfãs e bancos parados há 90 dias.
- Alternativas: não registradas.
- Consequências: dado novo com prazo de vida entra nesses jobs.
- Onde ver: [relatório de LGPD, itens 13 e "Segunda rodada"](relatorio-lgpd-seguranca.md)

## D-025: Limite de chamadas em três camadas
- Data: 08/10/2026 (rotas abertas e por conta, B5); 19/10/2026 (gateway)
- Situação: Valendo
- Contexto: login e rotas abertas aceitavam volume ilimitado.
- Decisão: 600 por minuto por IP no gateway; login 20 e demais 120 por minuto por IP nas rotas abertas; por conta, escrita 120, envio 15 e busca cara 60 por minuto. A recusa é 429 com `Retry-After`.
- Alternativas: um teto apertado no gateway. É largo de propósito, porque muita gente no celular sai pelo mesmo IP (CGNAT).
- Consequências: rota aberta nova entra no limite por IP.
- Onde ver: [observabilidade, "Rate limit"](observabilidade.md#rate-limit)

## D-026: Caddy como única porta pública
- Data: 08/10/2026 (B6)
- Situação: Valendo
- Contexto: sem servidor web no repositório, o front não tinha CSP nem HSTS, e a fonte do Google entregava o IP de todo visitante.
- Decisão: o Caddy tira o certificado, manda HTTP para HTTPS e põe HSTS, CSP e os outros cabeçalhos. Gateway e Postgres só escutam em `127.0.0.1`. A fonte Sora é servida pelo próprio app.
- Alternativas: não registradas.
- Consequências: script ou frame de outro domínio exige mudar a CSP no `deploy/Caddyfile`.
- Onde ver: [implantação, seção 5](implantacao-producao.md#5-https-e-cabeçalhos), [revisão de segurança](revisao-seguranca-pre-publicacao.md)

## D-027: Login do Google no app pelo navegador do celular
- Data: 24/10/2026 ([PR #79](https://github.com/Piggubr/cofrinho-app/pull/79))
- Situação: Valendo
- Contexto: dentro do WebView do Capacitor, o botão do Google abria o Safari e a tela ficava branca. O Google também bloqueia login OAuth dentro de WebView.
- Decisão: seguir a RFC 8252. O app abre o Google no navegador do sistema com um verificador que não sai do aparelho, o identity guarda o resultado por 5 minutos sob um código de uso único e o app volta por `piggu://entrar?google=<codigo>`.
- Alternativas: o botão do Google de sempre, que continua só no site.
- Consequências: a URI `https://piggu.com.br/api/auth/google/app` precisa estar no cliente OAuth do Google Cloud. A rota tem uma entrada de CORS própria no gateway.
- Onde ver: [PR #79](https://github.com/Piggubr/cofrinho-app/pull/79)

## D-080: E-mail da conta sai depois do commit, fora da requisição
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: "esqueci a senha" chamava a API de e-mail dentro da requisição só quando a conta existia; o tempo da resposta dizia quem usa o Piggu, e o e-mail lento prendia uma conexão do banco.
- Decisão: link de senha nova, confirmação de e-mail e aviso de cadastro repetido saem depois do commit, numa virtual thread. O aviso de fim do teste continua dentro do webhook, porque a falha dele faz a Stripe reenviar.
- Alternativas: fila de e-mails no banco, que ainda não se paga.
- Consequências: o contexto de log da requisição não vai junto; e-mail que falha só aparece no log.
- Onde ver: `ConfirmacaoDeEmail.depoisDoCommit`

# Dados

## D-028: Fora do identity, só o id da conta
- Data: 11/10/2026 (S3)
- Situação: Valendo
- Contexto: os serviços marcavam quem lançou cada coisa pelo e-mail, espalhando dado pessoal por seis bancos.
- Decisão: só o identity guarda e-mail. Nos outros serviços, quem lançou, dividiu ou avaliou é o UUID da conta. O que o Piggu faz sozinho tem o id zero.
- Alternativas: não registradas.
- Consequências: tabela nova fora do identity não ganha coluna de e-mail.
- Onde ver: [implantação, seção 3](implantacao-producao.md#3-senha-do-postgres), [relatório de LGPD, "Logs e correlação"](relatorio-lgpd-seguranca.md#logs-e-correlação)

## D-029: Migration aplicada nunca muda
- Data: 01/10/2026 (relatório de LGPD)
- Situação: Valendo
- Contexto: o Flyway confere o checksum de cada migration e para a subida em produção se uma mudou.
- Decisão: correção vira migration nova (a V6 apagou os e-mails que a V1 tinha gravado, sem tocar na V1). O perfil de teste usa `ddl-auto: validate`, e o `MigrationsTest` derruba qualquer diferença entre migration e entidade.
- Alternativas: editar a migration antiga.
- Consequências: o Sonar ignora o UPDATE sem WHERE nas migrations, porque é de propósito.
- Onde ver: [relatório de LGPD, item 12](relatorio-lgpd-seguranca.md), [qualidade-sonar, "Regras desligadas"](qualidade-sonar.md#regras-desligadas), [Três testes que merecem atenção](../backend/README.md#três-testes-que-merecem-atenção)

## D-030: Apagar ou renomear coluna em dois deploys
- Data: 22/10/2026
- Situação: Valendo
- Contexto: o rollback automático (D-035) volta o código, mas não o banco.
- Decisão: migration só acrescenta (tabela, coluna, índice). Apagar ou renomear exige dois deploys: primeiro a versão que para de usar a coluna, depois a que a remove.
- Alternativas: não registradas.
- Consequências: a versão anterior sempre sobe com o banco novo. É a mesma ideia da API que só acrescenta (D-059).
- Onde ver: [implantação, seção 8, "Rollback automático"](implantacao-producao.md#8-deploy-automático-pelo-github-actions)

## D-031: Painel analítico só lê contagens
- Data: 20/10/2026 ([PR #47](https://github.com/Piggubr/cofrinho-app/pull/47))
- Situação: Valendo
- Contexto: medir o uso do app sem expor dado de ninguém.
- Decisão: o painel "Uso do app" entra com o usuário `piggu_analitico`, só de leitura, e só lê views do schema `analitico`, que devolvem contagens. Nenhuma view traz e-mail, nome, documento, valor, descrição ou id de pessoa ou família.
- Alternativas: não registradas.
- Consequências: o `MigrationsTest` e o `MetricasDeUsoTest` quebram se uma coluna assim aparecer. View nova usa o placeholder `${esquema_analitico}`.
- Onde ver: [observabilidade, "Painel analítico"](observabilidade.md#painel-analítico)

## D-032: Backup diário cifrado, fora do servidor e testado
- Data: 11/10/2026
- Situação: Valendo
- Contexto: perder o disco do servidor seria perder o dinheiro registrado de todas as famílias (LGPD art. 46).
- Decisão: `backup.sh` cifra os seis bancos e as fotos num `.tar.gpg` diário, guarda 7 diários e 4 semanais e manda uma cópia para um bucket fora do servidor (Backblaze B2 sugerido). Toda semana, um teste restaura e confere tabela por tabela. O CI testa os scripts em todo PR.
- Alternativas: Cloudflare R2 ou S3 servem do mesmo jeito. `rclone sync` fica proibido, para um disco vazio não apagar a cópia de fora.
- Consequências: a senha do backup e a `PIGGU_CPF_CHAVE` ficam guardadas fora do servidor.
- Onde ver: [implantação, seção 6](implantacao-producao.md#6-backup-diário-testado)

## D-077: A família nova de quem sai tem id previsível
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: sair ou ser removido cria uma família nova e pede a cada serviço que mova os dados para ela. Com id aleatório, um serviço fora no meio desfazia o identity, mas não quem já tinha movido; a nova tentativa ia para outro id e os dados ficavam numa família que não existe.
- Decisão: o id sai da conta, da família de onde ela sai e de um contador que pula ids já usados. A nova tentativa manda para o mesmo lugar.
- Alternativas: saga com compensação em cada serviço.
- Consequências: se o convite vencer entre uma tentativa que falhou e a seguinte, o que já foi movido fica na família que convidou.
- Onde ver: `FamiliaService.mudarParaFamiliaPropria`

# Infraestrutura e deploy

## D-033: Um servidor Oracle Cloud Always Free, com Docker Compose
- Data: 11/10/2026 (estudo do alpha); produção desde ~20/10/2026
- Situação: Valendo
- Contexto: o Piggu são sete processos Java mais o Postgres, que pedem uns 4 a 6 GB de RAM, e a meta do alpha era custo zero.
- Decisão: tudo num servidor Oracle Cloud Always Free (ARM), com o `docker-compose.yml` do repositório e o Postgres 16 na mesma máquina.
- Alternativas: Cloudflare Pages ou Workers não rodam o backend Java. Postgres gerenciado grátis (Neon, Supabase) exigiria adaptar os seis bancos e tem limites pequenos.
- Consequências: as imagens precisam ser `linux/arm64` (D-035). O alpha fica atrás do Cloudflare Access.
- Onde ver: relatório "Alpha fechado do Piggu" na pasta do projeto (fora do repositório), [PR #69](https://github.com/Piggubr/cofrinho-app/pull/69), [implantação, seção 8](implantacao-producao.md#8-deploy-automático-pelo-github-actions)

## D-034: Só a `main` publica, com chave SSH presa ao script
- Data: 12/10/2026 ([PR #10](https://github.com/Piggubr/cofrinho-app/pull/10))
- Situação: Valendo
- Contexto: atualizar o servidor exigia entrar por SSH e rodar os comandos à mão.
- Decisão: cada merge na `main` com o CI todo verde publica sozinho. A chave do Actions só roda `deploy/atualizar.sh` (`command=` e `restrict` no `authorized_keys`), que só aceita commit que está na `main`. Os segredos ficam no ambiente `producao`, que só aceita a `main`.
- Alternativas: deploy à mão, que continua possível pelo mesmo script.
- Consequências: sem a variável `DEPLOY_LIGADO=true`, o job fica pulado.
- Onde ver: [implantação, seção 8](implantacao-producao.md#8-deploy-automático-pelo-github-actions), [branches](branches.md#por-que-assim)

## D-035: Imagens montadas no CI, para ARM, com rollback automático
- Data: 23/10/2026 ([PR #58](https://github.com/Piggubr/cofrinho-app/pull/58) e [PR #69](https://github.com/Piggubr/cofrinho-app/pull/69))
- Situação: Valendo
- Contexto: o servidor compilava Java e o site a cada deploy. Depois, imagens `amd64` derrubaram o servidor ARM, e o rollback caiu no mesmo erro.
- Decisão: o CI monta as imagens uma vez, passa o Trivy e publica no GHCR com o sha do commit, para `linux/arm64` (variável `PIGGU_PLATAFORMA`). Se um serviço não fica saudável em 5 minutos, o script volta sozinho para o commit anterior.
- Alternativas: sem imagem no registro, ou com imagem de outro processador, o script compila no servidor, como antes.
- Consequências: o que vai ao ar é o que o CI conferiu. O servidor guarda só as imagens da versão no ar e da anterior.
- Onde ver: [implantação, seção 8](implantacao-producao.md#8-deploy-automático-pelo-github-actions)

## D-036: Monitoramento no próprio servidor
- Data: 20/10/2026 ([PR #47](https://github.com/Piggubr/cofrinho-app/pull/47))
- Situação: Valendo
- Contexto: era preciso ver performance, erros, logs e uso sem mandar dado para fora.
- Decisão: Prometheus (1 ano), Loki (30 dias), Alloy, node-exporter e Grafana no perfil `observabilidade` do Compose, sem porta pública (Grafana em `127.0.0.1:3000`, por túnel SSH). Alertas por e-mail. O Alloy lê os arquivos de log só para leitura, não o socket do Docker.
- Alternativas: o socket do Docker, que daria controle total da máquina ao container.
- Consequências: cerca de 1,4 GB de memória a mais. Painel muda por JSON no repositório, nunca pela tela.
- Onde ver: [observabilidade](observabilidade.md)

## D-037: Versão de produção pela data
- Data: 23/10/2026 ([PR #58](https://github.com/Piggubr/cofrinho-app/pull/58))
- Situação: Valendo
- Contexto: faltava um histórico do que foi ao ar e quando.
- Decisão: depois de publicar, o job "Versao e notas" cria uma Release `vAAAA.MM.DD` (com `.2`, `.3` no mesmo dia), com as notas dos PRs que entraram.
- Alternativas: não registradas.
- Consequências: o sha de cada versão fica em *Releases*, para voltar à mão. Os apps têm a própria versão (1.0.0 desde 12/10/2026; o nativo será a 2.0.0, D-057).
- Onde ver: [implantação, seção 8, "Versões"](implantacao-producao.md#8-deploy-automático-pelo-github-actions)

## D-069: Repositório privado
- Data: confirmada em 26/10/2026
- Situação: Valendo
- Contexto: os documentos se contradiziam. O guia de implantação e o PR #58 tratavam o repositório como público; o `docs/qualidade-sonar.md`, o `docs/branches.md` e a revisão de segurança, como privado.
- Decisão: o repositório é privado (resposta do Dudu).
- Alternativas: público. A revisão de segurança pediu para não abrir enquanto o GitHub não limpar os commits antigos, de antes da reescrita do histórico.
- Consequências: o servidor busca o código com uma deploy key só de leitura. As imagens do GHCR ficam privadas, porque levam o código compilado. O CodeQL só avisa (sem o GitHub Code Security) e o Sonar fica no plano Free para privado (D-064).
- Onde ver: [implantação, seção 8](implantacao-producao.md#8-deploy-automático-pelo-github-actions), [revisão de segurança, seção 8](revisao-seguranca-pre-publicacao.md), [branches](branches.md)

## D-070: E-mails pela Resend
- Data: 11/10/2026
- Situação: Valendo
- Contexto: o Piggu manda e-mail de confirmação, de "esqueci minha senha", os avisos do dia e os alertas do monitoramento.
- Decisão: tudo sai pela API da Resend, com remetente `@piggu.com.br` (`PIGGU_EMAIL_API_KEY` e `PIGGU_EMAIL_REMETENTE`).
- Alternativas: a Resend foi escolha do Claude, sem comparação registrada com outros serviços (Dudu, 26/10/2026).
- Consequências: o domínio precisa de SPF, DKIM e DMARC verificados na Resend. Em 19/10/2026 o DNS não tinha nenhum deles, e o Dudu disse em 26/10/2026 que os e-mails parecem não estar funcionando. Sem e-mail configurado, o identity escreve no log o link que mandaria.
- Onde ver: [e-mail do domínio](email-do-dominio.md), [implantação, seção 4](implantacao-producao.md#4-demais-variáveis)

# Pagamentos e Premium

## D-038: O backend aplica os limites do Premium
- Data: 01/10/2026
- Situação: Valendo
- Contexto: esconder um botão no front não impede ninguém de chamar a API.
- Decisão: o backend recusa com 422 `PLANO_PREMIUM`, e o front só esconde e convida. O plano vai no token (claim `plano`). O núcleo (gastos, cofrinho, compras, relatório do mês) é grátis. Ver, apagar, exportar e desconectar são sempre grátis, porque Premium vencido nunca prende dado da pessoa.
- Alternativas: não registradas.
- Consequências: recurso pago novo precisa do teste no backend, não só no front. A divisão completa e o porquê de cada linha estão na tabela de planos.
- Onde ver: [planos Premium, "Divisão"](planos-premium.md#divisão)

## D-039: Premium da família inteira
- Data: 01/10/2026
- Situação: Substituída por D-040
- Contexto: o Premium nasceu como coisa da casa.
- Decisão: o titular assinava e todos da família usavam.
- Alternativas: não registradas.
- Consequências: as assinaturas que já existiam viraram individuais de quem pagou (migration `V18`).
- Onde ver: [planos Premium, "Preços"](planos-premium.md#preços)

## D-040: Três planos: individual, casal e família
- Data: 11/10/2026 (individual e família); 22/10/2026 (casal, [PR #57](https://github.com/Piggubr/cofrinho-app/pull/57))
- Situação: Valendo
- Contexto: decisão do Eduardo de cobrar diferente por quem usa.
- Decisão: individual libera só quem assinou; casal libera quem assinou e o parceiro; família libera até 5 pessoas, contando convites pendentes. Uma assinatura por casa. O tipo vem do preço na Stripe ou do produto da loja ("casal" ou "familia" no id).
- Alternativas: não registradas.
- Consequências: trocar de plano pelo Portal do Cliente funciona sem código novo. Sem os preços do casal na Stripe, o card some.
- Onde ver: [planos Premium, "Preços"](planos-premium.md#preços)

## D-041: Stripe no site, RevenueCat nas lojas
- Data: 01/10/2026 (Stripe); 09/10/2026 (RevenueCat)
- Situação: Valendo; a interface `ProvedorDePagamento` saiu (D-081)
- Contexto: Apple e Google exigem compra dentro do app para assinatura digital vendida no app.
- Decisão: no site, Stripe (Checkout e Portal do Cliente, API REST sem SDK) atrás da interface `ProvedorDePagamento`. Nas lojas, RevenueCat, que valida o recibo e avisa o backend por webhook. O preço do app é 15% maior por causa da taxa das lojas, e a tela web só mostra o preço do site.
- Alternativas: trocar de provedor é escrever outra implementação de `ProvedorDePagamento`.
- Consequências: a conta no RevenueCat ainda não foi criada. O preço do iPhone está em aberto (D-048).
- Onde ver: [planos Premium, "Como funciona"](planos-premium.md#como-funciona)

## D-042: Premium só muda pelo webhook assinado
- Data: 01/10/2026
- Situação: Valendo
- Contexto: voltar da página de pagamento não prova que a pessoa pagou.
- Decisão: o Premium só muda por webhook assinado (HMAC, tolerância de 5 minutos). Aviso antigo que chega depois de um mais novo é ignorado. O webhook do RevenueCat é idempotente pelo id do aviso.
- Alternativas: confiar na volta do Checkout.
- Consequências: sem o webhook configurado, ninguém vira Premium.
- Onde ver: [planos Premium, "Como funciona"](planos-premium.md#como-funciona)

## D-043: Cobrar pelo Open Finance sim, pela desconexão nunca
- Data: 01/10/2026
- Situação: Valendo
- Contexto: a Resolução Conjunta nº 1/2020 do Banco Central proíbe tarifa pelo compartilhamento de dados.
- Decisão: o Open Finance pode ser Premium, porque a resolução permite cobrar pela agregação. Desconectar o banco é sempre grátis e a qualquer momento (art. 15).
- Alternativas: não registradas.
- Consequências: é leitura da norma, não parecer jurídico; vale um advogado antes de cobrar. Falta desconectar sozinho o banco 30 dias depois do Premium vencer.
- Onde ver: [planos Premium, "Posso cobrar pelo Open Finance?"](planos-premium.md#posso-cobrar-pelo-open-finance)

## D-044: Sete dias de teste e sete de arrependimento
- Data: 01/10/2026 (teste); 09/10/2026 (arrependimento)
- Situação: Valendo
- Contexto: o Código de Defesa do Consumidor (art. 49) dá 7 dias de arrependimento.
- Decisão: 7 dias de teste grátis (`PIGGU_DIAS_DE_TESTE`), uma vez por conta. Nos 7 primeiros dias pagos, o botão "Cancelar e pedir reembolso" cancela e devolve pela Stripe. Cancelar é tão fácil quanto assinar.
- Alternativas: não registradas.
- Consequências: o teste fica marcado quando a Stripe avisa que começou.
- Onde ver: [planos Premium, "Falta antes de cobrar de verdade"](planos-premium.md#falta-antes-de-cobrar-de-verdade)

## D-045: Open Finance desligado por enquanto
- Data: 11/10/2026, antes da 1.0.0
- Situação: Valendo (com lembrete de reverter)
- Contexto: custo (resposta do Dudu em 26/10/2026). A Pluggy cobra por conexão (planos Premium, "Divisão").
- Decisão: `PIGGU_OPEN_FINANCE=false` por padrão: o card de bancos some, conexão nova é recusada e o recurso sai da lista do Premium.
- Alternativas: deixar ligado com a Pluggy configurada, como antes.
- Consequências: para voltar, `PIGGU_OPEN_FINANCE=true`, as variáveis `PLUGGY_*` e o perfil `open-finance`, sem mexer no código. Quando religar ainda não foi definido (veja as perguntas).
- Onde ver: [planos Premium, "Desligado por enquanto"](planos-premium.md#desligado-por-enquanto-lembrar-de-reverter)

## D-046: Leitor próprio de nota primeiro, Gemini de reserva
- Data: 01/10/2026 (branch `feature/leitor-nota-ocr`)
- Situação: Substituída por D-047
- Contexto: cada leitura pelo Gemini custa, e a foto sai do servidor.
- Decisão: o leitor próprio (Tesseract) lia primeiro, grátis até 10 por mês por família; o Gemini ficava de reserva no Premium.
- Alternativas: só o Gemini.
- Consequências: o código continua e volta com `PIGGU_LEITOR_PROPRIO=true`.
- Onde ver: [planos Premium, "Divisão"](planos-premium.md#divisão)

## D-047: Nota fiscal direto no Gemini
- Data: 11/10/2026 ([PR #11](https://github.com/Piggubr/cofrinho-app/pull/11))
- Situação: Valendo (com lembrete de reverter)
- Contexto: custo (resposta do Dudu em 26/10/2026). O Gemini foi a primeira opção de IA considerada, sem comparação com outros provedores.
- Decisão: `PIGGU_LEITOR_PROPRIO=false` por padrão: toda foto vai ao Gemini, com o mesmo consentimento, 10 por mês por família no grátis e sem limite no Premium.
- Alternativas: o leitor próprio de D-046.
- Consequências: ao religar o leitor próprio, volte também o texto do aviso de privacidade ("Foto da nota fiscal" e Google Gemini).
- Onde ver: [planos Premium, "Desligado por enquanto"](planos-premium.md#desligado-por-enquanto-lembrar-de-reverter)

## D-048: Preço do Premium no iPhone
- Data: 25/10/2026
- Situação: Em aberto
- Contexto: a Apple fica com 15% a 30% da assinatura vendida no app. A tabela atual põe o preço do app 15% acima do site (D-041).
- Decisão: ainda não tomada.
- Alternativas: manter os +15%, subir mais, ou outro modelo.
- Consequências: precisa estar decidido antes de publicar o app nativo com StoreKit.
- Onde ver: [planos Premium, "Preços"](planos-premium.md#preços)

## D-078: Quem paga o Premium não muda de família com ele valendo
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: o aviso da Stripe vale para a família atual de quem assinou. Quem aceitava convite levava a renovação para a família nova, trocava o plano dela e tirava do titular dela o direito ao reembolso.
- Decisão: quem é o assinante de um Premium que ainda vale não aceita convite (409, `ASSINATURA_ATIVA`). Aviso de fim de outra assinatura não derruba o plano de quem paga agora.
- Alternativas: deixar entrar e mover a assinatura; o identity não sabe se a assinatura foi cancelada e só vale até o fim do período.
- Consequências: quem cancelou o anual espera o fim do período para entrar em outra família.
- Onde ver: `FamiliaService.aceitarConvite`, `Household.aplicarAssinatura`

## D-079: Cartão recusado dá 7 dias de Premium
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: em `past_due` a Stripe já moveu `current_period_end` para o fim do período que não foi pago; o anual recusado ganhava até um ano.
- Decisão: em `past_due`, o Premium vale até `current_period_start` mais 7 dias de carência.
- Alternativas: confiar só na régua de cobrança do painel da Stripe.
- Consequências: se a Stripe conseguir cobrar depois dos 7 dias, o aviso de `active` devolve o Premium.
- Onde ver: `StripePagamentos`

## D-081: Stripe e RevenueCat sem interface
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: `ProvedorDePagamento` e `LojaDeApps` tinham uma implementação cada, e os testes já mockavam pelo Mockito.
- Decisão: o código usa `StripePagamentos` e `RevenueCatLoja` direto. A interface volta quando houver um segundo provedor.
- Alternativas: manter as interfaces "para depois" (D-041).
- Consequências: trocar de provedor é extrair a interface antes; o resto de D-041 continua valendo.
- Onde ver: `backend/piggu-identity/.../billing/`

# Apps (site, iOS e Android)

## D-049: Site em Angular, com telas sob demanda
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o app original entregava tudo num `index.html` de 678 linhas, e o navegador baixava todas as abas mesmo abrindo uma só.
- Decisão: Angular 22 com componentes standalone, signals e uma rota carregada sob demanda por tela. O guard é conveniência; quem decide o acesso é o backend, que recusa com 403.
- Alternativas: outros frameworks. Angular foi escolhido por ser a tecnologia que o Dudu conhece (resposta de 26/10/2026).
- Consequências: tela nova entra como rota sob demanda em `app.routes.ts`.
- Onde ver: [frontend/README.md, "Telas"](../frontend/README.md#telas)

## D-050: Só o login conhece os tokens; uma renovação por vez
- Data: 14/09/2026
- Situação: Valendo
- Contexto: o refresh é trocado a cada uso (D-012), então duas renovações em paralelo invalidam uma à outra e derrubam a sessão.
- Decisão: só o `AuthService` e o `authInterceptor` sabem dos tokens. Num 401, o interceptor renova e repete a chamada uma vez, e chamadas simultâneas dividem a mesma renovação (`shareReplay`).
- Alternativas: cada tela cuidar do token.
- Consequências: o `auth.interceptor.spec.ts` protege esse caso. O app nativo precisa da mesma regra.
- Onde ver: [Dois tokens, escondidos do resto do app](../frontend/README.md#dois-tokens-escondidos-do-resto-do-app)

## D-051: Textos em português do Brasil, prontos para tradução
- Data: 09/10/2026
- Situação: Valendo
- Contexto: o público é brasileiro (D-020), mas uma tradução futura não deveria exigir mexer em cada tela.
- Decisão: o idioma de origem é `pt-BR`. Todo texto leva `i18n` no template ou `$localize` no TypeScript, e `npm run i18n` extrai o catálogo.
- Alternativas: traduzir agora.
- Consequências: texto novo já entra em português do Brasil e marcado. `scripts/conferir-acentos.sh` pega acento quebrado.
- Onde ver: [Textos e tradução](../frontend/README.md#textos-e-tradução)

## D-052: Lista de compras risca antes da resposta
- Data: 14/09/2026
- Situação: Valendo
- Contexto: a lista é mais usada de pé, no supermercado, muitas vezes com sinal ruim.
- Decisão: riscar um item muda a tela na hora e desfaz se o servidor recusar.
- Alternativas: esperar a resposta, o que deixaria a tela lenta.
- Consequências: a mesma ideia vale para o gasto lançado sem sinal, que fica numa fila no aparelho.
- Onde ver: [Lista de compras marca antes de confirmar](../frontend/README.md#lista-de-compras-marca-antes-de-confirmar)

## D-053: Apps em Capacitor abrindo o site no ar
- Data: 17/10/2026 ([PR #39](https://github.com/Piggubr/cofrinho-app/pull/39))
- Situação: Valendo (o iOS sai conforme D-057)
- Contexto: ter app nas lojas sem manter outro código.
- Decisão: o app de produção (Capacitor 8, `appId br.com.piggu.app`) abre `https://piggu.com.br` dentro do WebView (`server.url`) e acompanha cada deploy sem binário novo. A demo empacota o site com dados de exemplo.
- Alternativas: empacotar o site dentro do app, o que pediria um binário novo a cada mudança.
- Consequências: o que só o navegador do sistema faz (login do Google) precisa de caminho próprio (D-027).
- Onde ver: `frontend/capacitor.config.ts`, [app iOS de demonstração](app-ios-demo.md)

## D-054: Barra de abas nativa com Liquid Glass no Capacitor
- Data: 10/10/2026 ([PR #5](https://github.com/Piggubr/cofrinho-app/pull/5))
- Situação: Valendo (até o app SwiftUI, D-055)
- Contexto: o Liquid Glass do iOS 26 não existe em HTML.
- Decisão: no iOS, a barra de baixo é um `UITabBar` nativo (plugin `AbasNativasPlugin`), e o resto continua sendo o Angular no WebView. No iOS 26 ela ganha o Liquid Glass sozinha.
- Alternativas: só o vidro em CSS, que continua no navegador.
- Consequências: o Swift foi escrito sem Xcode; altura e espaçamento precisam ser conferidos no aparelho.
- Onde ver: [LIQUID-GLASS-NATIVO.md](../frontend/ios/LIQUID-GLASS-NATIVO.md)

## D-055: iOS reescrito em SwiftUI
- Data: 25/10/2026
- Situação: Valendo
- Contexto: o próximo grande passo é ter apps nativos, mantendo o backend.
- Decisão: SwiftUI puro, mínimo iOS 17. Liquid Glass no iOS 26 e 27, e os materiais translúcidos do sistema no 17 e 18. Só SDKs da Apple, mais o login do Google. `@Observable`, async/await e SwiftData para o cache.
- Alternativas: continuar no Capacitor (D-053).
- Consequências: as regras de cada etapa ficam em `ios-nativo/CLAUDE.md`, criado na etapa 0 do plano.
- Onde ver: plano do iOS nativo (documento do projeto, fora do repositório), `ios-nativo/` quando existir

## D-056: Android reescrito em Kotlin, com Material 3
- Data: 25/10/2026
- Situação: Valendo
- Contexto: o mesmo de D-055, no Android.
- Decisão: Kotlin com Material 3 (Material You e Expressive), com o rosa do Piggu. Mínimo API 26 (Android 8.0), que cobre cerca de 96% dos aparelhos.
- Alternativas: continuar no Capacitor (D-053).
- Consequências: o Capacitor do Android fica até o Kotlin ficar pronto (D-057). Conta pessoal nova no Google Play exige um teste fechado de 12 pessoas por 14 dias.
- Onde ver: plano do Android nativo (documento do projeto, fora do repositório)

## D-057: Nativo entra como versão 2.0.0 no mesmo app das lojas
- Data: 25/10/2026
- Situação: Valendo
- Contexto: quem já instalou o app não deveria ter que baixar outro.
- Decisão: o nativo é a versão 2.0.0 do mesmo app, com o mesmo Bundle ID `br.com.piggu.app`. O Capacitor sai do iOS um mês depois de o 2.0.0 estar na loja sem problemas. O Capacitor do Android fica até o Kotlin ficar pronto.
- Alternativas: um app novo nas lojas.
- Consequências: por um tempo, versões velhas e novas falam com a mesma API (D-059).
- Onde ver: plano do app nativo (documento do projeto, fora do repositório)

## D-058: Tokens no app nativo
- Data: 25/10/2026
- Situação: Valendo
- Contexto: o app nativo não tem o cookie HttpOnly do navegador (D-014).
- Decisão: o access token fica só na memória e o refresh vai para o Keychain (iOS) ou o Keystore (Android). Para apps nativos, o backend devolve o refresh no corpo da resposta.
- Alternativas: o cookie do site.
- Consequências: na `develop` de hoje, `/api/auth/refresh` só lê o cookie; a etapa 1 do plano iOS implementa a resposta para o nativo no identity, que serve aos dois apps. Feito em 26/10/2026 (D-072).
- Onde ver: `backend/piggu-identity/.../api/AuthController.java`, plano do iOS nativo

## D-059: A API só acrescenta campos
- Data: 25/10/2026
- Situação: Valendo
- Contexto: app instalado não atualiza junto com o servidor (D-057).
- Decisão: a API nunca remove nem renomeia campos, só acrescenta. Um aviso de "atualize o app" aparece quando a versão instalada for velha demais.
- Alternativas: não registradas.
- Consequências: mudança que precisaria tirar um campo vira um campo novo, e o antigo continua. É a regra de D-030 aplicada à API. O contrato fica em `docs/API_SPEC.md`.
- Onde ver: `docs/API_SPEC.md`

## D-060: Features novas no site durante a migração
- Data: 25/10/2026
- Situação: Em aberto
- Contexto: enquanto o iOS e o Android nativos são escritos, toda feature nova no site também teria que ir para os dois apps.
- Decisão: ainda não tomada.
- Alternativas: congelar features novas no site durante a migração, ou fazer só no site e anotar para o nativo.
- Consequências: até decidir, toda feature nova no site deve ser anotada para o nativo.
- Onde ver: este registro

## D-075: Só sessão recusada encerra a sessão; só dado inválido tira gasto da fila
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: qualquer erro da chamada repetida depois de renovar deslogava a pessoa, e a fila sem sinal apagava os gastos guardados quando a sessão tinha acabado (401).
- Decisão: no front, a sessão só acaba quando a renovação volta 401 ou 403; sem sinal, servidor fora ou 429 só repassam o erro. A fila só descarta gasto que volta 400 ou 422; qualquer outra falha para a fila e guarda o resto. Ao abrir o app com a renovação recusada, o perfil guardado e as listas do service worker saem; sem sinal, ficam.
- Alternativas: não registradas.
- Consequências: gastos de quem saiu continuam guardados no aparelho até essa pessoa entrar de novo.
- Onde ver: `auth.interceptor.ts`, `fila-de-gastos.ts`

## D-076: O campo de valor do site lê igual ao app nativo
- Data: 26/10/2026 ([PR #89](https://github.com/Piggubr/cofrinho-app/pull/89))
- Situação: Valendo
- Contexto: "0.500" virava R$ 500 no site e era recusado no iPhone; o arredondamento em ponto flutuante dava "1,005" → 1,00 e "10,005" → 10,01.
- Decisão: `lerValor` segue as regras do `Formatos.lerValor` do app nativo: grupos de milhar com três dígitos e sem "0" na frente, no máximo dois decimais, sem sinal de menos. Fora disso, o campo fica vazio.
- Alternativas: arredondar no texto e aceitar mais casas.
- Consequências: valor negativo não entra mais pelo campo; nenhuma tela usava.
- Onde ver: `campo-de-valor.ts`, `ios-nativo/Piggu/Core/Formatos/Formatos.swift`

## D-071: Chiclete como cor padrão e Porquinho Neon como ícone
- Data: 20/10/2026 ([PR #42](https://github.com/Piggubr/cofrinho-app/pull/42))
- Situação: Valendo
- Contexto: o app abria no Rosa Piggu, em tons pastel.
- Decisão: o app abre no Chiclete, um rosa-choque, e o ícone padrão é o Porquinho Neon. Segundo o Dudu (26/10/2026), é uma cor mais chamativa, que pega mais as pessoas.
- Alternativas: o Rosa Piggu e o ícone Clássico continuam disponíveis e grátis.
- Consequências: quem não escolheu cor vê o Chiclete; quem tinha escolhido Rosa continua no Rosa.
- Onde ver: [PR #42](https://github.com/Piggubr/cofrinho-app/pull/42)

## D-072: Refresh no corpo só para quem pede, e Apple pelo e-mail
- Data: 26/10/2026
- Situação: Valendo
- Contexto: etapa 1 do plano iOS. O app nativo não tem o cookie do site (D-058), e o site não pode passar a ver o refresh (D-014).
- Decisão: o login (`/api/auth/google`, `/entrar` e `/apple`) com `refreshNoCorpo: true` devolve `refreshToken` no corpo e não põe cookie. `/api/auth/refresh`, `/logout` e `/me/senha` aceitam `refreshToken` no corpo; renovar pelo corpo devolve o novo no corpo. O refresh que chegou pelo cookie nunca volta no corpo, peça o cliente o que pedir: um XSS no site consegue renovar, mas não leva o refresh. Entrar com Apple confere o identity token contra o JWKS da Apple (emissor, Bundle ID, validade, e-mail verificado e o nonce, que é o SHA-256 do valor que o app manda) e acha a conta pelo e-mail, como o Google. Sem `APPLE_CLIENT_IDS` a rota responde 501.
- Alternativas: um cabeçalho `X-Piggu-Cliente: nativo`, que pediria abrir o CORS do gateway e que o próprio site poderia mandar. Guardar o `sub` da Apple numa coluna nova, que fica para quando o e-mail não bastar.
- Consequências: quem escolhe "Ocultar meu e-mail" na Apple recebe um e-mail de retransmissão (`privaterelay.appleid.com`) e ganha uma conta separada da que tem pelo Google. Falta revogar o token na Apple quando a conta é apagada, que a App Store exige: precisa da chave do Sign in with Apple e entra antes da revisão.
- Onde ver: `backend/piggu-identity/.../api/AuthController.java`, `.../apple/AppleIdTokenVerifier.java`, [API_SPEC, "Entrar e manter a sessão"](API_SPEC.md#entrar-e-manter-a-sessão)

# Processo

## D-061: `develop` junta, `main` é produção
- Data: 12/10/2026 ([PR #10](https://github.com/Piggubr/cofrinho-app/pull/10))
- Situação: Valendo
- Contexto: nenhuma branch tinha regra, e qualquer uma recebia push direto.
- Decisão: todo trabalho sai da `develop` e volta para ela por PR. A `main` só recebe PR da `develop` ou de `hotfix/...` (conferido pelo job "Origem do PR para a main"). Ninguém dá push direto, força push ou apaga as duas.
- Alternativas: não registradas.
- Consequências: hotfix volta da `main` para a `develop` em outro PR. As regras pedem 0 aprovações enquanto o trabalho é de uma pessoa só.
- Onde ver: [branches](branches.md)

## D-062: Dependabot abre PR na `develop`
- Data: 17/10/2026 ([PR #35](https://github.com/Piggubr/cofrinho-app/pull/35))
- Situação: Valendo
- Contexto: o Dependabot abriu 9 PRs contra a `main`, e todos falharam no check de origem.
- Decisão: `target-branch: develop` em cada bloco do `.github/dependabot.yml`.
- Alternativas: aceitar PR do Dependabot na `main`, o que furaria D-061.
- Consequências: o Dependabot lê o arquivo da branch padrão, então mudança nele só vale depois de chegar na `main`.
- Onde ver: [PR #35](https://github.com/Piggubr/cofrinho-app/pull/35)

## D-063: CI completo em todo PR
- Data: 23/10/2026 ([PR #58](https://github.com/Piggubr/cofrinho-app/pull/58))
- Situação: Valendo
- Contexto: o deploy precisa de confiança de que o código está bom.
- Decisão: o CI roda tudo a cada PR. Lint (ESLint e Checkstyle), testes do backend e do front, ponta a ponta, smoke e regressão, backup, monitoramento, OSV, gitleaks e imagens travam o merge e o deploy. CodeQL, Sonar e Trivy da imagem web só informam por enquanto. Push novo num PR cancela o CI anterior.
- Alternativas: o Prettier só avisa nos arquivos do PR, porque formatar tudo de uma vez embolaria os PRs abertos.
- Consequências: renomeou um job? Atualize os rulesets, senão o PR espera um check que nunca chega.
- Onde ver: [branches, "Ligar as regras no GitHub"](branches.md#ligar-as-regras-no-github-uma-vez-por-quem-é-admin), [PR #58](https://github.com/Piggubr/cofrinho-app/pull/58)

## D-064: SonarQube Cloud grátis, analisando a `develop`
- Data: 23/10/2026 ([PR #66](https://github.com/Piggubr/cofrinho-app/pull/66))
- Situação: Valendo
- Contexto: faltava uma visão de bugs, duplicação e cobertura comentada nos PRs.
- Decisão: plano Free do SonarQube Cloud, até 50 mil linhas (42,6 mil em 23/10/2026). Ele só analisa a branch principal do projeto no Sonar, que é a `develop`, e os PRs para ela. O backend vai num relatório só, montado pelo `piggu-cobertura`.
- Alternativas: o SonarQube Community Build, que pede uns 2 GB de RAM e um Postgres no servidor, fica exposto na internet e não comenta nos PRs.
- Consequências: passando de 50 mil linhas, a análise para.
- Onde ver: [qualidade-sonar](qualidade-sonar.md)

## D-065: Cobertura com piso que só sobe
- Data: 01/10/2026
- Situação: Valendo
- Contexto: evitar que a cobertura caia sem ninguém ver.
- Decisão: o piso fica no `angular.json` (`coverageThresholds`) e nas propriedades `cobertura.linhas` e `cobertura.ramos` do `pom.xml` de cada módulo. Teste novo, piso novo.
- Alternativas: não registradas.
- Consequências: nunca baixe um piso para o CI passar.
- Onde ver: [README.md, "Testes"](../README.md#testes)

## D-066: Testes com Postgres de verdade
- Data: 14/09/2026
- Situação: Valendo
- Contexto: as migrations usam `jsonb`, arrays de texto, índice GIN e `gen_random_uuid`.
- Decisão: os testes sobem um PostgreSQL de verdade com Testcontainers e aplicam as migrations reais. Um container compartilhado por módulo.
- Alternativas: H2, que fingiria ser Postgres e esconderia os erros que queremos pegar.
- Consequências: rodar os testes do backend exige Docker. O POM fixa a versão da API do Docker em 1.43.
- Onde ver: [Testes (backend)](../backend/README.md#testes)

## D-067: Bug corrigido vira teste de regressão com data
- Data: 20/10/2026 ([PR #44](https://github.com/Piggubr/cofrinho-app/pull/44))
- Situação: Valendo
- Contexto: alguns bugs só aparecem com a stack inteira de pé (o 401 no login de 14/10, o cookie do refresh, CORS).
- Decisão: `scripts/smoke.sh` sobe a stack como vai para o servidor e roda o smoke e a regressão. Corrigiu um bug que a stack pega? Um teste lá, com a data e o que quebrou.
- Alternativas: não registradas.
- Consequências: o CI roda os dois a cada PR.
- Onde ver: [README.md, "Testes"](../README.md#testes)

## D-068: Toda decisão nova vira um D-0xx no mesmo PR
- Data: 25/10/2026
- Situação: Valendo
- Contexto: a memória do projeto no Claude está desligada, então o repositório é o único lugar que toda sessão nova lê.
- Decisão: este arquivo é o registro das decisões. Quem decidir algo novo acrescenta um D-0xx no mesmo PR, e o `CLAUDE.md` da raiz manda ler este arquivo antes de mudar algo.
- Alternativas: decisões espalhadas em PRs e documentos fora do repositório.
- Consequências: decisão antiga nunca é apagada; se mudar, vira "Substituída por".
- Onde ver: [CLAUDE.md](../CLAUDE.md)

---

# Perguntas para o Dudu

Decisões que parecem tomadas, mas sem o porquê escrito em lugar nenhum. Quando a resposta
vier, ela entra como Contexto ou Alternativas da decisão citada (ou vira uma decisão nova).

1. **O que precisa acontecer para religar o Open Finance e o leitor próprio de nota?** Por
   exemplo, um número de assinantes Premium ou uma receita mínima. (D-045, D-047)

As oito perguntas de 25/10/2026 foram respondidas em 26/10/2026 e estão em D-001, D-006,
D-008, D-045, D-047, D-049, D-069, D-070 e D-071.
