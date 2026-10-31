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
| [D-142](#d-142-revogar-o-entrar-com-apple-ao-excluir-a-conta) | Revogar o Entrar com Apple ao excluir a conta | Valendo |
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
| [D-083](#d-083-termos-e-aviso-do-app-nativo-copiados-do-site-pelo-ci) | Termos e Aviso do app nativo copiados do site pelo CI | Valendo |
| [D-084](#d-084-google-no-app-nativo-com-o-id-do-site-no-aud) | Google no app nativo com o ID do site no `aud` | Valendo |
| [D-089](#d-089-app-ios-nativo-junta-na-featureios-nativo-antes-da-develop) | App iOS nativo junta na `feature/ios-nativo` antes da `develop` | Valendo |
| [D-090](#d-090-etapa-4-do-ios-o-que-ainda-não-é-nativo-abre-no-site) | Etapa 4 do iOS: o que ainda não é nativo abre no site | Valendo |
| [D-091](#d-091-conectar-banco-no-app-nativo-pela-página-do-site-num-aswebauthenticationsession) | Conectar banco no app nativo pela página do site, num ASWebAuthenticationSession | Valendo |
| [D-092](#d-092-etapa-6-do-ios-mais-nos-grupos-do-site-módulos-no-perfil-e-ícone-escolhido-à-parte-da-cor) | Etapa 6 do iOS: Mais nos grupos do site, módulos no Perfil e ícone escolhido à parte da cor | Valendo |
| [D-093](#d-093-etapa-7-do-ios-widgets-pelo-app-group-siri-com-a-sessão-do-app-e-push-que-abre-o-painel) | Etapa 7 do iOS: widgets pelo App Group, Siri com a sessão do app e push que abre o Painel | Valendo |
| [D-094](#d-094-premium-no-iphone-pela-compra-da-apple-conferida-no-backend-com-a-biblioteca-da-apple) | Premium no iPhone pela compra da Apple, conferida no backend com a biblioteca da Apple | Valendo |
| [D-095](#d-095-etapa-9-do-ios-abrir-com-o-perfil-guardado-e-empilhar-no-texto-grande) | Etapa 9 do iOS: abrir com o perfil guardado e empilhar no texto grande | Valendo |
| [D-096](#d-096-ci-do-ios-nativo-no-xcode-27) | CI do iOS nativo no Xcode 27 | Valendo |
| [D-097](#d-097-topo-das-abas-com-a-marca-a-busca-e-o-assistente-trava-em-janela-própria) | Topo das abas com a marca, a busca e o assistente; trava em janela própria | Valendo |
| [D-098](#d-098-o--do-ios-nativo-abre-as-opções-como-no-site) | O + do iOS nativo abre as opções, como no site | Valendo |
| [D-099](#d-099-mês-que-acompanha-o-puxar-barras-por-categoria-num-desenho-só-e-vazios-dos-cartões-no-centro) | Mês que acompanha o puxar, barras por categoria num desenho só e vazios dos cartões no centro | Valendo |
| [D-140](#d-140-importar-extrato-no-app-ios-sem-passar-pelo-site) | Importar extrato no app iOS, sem passar pelo site | Valendo |
| [D-141](#d-141-iphone-deitado-aba-e-pilhas-por-janela-iphone-duo) | iPhone deitado, aba e pilhas por janela (iPhone Duo) | Valendo |
| [D-136](#d-136-android-nativo-compila-com-o-sdk-37-e-mira-o-36) | Android nativo compila com o SDK 37 e mira o 36 | Valendo |
| [D-138](#d-138-etapa-a2-do-android-o-que-ainda-não-é-nativo-abre-no-site-num-custom-tab) | Etapa A2 do Android: o que ainda não é nativo abre no site num Custom Tab | Valendo |
| [D-100](#d-100-sessão-do-android-refresh-cifrado-pelo-keystore-e-uma-renovação-por-vez) | Sessão do Android: refresh cifrado pelo Keystore e uma renovação por vez | Valendo |
| [D-101](#d-101-contrato-da-api-no-android-datas-em-texto-números-em-bigdecimal-e-enum-desconhecido) | Contrato da API no Android: datas em texto, números em BigDecimal e enum desconhecido | Valendo |
| [D-102](#d-102-visual-do-android-13-cores-cor-no-aparelho-e-material-symbols) | Visual do Android: 13 cores, cor no aparelho e Material Symbols | Valendo |
| [D-103](#d-103-cache-do-android-respostas-da-api-em-json-no-room) | Cache do Android: respostas da API em JSON no Room | Valendo |
| [D-104](#d-104-google-no-android-pelo-credential-manager-com-o-botão-escondido-até-o-id-do-android-existir) | Google no Android pelo Credential Manager, com o botão escondido até o ID do Android existir | Valendo |
| [D-105](#d-105-etapa-a3-do-android-a-entrada-nativa-fora-da-moldura) | Etapa A3 do Android: a entrada nativa, fora da moldura | Valendo |
| [D-106](#d-106-termos-e-aviso-do-android-copiados-do-site-pelo-ci) | Termos e Aviso do Android copiados do site pelo CI | Valendo |
| [D-107](#d-107-formulários-da-entrada-no-android-com-o-erro-no-campo) | Formulários da entrada no Android com o erro no campo | Valendo |
| [D-108](#d-108-fila-de-gastos-do-android-no-room-com-o-workmanager-e-sair-apaga) | Fila de gastos do Android no Room, com o WorkManager, e "Sair" apaga | Substituída em parte por D-123 |
| [D-109](#d-109-painel-do-android-com-gráficos-em-canvas-e-organizar-no-próprio-painel) | Painel do Android com gráficos em Canvas e organizar no próprio Painel | Valendo |
| [D-110](#d-110-gastos-do-android-apagar-com-desfazer-busca-e-filtros-no-aparelho) | Gastos do Android: apagar com Desfazer, busca e filtros no aparelho | Valendo |
| [D-111](#d-111-lançar-no-android-vários-itens-numa-folha-só-e-a-receita-pelo-) | Lançar no Android: vários itens numa folha só, e a receita pelo + | Valendo |
| [D-112](#d-112-busca-e-assistente-do-android-e-as-capturas-com-o-hilt-de-verdade) | Busca e assistente do Android, e as capturas com o Hilt de verdade | Valendo |
| [D-113](#d-113-contas-do-mês-do-android-em-três-grupos-com-a-nova-conta-numa-folha) | Contas do mês do Android em três grupos, com a nova conta numa folha | Valendo |
| [D-114](#d-114-orçamento-do-android-com-um-guardado-só-e-o-alerta-escrito) | Orçamento do Android com um guardado só e o alerta escrito | Valendo |
| [D-115](#d-115-calendário-do-android-lado-a-lado-pela-largura-e-a-grade-com-limite-de-fonte) | Calendário do Android lado a lado pela largura e a grade com limite de fonte | Valendo |
| [D-116](#d-116-relatórios-do-android-com-o-csv-do-mês-e-tabela-também-no-mês) | Relatórios do Android com o CSV do mês e tabela também no mês | Valendo |
| [D-117](#d-117-cartões-e-bancos-do-android-fatura-numa-folha-e-conectar-banco-pelo-site) | Cartões e bancos do Android: fatura numa folha e conectar banco pelo site | Valendo |
| [D-118](#d-118-tour-do-android-em-diálogos-no-primeiro-acesso-e-pelo-perfil) | Tour do Android em diálogos, no primeiro acesso e pelo Perfil | Substituída por D-130 |
| [D-119](#d-119-o-ícone-do-app-troca-quando-a-pessoa-sai-do-app) | O ícone do app troca quando a pessoa sai do app | Valendo |
| [D-120](#d-120-metas-prêmios-e-girls-math-do-android-criar-e-editar-numa-folha) | Metas, Prêmios e Girls Math do Android: criar e editar numa folha | Valendo |
| [D-121](#d-121-premium-do-android-sem-a-stripe-em-breve-até-a-a8) | Premium do Android sem a Stripe: "Em breve" até a A8 | Substituída por D-133 |
| [D-122](#d-122-perfil-do-android-com-o-que-estava-no-mais) | Perfil do Android com o que estava no Mais | Valendo |
| [D-123](#d-123-sair-do-android-guarda-a-fila-como-no-ios) | "Sair" do Android guarda a fila, como no iOS | Valendo |
| [D-124](#d-124-compras-do-android-com-leitor-de-código-de-barras) | Compras do Android com leitor de código de barras | Valendo |
| [D-125](#d-125-lugares-e-filmes-do-android-mapa-pelo-app-de-mapas-formulário-numa-folha) | Lugares e Filmes do Android: mapa pelo app de mapas, formulário numa folha | Valendo |
| [D-126](#d-126-fotos-do-android-foto-reduzida-e-legenda-que-se-salva-sozinha) | Fotos do Android: foto reduzida e legenda que se salva sozinha | Valendo |
| [D-127](#d-127-imagens-do-android-pelo-coil-com-o-token-só-para-a-api-do-piggu) | Imagens do Android pelo Coil, com o token só para a API do Piggu | Valendo |
| [D-128](#d-128-foto-do-recibo-no-android-os-itens-lidos-preenchem-o-formulário) | Foto do recibo no Android: os itens lidos preenchem o formulário | Valendo |
| [D-129](#d-129-widgets-atalhos-e-bloco-das-configurações-rápidas-do-android) | Widgets, atalhos e bloco das Configurações rápidas do Android | Valendo |
| [D-130](#d-130-tour-do-android-em-balões-que-saem-da-moldura) | Tour do Android em balões que saem da moldura | Valendo |
| [D-131](#d-131-trava-do-android-com-1-minuto-fora-e-esconder-nos-recentes) | Trava do Android com 1 minuto fora e esconder nos recentes | Valendo |
| [D-132](#d-132-premium-no-android-pela-compra-do-google-play-conferida-no-backend-com-a-play-developer-api) | Premium no Android pela compra do Google Play, conferida no backend com a Play Developer API | Valendo |
| [D-133](#d-133-premium-do-android-pelo-google-play-billing-e-o-stripe-fora-do-app) | Premium do Android pelo Google Play Billing, e o Stripe fora do app | Valendo |
| [D-134](#d-134-a-cor-do-texto-do-tema-no-android-passa-no-wcag-aa) | A cor do texto do tema no Android passa no WCAG AA | Valendo |
| [D-135](#d-135-release-do-android-chave-de-upload-por-variável-aab-no-ci-e-abertura-medida-no-painel) | Release do Android: chave de upload por variável, AAB no CI e abertura medida no Painel | Valendo |
| [D-139](#d-139-push-no-android-pela-api-http-v1-do-fcm-sem-o-sdk-do-firebase) | Push no Android pela API HTTP v1 do FCM, sem o SDK do Firebase | Valendo |
| **Processo** | | |
| [D-061](#d-061-develop-junta-main-é-produção) | `develop` junta, `main` é produção | Valendo |
| [D-062](#d-062-dependabot-abre-pr-na-develop) | Dependabot abre PR na `develop` | Valendo |
| [D-063](#d-063-ci-completo-em-todo-pr) | CI completo em todo PR | Valendo |
| [D-064](#d-064-sonarqube-cloud-grátis-analisando-a-develop) | SonarQube Cloud grátis, analisando a `develop` | Valendo |
| [D-065](#d-065-cobertura-com-piso-que-só-sobe) | Cobertura com piso que só sobe | Valendo |
| [D-066](#d-066-testes-com-postgres-de-verdade) | Testes com Postgres de verdade | Valendo |
| [D-067](#d-067-bug-corrigido-vira-teste-de-regressão-com-data) | Bug corrigido vira teste de regressão com data | Valendo |
| [D-068](#d-068-toda-decisão-nova-vira-um-d-0xx-no-mesmo-pr) | Toda decisão nova vira um D-0xx no mesmo PR | Valendo |
| [D-137](#d-137-etapas-do-android-juntam-em-featureandroid-nativo) | Etapas do Android juntam em `feature/android-nativo` | Valendo |

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
- Consequências: os tokens internos (exclusão da conta, mudança de família, lembrete diário) não levam a claim, para funcionarem com o cadastro pela metade. O iOS nativo pede CPF e celular na `SobreVoceView` desde a etapa 9. O celular ainda não é confirmado por SMS.
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
- Consequências: quem escolhe "Ocultar meu e-mail" na Apple recebe um e-mail de retransmissão (`privaterelay.appleid.com`) e ganha uma conta separada da que tem pelo Google. A revogação do token na Apple ao apagar a conta veio na D-142, que a App Store exige: precisa da chave do Sign in with Apple e entra antes da revisão.
- Onde ver: `backend/piggu-identity/.../api/AuthController.java`, `.../apple/AppleIdTokenVerifier.java`, [API_SPEC, "Entrar e manter a sessão"](API_SPEC.md#entrar-e-manter-a-sessão)

## D-083: Termos e Aviso do app nativo copiados do site pelo CI
- Data: 26/10/2026
- Situação: Valendo
- Contexto: etapa 3 do plano iOS. O app nativo mostra os Termos de Uso e o Aviso de Privacidade em telas próprias, com o mesmo texto do site, e o aceite grava a versão (`VERSAO_DO_AVISO`). Duas cópias escritas à mão acabariam diferentes.
- Decisão: o texto do site (`features/legal/*.html` e a versão de `aviso.ts`) é a fonte. `ios-nativo/scripts/textos-legais.py` gera `ios-nativo/Piggu/Recursos/Termos.md` e `Privacidade.md`, e o CI do iOS falha se eles ficarem diferentes do HTML. A versão no Swift (`Conta.versaoDosTermos`) é conferida contra o texto nos testes.
- Alternativas: abrir o site numa web view, que não abre sem internet e fica fora do visual do app, ou guardar os textos no String Catalog, onde cada parágrafo vira uma chave solta.
- Consequências: mudou o texto no site, roda o script e faz o commit no mesmo PR. O Android vai precisar do mesmo script.
- Onde ver: `ios-nativo/scripts/textos-legais.py`, `ios-nativo/Piggu/Telas/Entrada/TextoLegalView.swift`, `.github/workflows/ios-nativo.yml`

## D-084: Google no app nativo com o ID do site no `aud`
- Data: 26/10/2026
- Situação: Valendo
- Contexto: etapa 3 do plano iOS. O login do Google no app usa um ID OAuth do tipo iOS, e o backend só aceita ID tokens cuja audiência esteja em `GOOGLE_CLIENT_ID`. Pôr os dois IDs nessa variável, separados por vírgula, não funciona: o `application.yml` a lê como um item só da lista, e o login do site pararia.
- Decisão: o app abre o login com o ID do iOS e pede o token para o ID do site (`GIDServerClientID`), então o `aud` é o que o backend já aceita e o servidor não muda. Os dois IDs ficam em `ios-nativo/Config/Piggu.xcconfig`, no Git: são públicos e vão dentro do app. O site publica o `apple-app-site-association` com o Team `H766LA27ZJ` para os links de confirmar e-mail e de redefinir senha abrirem o app.
- Alternativas: aceitar uma lista de IDs no backend, que mexe no servidor sem necessidade; guardar o ID do iOS fora do Git, que só escondia o botão nos builds sem o arquivo.
- Consequências: trocar o ID do site exige trocar também o `GOOGLE_SERVER_CLIENT_ID` do app. O ID do iOS precisa estar no mesmo projeto do Google Cloud que o do site.
- Onde ver: `ios-nativo/Config/Piggu.xcconfig`, `ios-nativo/Piggu/Core/Auth/EntradaComGoogle.swift`, `frontend/public/.well-known/apple-app-site-association`, `deploy/Caddyfile`

## D-090: Etapa 4 do iOS: o que ainda não é nativo abre no site
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 4 do plano iOS (Painel, Gastos e Receitas com paridade com o site). Os cartões do Painel e os formulários levam a telas que só chegam ao app nas etapas 5 a 7 (orçamentos, contas do mês, cartões e bancos, metas, Girls Math, calendário, Premium), e duas funções dependem de algo que o app ainda não tem: a leitura de nota por foto (etapa 7) e o widget da Pluggy para conectar banco.
- Decisão: esses atalhos abrem a página do `piggu.com.br` no navegador, com um ícone de "abre fora"; o resto da tela é nativo. A importação de extrato (nativa desde a D-140) e a leitura de nota ficam fora do app por enquanto. Os gráficos do Painel usam o Swift Charts (SDK da Apple). A ordem do Painel fica no aparelho na mesma chave e no mesmo JSON do site (`piggu_painel_<id>`); no iOS 27 os cartões se arrastam no próprio Painel (`reorderable`), antes pela tela de personalizar (`List` com `onMove`). Gasto lançado sem internet vai para uma fila no SwiftData, com o mesmo `idDoEnvio` no reenvio, e a fila não é apagada ao sair da conta: só a pessoa que lançou envia, quando entrar de novo.
- Alternativas: esconder os atalhos até a etapa certa, que deixaria cartões sem saída; abrir o site numa web view, que pede login de novo dentro dela.
- Consequências: cada etapa seguinte troca o link do site pela tela nativa. Os links ficam em `LinkDoSite`, fáceis de achar.
- Onde ver: `ios-nativo/Piggu/Telas/Componentes/Componentes.swift` (`LinkDoSite`), `ios-nativo/Piggu/Core/Painel/PainelOrganizado.swift`, `ios-nativo/Piggu/Core/Offline/FilaDeGastos.swift`

## D-091: Conectar banco no app nativo pela página do site, num ASWebAuthenticationSession
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 5 do plano iOS. O site conecta bancos pelo widget da Pluggy, que só existe em JavaScript e avisa o banco conectado por callback, sem redirecionar. O app nativo não pode abrir um WebView para isso (a pessoa digita a senha do banco), e a Pluggy não tem uma página hospedada que devolva o item por redirecionamento (o `oauthRedirectUri` dela só cobre a volta do OAuth do banco dentro do widget).
- Decisão: o site ganha a página pública `/conectar-banco`. O app pede o token em `POST /api/banking/connect-token` (com a autorização, como o site), abre `https://piggu.com.br/conectar-banco#token=...` num `ASWebAuthenticationSession` efêmero e espera `piggu://banco-conectado?item=...` (ou `?fechou=1`, `?erro=...`). A página não tem sessão nem chama a API: lê o token do fragmento (que não vai ao servidor nem aos logs), tira ele do histórico, abre o widget com o mesmo `PluggyConnectService` do site e redireciona com o resultado. O app registra o item em `POST /api/banking/items`.
- Alternativas: o SDK nativo da Pluggy, que é uma biblioteca nova (o app só usa os SDKs da Apple e o Google Sign-In, D-055) e por dentro é um WebView; um WebView próprio, proibido pelo plano.
- Consequências: o backend não muda e o CSP do site já permite o script e os frames da Pluggy. Bancos que fazem OAuth dentro do widget abrem a página do banco a partir do navegador do sistema; vale testar com um banco de verdade antes do TestFlight.
- Onde ver: `frontend/src/app/features/conectar-banco/`, `frontend/src/app/core/banking/pluggy-connect.service.ts`, `ios-nativo/Piggu/Core/Bancos/OpenFinance.swift`

## D-092: Etapa 6 do iOS: Mais nos grupos do site, módulos no Perfil e ícone escolhido à parte da cor
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 6 do plano iOS (metas, Girls Math, Planejar, Prêmios, Família e Perfil). O site não tem tela para ligar e desligar os módulos de estilo de vida (só a rota `PUT /auth/me/modules`), troca o ícone do app num seletor próprio, separado da cor, e Compras, Lugares, Filmes e Fotos ainda não estão em nenhuma etapa do plano.
- Decisão: o Mais segue os grupos e a ordem do menu do site (Planejar, Contas, Estilo de vida, Conta), com o Perfil no topo e o Premium só para o titular. O Perfil ganha "Telas do menu", com os seis módulos, e o Mais esconde o que estiver desligado (ausente liga todos, como no site). O ícone do app tem o seu seletor, como no site, com os mesmos 12 ícones do app Capacitor (`AppIcon-<id>` e `setAlternateIconName`); ele não muda sozinho com a cor, porque o iOS mostra um aviso a cada troca. Compras, Lugares, Filmes e Fotos abrem no site (D-090). Quem é membro vê as mesmas abas e telas, sem o Premium, e na Família a lista com "Sair da família", como no site.
- Alternativas: trocar o ícone junto com a cor, que mostraria o aviso do sistema a cada troca de cor e não cobre os ícones que não têm cor (Clássico, P de Piggu, Silhueta, Namorados).
- Consequências: o site pode ganhar a mesma tela de módulos depois; o app já usa o campo `preferencias.modulos`.
- Onde ver: `ios-nativo/Piggu/Telas/MaisView.swift`, `ios-nativo/Piggu/Telas/Conta/PerfilView.swift`, `ios-nativo/Piggu/Core/Design/IconeDoApp.swift`

## D-093: Etapa 7 do iOS: widgets pelo App Group, Siri com a sessão do app e push que abre o Painel
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 7 do plano iOS (Compras, Lugares, Filmes e Fotos, widgets, Siri, Face ID, notificações, TipKit, leitura da nota e Spotlight). Os widgets rodam fora do app, sem sessão; o push do backend é o resumo do dia (fatura, orçamento, contas) e não diz a tela; o tour do site tem uma dica do assistente, que o app ainda não tem.
- Decisão: um alvo novo, `PigguWidgetsExtension`, lê só um resumo do mês (gasto, limite, a próxima meta, a cor) que o app grava no App Group `group.br.com.piggu.app` ao abrir, ao voltar e depois de lançar, e recarrega com `WidgetCenter.reloadAllTimelines()`; nada de token, e-mail ou lista de gastos, e o resumo some ao sair da conta. O "Lançar gasto" (Siri, Atalhos e o controle da Central de Controle) é o `LancarGastoIntent` do app Capacitor, compartilhado com o widget, e abre a folha nativa. "Quanto gastei este mês" responde em voz sem abrir o app, com a sessão do próprio app (o refresh no cookie ou no Keychain) e, sem rede, com o último resumo e a hora dele. Tocar numa notificação abre o Painel ("Para ficar de olho"), ou a tela de uma chave `rota` se o backend mandar no futuro. A permissão de notificação é pedida ao ligar "Notificação neste iPhone" no Perfil, como no site. O tour vira dicas do TipKit perto do que explicam, sem a do assistente. A leitura da nota usa o `DataScannerViewController` e só preenche total e data quando acha uma linha de total; senão a pessoa digita. Os gastos do mês vão para o Spotlight e saem ao sair da conta.
- Alternativas: o widget chamar a API com um token guardado no App Group, que deixaria uma credencial fora do Keychain e de qualquer controle de sessão; pedir a permissão de notificação ao abrir o app, que o site não faz e que a Apple desaconselha.
- Consequências: para o TestFlight, o App Group, o alvo do widget (`br.com.piggu.app.PigguWidgets`) e o Push Notifications precisam estar no App ID; a assinatura automática do Xcode registra. O backend pode mandar `rota` no push quando quiser abrir uma tela específica.
- Onde ver: `ios-nativo/PigguWidgets/`, `ios-nativo/Compartilhado/`, `ios-nativo/Piggu/Core/Sistema/`

## D-094: Premium no iPhone pela compra da Apple, conferida no backend com a biblioteca da Apple
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 8 do plano iOS. No site o Premium é vendido pela Stripe; no app, assinatura digital tem que ser pela compra da Apple. O backend já recebia compras das lojas pelo RevenueCat (`/api/billing/stores/{loja}/purchases`), que nunca chegou a ser configurado. No Brasil, desde 18/06/2026 (iOS 26.5), a Apple deixa pôr um link para pagar fora do app, mas com o entitlement de compra externa, a folha de aviso, a compra da Apple sempre ao lado e comissão de 15% (10% depois do primeiro ano da assinatura) sobre o que for vendido até 7 dias depois do toque.
- Decisão: o app usa o StoreKit 2 (`SubscriptionStoreView`) com dois produtos individuais, `br.com.piggu.premium.mensal` e `br.com.piggu.premium.anual`, no grupo "Piggu Premium". A compra leva o id da conta Piggu como `appAccountToken`, e o app manda a transação assinada a `POST /api/billing/app-store/transactions` antes de finalizá-la. As App Store Server Notifications V2 chegam em `POST /api/billing/webhooks/app-store`. Os dois conferem o JWS com a `app-store-server-library` oficial (5.2.0, sem o cliente HTTP) contra a Apple Root CA - G3 e gravam no mesmo plano da casa que a Stripe atualiza, com origem `APP_STORE`. A compra só vale para a conta do `appAccountToken` (409 `OUTRA_CONTA`). Premium vigente de outra origem (o site) não é trocado nem derrubado por aviso da Apple. O sandbox é aceito também em produção, porque a revisão da Apple compra no sandbox; o ambiente do Xcode, só fora da produção. Quem já é Premium, pelo site ou por outra pessoa da casa, vê o plano e não vê a loja. Os links do app para `/plano` no site viraram a tela nativa. O app não oferece o Stripe por enquanto.
- Alternativas: o RevenueCat (mais um operador de dados e uma conta paga para o que a biblioteca da Apple faz); conferir só no aparelho (qualquer um forjaria o Premium no backend); oferecer o Stripe no iPhone pelo link externo do Brasil (dá para fazer, mas exige o entitlement, a compra da Apple igualmente visível, relatório mensal pela External Purchase Server API e comissão de 15%, então o ganho sobre os 26% da compra da Apple é pequeno perto do trabalho; fica para depois).
- Consequências: no App Store Connect falta criar o grupo, os produtos e os preços, e apontar as notificações V2 para a URL acima (produção e sandbox). O servidor precisa de `APP_STORE_APP_APPLE_ID` para aceitar compras de produção. Casal e família continuam só no site. O RevenueCat segue para o Android.
- Onde ver: `backend/piggu-identity/src/main/java/com/piggu/identity/billing/AppStoreLoja.java`, `ios-nativo/Piggu/Core/Loja/LojaDaApple.swift`, `ios-nativo/Piggu/Telas/Conta/PremiumView.swift`, `ios-nativo/PigguTests/Piggu.storekit`

## D-136: Android nativo compila com o SDK 37 e mira o 36
- Data: 27/10/2026
- Situação: Valendo
- Contexto: o plano da etapa A0 pedia compileSdk 36 e as bibliotecas estáveis mais novas. O Compose BOM 2026.09.00 (Material 3 1.4) e o Navigation 3 1.2.0 só aceitam compilar com o SDK 37 ou mais novo.
- Decisão: `compileSdk = 37` e `targetSdk = 36`. O compileSdk só muda as APIs que o código enxerga ao compilar; o comportamento no aparelho segue o targetSdk. As cores do tema saem do Material Color Utilities (esquema Vibrant) a partir da `--primaria` de cada tema do site, com a primária e o texto sobre ela iguais aos do site (`core/designsystem/gerar-cores.mjs`).
- Alternativas: voltar para bibliotecas mais velhas que ainda compilam com o 36, ficando sem o Material 3 1.4 e o Navigation 3 estável mais novo.
- Consequências: o build precisa da plataforma `android-37.0` do SDK, que o Gradle baixa sozinho. Subir o targetSdk para 37 é uma decisão à parte, com teste das mudanças de comportamento.
- Onde ver: `android-nativo/app/build.gradle.kts`, `android-nativo/gradle/libs.versions.toml`

## D-138: Etapa A2 do Android: o que ainda não é nativo abre no site num Custom Tab
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A2 do plano Android (fundação e moldura). As telas chegam nas etapas A3 a A7, mas o menu, os links e o + já precisam levar a algum lugar. O iOS resolveu o mesmo com o navegador (D-090 de lá).
- Decisão: cada rota de `app.routes.ts` vira uma rota tipada (`Tela`, Navigation 3) com a flag `nativa`. A que ainda não é nativa abre a página do `piggu.com.br` num Custom Tab, com o ícone de "abre fora"; as quatro abas mostram o aviso com "Abrir no site", e o + abre a folha com Lançar gasto (`/gastos?rapido=1&valor=`) e Lançar receita no site. A busca e o assistente ficam no topo e avisam que chegam na A4. Os App Links de `https://piggu.com.br` cobrem só as telas nativas (hoje as quatro abas): uma tela que abre no site num Custom Tab voltaria para o app. O Mais segue os grupos do site com o Perfil no topo; no tablet, o trilho (largura média) e a gaveta fixa com os grupos (tela larga). Os módulos de estilo de vida seguem a D-092 do iOS: ausente liga todos, e a lista (até vazia) esconde o que não está nela; o `shell.ts` do site ainda mostra todos.
- Alternativas: esconder o que não é nativo, que deixaria o menu pela metade; WebView, que pede login de novo e fica fora do visual.
- Consequências: a página do site no Custom Tab pede login (o app não passa a sessão). Cada etapa troca o link pela tela, acrescenta o caminho no `intent-filter` dos App Links e muda `nativa`. O `assetlinks.json` precisa estar publicado no site para os App Links abrirem o app.
- Onde ver: `android-nativo/app/src/main/kotlin/br/com/piggu/app/navegacao/`, `android-nativo/app/src/main/kotlin/br/com/piggu/app/moldura/`, `android-nativo/core/ui/.../Site.kt`

## D-100: Sessão do Android: refresh cifrado pelo Keystore e uma renovação por vez
- Data: 28/10/2026
- Situação: Valendo
- Contexto: o app Android entra com `refreshNoCorpo: true` (D-072) e precisa guardar o refresh sem o Keychain do iOS. O backend troca o refresh a cada uso, então duas renovações ao mesmo tempo derrubam a sessão.
- Decisão: o access token fica só na memória. Com "Continuar conectado", o refresh vai para o DataStore cifrado com AES-GCM numa chave do Android Keystore, junto com o perfil; sem, fica só na memória. Refresh que não decifra vale como sessão nenhuma. O `Authenticator` do OkHttp renova no 401 uma única vez por chamada, com um `Mutex`: quem chega durante a renovação espera e usa o token novo. A renovação é uma chamada síncrona na thread de quem esperava, fora da fila do OkHttp, que tem 5 chamadas por servidor e ficaria cheia das que esperam por ela. Só a renovação recusada com 401 ou 403 encerra a sessão (D-075); com o perfil guardado, o app abre logado na hora e renova por trás (D-095 do iOS). Sair manda o refresh para `/api/auth/logout` e apaga o cofre e o cache.
- Alternativas: EncryptedSharedPreferences, que o Google descontinuou; renovar pela chamada `suspend` do Retrofit, que travava com 5 chamadas simultâneas (teste em `SessaoTest`).
- Consequências: a fila de gastos, o resumo dos widgets e a conversa do assistente ainda não existem; quem os trouxer apaga ao sair. A chave do Keystore não vai para o backup (o backup do app está desligado).
- Onde ver: `android-nativo/core/data/.../Sessao.kt`, `AutenticacaoDaSessao.kt`, `android-nativo/core/datastore/`

## D-101: Contrato da API no Android: datas em texto, números em BigDecimal e enum desconhecido
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A2. O app copia `models.ts` e os `docs/api/*.yaml` com kotlinx.serialization e precisa sobreviver a campos e valores novos da API (D-059).
- Decisão: datas ficam em `String`, como no site e no iOS. Todo `number` do contrato vira `BigDecimal` (também registrado como serializador contextual, para respostas que são só um mapa de números), e todo enum tem `DESCONHECIDO`, também dentro de listas. Os serviços lançam só `ErroDaApi` (sem internet, resposta fora do contrato ou o erro do servidor), e as mensagens seguem `mensagem-de-erro.ts`. O app manda `User-Agent: Piggu-Android/<versão>`, e um 426 com `ATUALIZE_O_APP` abre a tela de atualizar com a ficha do Play, antes do backend mandar esse código. A variante demo põe a API falsa (`ApiDaDemo`, porte de `api-da-demo.ts`) como o último interceptador do OkHttp.
- Alternativas: `java.time` nos modelos, que quebraria com uma variação do formato que o servidor mande; `Double` para dinheiro.
- Consequências: o `ContratoTest` confere os nomes dos campos contra os yaml, e o `DemoContraModelosTest` lê toda resposta da demo com os modelos. Uma tela que precise da data a converte onde usa.
- Onde ver: `android-nativo/core/model/`, `android-nativo/core/network/`, `android-nativo/core/testing/`

## D-102: Visual do Android: 13 cores, cor no aparelho e Material Symbols
- Data: 28/10/2026
- Situação: Valendo
- Contexto: o site tem 13 cores (4 grátis e 9 do Premium), guardadas no aparelho, e a A0 só tinha quatro.
- Decisão: as 13 saem do `gerar-cores.mjs` com o esquema Vibrant, menos o Grafite, que usa o Neutral (no Vibrant as superfícies ficavam azuis). A cor, o modo (Automático, Claro e Escuro) e a marca de "voltou ao padrão" ficam no DataStore com as chaves do `localStorage` do site; quando o Premium acaba, a cor Premium volta ao Chiclete e o app avisa uma vez. "Cores do celular" (Android 12 ou mais novo) fica só no Android. Os ícones de `icone.ts` são do Material Symbols Outlined, gerados por `gerar-icones.mjs`. O `lerValor` devolve duas casas, e data impossível (30/02) é inválida.
- Alternativas: o Vibrant também no Grafite; desenhar os ícones do Lucide do site, que não têm equivalente oficial no Android.
- Consequências: a escolha da cor e do modo no Perfil chega com o Perfil nativo.
- Onde ver: `android-nativo/core/designsystem/`, `android-nativo/core/ui/.../formatos/`, `android-nativo/core/datastore/.../PreferenciasDoAparelho.kt`

## D-103: Cache do Android: respostas da API em JSON no Room
- Data: 28/10/2026
- Situação: Valendo
- Contexto: o app tem que abrir sem internet com o resumo e os gastos do mês e lançar com as listas guardadas (`listas-guardadas.ts`).
- Decisão: como o iOS (`RespostaGuardada`), o Room guarda a resposta como veio, em JSON, por chave (`resumo/aaaa-mm`, `gastos/aaaa-mm`, `categorias`, `contas`, `pessoas-da-familia`), com a hora. Os repositórios mostram o guardado, buscam na rede e trocam; sem rede, fica o guardado com a hora. Versão nova do banco começa vazia em vez de migrar. O cache sai ao entrar com outra conta e ao sair.
- Alternativas: uma tabela por tipo, que só vale quando alguma tela precisar consultar o cache (busca sem internet, por exemplo).
- Consequências: nenhuma migração de banco por enquanto.
- Onde ver: `android-nativo/core/database/`, `android-nativo/core/data/.../Carga.kt`, `Repositorios.kt`

## D-104: Google no Android pelo Credential Manager, com o botão escondido até o ID do Android existir
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A3 do plano Android. O Google no Android só autoriza um app com um ID OAuth do tipo Android (pacote e SHA-1 da chave) no mesmo projeto do Google Cloud do ID do site, e esse ID ainda não foi criado. Sem ele, o login falha com `DEVELOPER_ERROR`.
- Decisão: o app usa o Sign in with Google do Credential Manager, com `serverClientId` igual ao ID do site, então o `aud` do ID token é o que o backend já confere (D-084) e o servidor não muda. O botão só aparece com `piggu.googleNoAndroid=true` (Gradle); até lá, só e-mail e senha. Na primeira vez, o TERMOS_NECESSARIOS pede o aceite na própria tela, como o site. Depois de entrar com a senha, o app oferece guardá-la no gerenciador de senhas do aparelho; ao sair, esquece a conta do Google escolhida.
- Alternativas: o Google pelo navegador, como o app Capacitor (#79), que pede o vai e volta pelo `piggu://`; mostrar o botão e deixar falhar.
- Consequências: com o ID OAuth do Android criado (os passos estão no PR #107), basta ligar a propriedade e gerar o app de novo. `GOOGLE_CLIENT_ID_ANDROID` no servidor continua opcional.
- Onde ver: `android-nativo/feature/login/.../Credenciais.kt`, `LoginViewModel.kt`, `android-nativo/feature/login/build.gradle.kts`

## D-105: Etapa A3 do Android: a entrada nativa, fora da moldura
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A3 (login, criar conta, aceite, Sobre você, CPF, links do e-mail, termos e excluir a conta). O Perfil, onde o site oferece reenviar a confirmação, informar o CPF e excluir a conta, só fica nativo mais adiante, e o Google Play exige excluir a conta dentro do app.
- Decisão: as telas da entrada são telas cheias, sem a moldura, numa pilha própria: sem sessão (login e as telas abertas), com uma pendência (o aceite ou o "Sobre você", na ordem do `auth.guard.ts`) e por cima das abas (termos, CPF, links do e-mail). Um 403 `CADASTRO_PENDENTE` de qualquer serviço marca a conta como incompleta e leva ao "Sobre você". Os App Links passam a cobrir `/confirmar-email`, `/redefinir-senha`, `/esqueci-senha`, `/termos`, `/privacidade` e `/cpf`. Até o Perfil nativo, o Mais mostra o aviso de confirmar o e-mail com o reenviar, o "Informar agora" do documento e "Excluir minha conta", com o texto do site e a palavra EXCLUIR.
- Alternativas: mandar essas três ações do Mais para o site, que pede login de novo no Custom Tab e não cumpre a regra do Google Play.
- Consequências: o Perfil nativo leva as três ações para ele e o Mais volta a ficar como o site.
- Onde ver: `android-nativo/app/.../Entrada.kt`, `PigguApp.kt`, `android-nativo/core/data/.../AutenticacaoDaSessao.kt`, `android-nativo/feature/login/`

## D-106: Termos e Aviso do Android copiados do site pelo CI
- Data: 28/10/2026
- Situação: Valendo
- Contexto: a D-083 do iOS: o app mostra os Termos e o Aviso sem internet e sem login, com o texto do site.
- Decisão: `android-nativo/scripts/textos-legais.py` gera `Termos.md` e `Privacidade.md` em `feature/legal/src/main/assets/`, no mesmo formato do iOS, e o CI do Android falha se ficarem diferentes do HTML do site. A tela tem um sumário "Nesta página" que leva a cada seção, e as linhas das tabelas viram cartões. O script corrige um `}` que o do iOS deixa num título do Aviso.
- Alternativas: abrir o site, que pede internet.
- Consequências: mudou o texto no site, roda o script e faz o commit no mesmo PR. Vale levar a correção do `}` para o script do iOS.
- Onde ver: `android-nativo/scripts/textos-legais.py`, `android-nativo/feature/legal/`, `.github/workflows/android-nativo.yml`

## D-107: Formulários da entrada no Android com o erro no campo
- Data: 28/10/2026
- Situação: Valendo
- Contexto: no site, criar conta, "Sobre você" e CPF mostram um aviso só no topo.
- Decisão: no app, o primeiro campo com problema ganha o foco, a borda vermelha e a mensagem embaixo; um erro do servidor também vai para o campo (o primeiro de `campos` no `VALIDACAO`, ou pelo código: `IDADE_MINIMA`, `SENHA_FRACA`, `TELEFONE_INVALIDO`, `CPF_NECESSARIO`, `CPF_EM_USO`). Sem campo (sem internet, erro genérico), o aviso fica no topo. As validações e a ordem são as do site; o nascimento sai de um calendário que não deixa escolher o futuro, e a tela do CPF não tem "Sair", como no iOS.
- Alternativas: o aviso único no topo, como o site.
- Consequências: o leitor de tela lê o erro junto do campo certo.
- Onde ver: `android-nativo/feature/login/.../cadastro/`, `sobrevoce/`, `cpf/`, `android-nativo/core/ui/.../formatos/`

## D-108: Fila de gastos do Android no Room, com o WorkManager, e "Sair" apaga
- Data: 28/10/2026
- Situação: Substituída em parte por D-123 (o "Sair" não apaga mais a fila)
- Contexto: etapa A4. Gasto lançado sem internet vai para uma fila (D-073 e D-075 do iOS). No iOS a fila não sai ao sair da conta (D-090 de lá); o Dudu pediu, no plano da A2, que o "Sair" do Android apague a fila.
- Decisão: a fila fica numa tabela do Room (banco na versão 2, com migração), de cada pessoa, e sobe na ordem por um Worker do WorkManager com a rede como restrição, agendado ao guardar, ao abrir o app, ao entrar e quando a rede volta. Cada toque em "Lançar" tem um `idDoEnvio` novo, e só 400 e 422 tiram da fila e contam como recusados. O "Sair" pelo botão (do Mais e do Aceite) apaga a fila da pessoa antes de sair; a sessão que só caiu não apaga. Os gastos da fila aparecem na lista com "aguardando envio" e não entram no total.
- Alternativas: guardar até a pessoa voltar, como o iOS.
- Consequências: quem sai com gastos sem enviar perde esses gastos. Num processo aberto só pelo WorkManager ninguém renova a sessão (duas renovações ao mesmo tempo derrubariam a sessão), então com o app fechado o envio espera a próxima abertura.
- Onde ver: `android-nativo/core/data/.../FilaNoRoom.kt`, `EnvioDaFila.kt`, `android-nativo/core/database/`, `AppViewModel.sair`

## D-109: Painel do Android com gráficos em Canvas e organizar no próprio Painel
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A4. O site organiza os cartões no Perfil, e o Perfil nativo ainda não existe.
- Decisão: as barras e o anel são desenhados em Canvas do Compose, sem biblioteca (Vico seria grande para três desenhos). Os cartões se organizam numa folha dentro do Painel, arrastando ou com "mover para cima/baixo" (TalkBack), e a ordem vai para o DataStore na mesma chave e no mesmo JSON do site (`piggu_painel_<id>`), assim como o "pular" dos primeiros passos (`piggu_guia_<id>`). Para o membro da família ficam escondidos o resumo, o mês, as categorias e os objetivos (o site só diz isso num comentário). Cartão que depende de dados some quando eles faltam; conectar banco abre o site, porque o widget da Pluggy só existe lá. O limite de objetivos do plano grátis chega como 422 `PLANO_PREMIUM` e abre o convite do Premium.
- Alternativas: o Vico; esperar o Perfil nativo para organizar.
- Consequências: quando o Perfil for nativo, ele pode abrir a mesma folha.
- Onde ver: `android-nativo/feature/painel/`, `android-nativo/core/ui/.../Graficos.kt`, `android-nativo/core/datastore/.../PainelDoAparelho.kt`

## D-110: Gastos do Android: apagar com Desfazer, busca e filtros no aparelho
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A4. O site confirma antes de apagar e não tem busca nem filtros na lista; o iOS tem.
- Decisão: deslizar apaga na hora e oferece "Desfazer" na Snackbar; o pedido só vai ao servidor quando ela some. Parcela com próximas pergunta antes ("só esta" ou "esta e as próximas"). A busca (sem acento, por item, estabelecimento e categoria) e os filtros de categoria e de forma de pagamento rodam no aparelho, como no iOS. Editar abre uma folha com item, categoria, valor e compartilhado; o link `piggu://gastos?id=...&mes=...` abre o mês e a edição do gasto. O CSV do mês sai pelo compartilhamento do Android (FileProvider próprio), e o extrato entra pelo seletor de arquivos com a prévia do site.
- Alternativas: confirmar antes, como o site.
- Consequências: "apagar as próximas" vale só para parcelas, como no backend.
- Onde ver: `android-nativo/feature/gastos/.../lista/`

## D-111: Lançar no Android: vários itens numa folha só, e a receita pelo +
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A4. O + abre a folha com Lançar gasto e Lançar receita (D-098 do iOS).
- Decisão: o formulário de gasto tem vários itens (descrição, categoria e valor), com parcelas e outra moeda valendo para todos e um POST só; data e estabelecimento ficam em "Mais opções", para o lançamento rápido. A última conta usada fica no aparelho, como no site. Sem internet, o gasto vai direto para a fila. A receita é lançada só pela folha do + (também pelo botão da aba), e a aba Receitas segue a ordem do iOS: o seletor de mês fixo no topo, a lista, "Entram todo mês" e "A receber". Lançou, a folha fecha, um aviso curto confirma e as abas recarregam pelo `Lancamentos`.
- Alternativas: o formulário dentro de cada aba, como o site no computador.
- Consequências: a foto do recibo chega na A7.
- Onde ver: `android-nativo/feature/gastos/.../formulario/`, `android-nativo/feature/receitas/`, `PigguApp.kt` (`FolhaDeLancar`)

## D-112: Busca e assistente do Android, e as capturas com o Hilt de verdade
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A4 (topo da D-097 do iOS). As telas do dia a dia carregam dados por ViewModels do Hilt, que as capturas do Robolectric não tinham.
- Decisão: a busca abre em tela cheia, com telas, gastos de todos os meses (pelo servidor) e lugares, filmes e compras só com o módulo ligado, filtrados no aparelho. O assistente é uma folha, só para o Premium, com a autorização do Gemini no primeiro uso; a conversa fica no DataStore por 30 dias (sem cifra, como o `localStorage` do site) e sai ao sair da conta. As capturas da A4 sobem o grafo do Hilt de verdade (`app/src/testDemo`), com a API falsa da demonstração sem atraso, no dia 28/10/2026, e a rede marcada como conectada.
- Alternativas: capturas só das telas sem estado, com dados escritos à mão.
- Consequências: as capturas mostram o que a demonstração mostra; mudar os dados da demo muda as imagens.
- Onde ver: `android-nativo/feature/assistente/`, `android-nativo/app/src/testDemo/`

## D-113: Contas do mês do Android em três grupos, com a nova conta numa folha
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A5. O site mostra as contas numa lista só, com etiquetas, e o formulário embaixo.
- Decisão: as contas aparecem em três cartões (Vencidas, A vencer e Pagas), e a nova conta fixa abre numa folha pelo + do topo, como no iOS. "Marcar como paga" lança o gasto do mês (`POST /api/bills/{id}/pay?mes=`). A categoria da conta usa o mesmo campo do lançar, com "+ Nova categoria…". As receitas fixas aparecem como no iOS, com "Parar de repetir".
- Alternativas: a lista única do site.
- Consequências: o "pagar várias de uma vez" do site e do iOS ainda não entrou; o seletor de mês não avança além do mês atual.
- Onde ver: `android-nativo/feature/contasfixas/`

## D-114: Orçamento do Android com um guardado só e o alerta escrito
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A5 (`features/budgets` com `features/goals` no topo).
- Decisão: os limites do mês, o gasto do mês e os limites por categoria são buscados juntos e guardados numa chave só (`orcamento/aaaa-mm`), para a tela abrir inteira sem internet. Os alertas de 80% e 100% aparecem também em texto, não só na cor da barra. Ver e apagar limites é livre; criar limite por categoria sem Premium mostra o convite. O mês do limite vai até 12 meses adiante.
- Alternativas: avisos de erro separados por bloco, como o site.
- Consequências: se uma das três buscas falha e não há nada guardado, a tela mostra o erro inteira.
- Onde ver: `android-nativo/feature/orcamentos/`

## D-115: Calendário do Android lado a lado pela largura e a grade com limite de fonte
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A5. O plano pedia a cena de lista e detalhe do Navigation 3 no tablet.
- Decisão: a partir da largura média, a grade e o gráfico ficam à esquerda e o dia escolhido, a nota nova e as notas sem data à direita, numa `Row` pelo WindowSizeClass, e não numa cena do Navigation 3: o "detalhe" é só o dia, e uma pilha de navegação dentro da tela não traria nada. A grade mostra gasto, receita e lembrete do dia, e o mês avança para o futuro por causa dos lembretes. A fonte da grade para em 1,3x, e o TalkBack lê o dia inteiro (D-095 do iOS); o gráfico tem as ações de dia anterior e próximo e a tabela por semana.
- Alternativas: a cena de lista e detalhe.
- Consequências: a demonstração não liga notas a gastos, então o aviso de "o gasto vai junto" só aparece com dados de verdade.
- Onde ver: `android-nativo/feature/calendario/`

## D-116: Relatórios do Android com o CSV do mês e tabela também no mês
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A5. No site o CSV do mês sai por Gastos; o iOS o põe também em Relatórios.
- Decisão: a aba Relatórios exporta o CSV do mês pela mesma rota (`/expenses/export`) e com o mesmo nome de arquivo do site, compartilhado por `ACTION_SEND` com um FileProvider próprio. O mês por categoria contra o anterior, com projeção, tem "Ver em tabela", como o ano; o ano mês a mês é do Premium, com o convite para os outros. Os números ficam em linhas (rótulo e valor), para aguentar fonte grande.
- Alternativas: o CSV só em Gastos.
- Consequências: as cores de receita (verde do site) e gasto (primária do tema) ficam no módulo até o tema ter cores de série.
- Onde ver: `android-nativo/feature/relatorios/`

## D-117: Cartões e bancos do Android: fatura numa folha e conectar banco pelo site
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A5. A página `/conectar-banco` do site (D-091 do iOS) só existe na `feature/ios-nativo`, não na `develop`, e o Open Finance está desligado (`PIGGU_OPEN_FINANCE`).
- Decisão: a fatura do cartão (entre os fechamentos, porte de `fatura.ts`) abre numa folha, com "Marcar como paga" e "Desfazer" (D-085). "Conectar banco" só aparece com o Open Finance ligado e abre `/contas-e-cartoes` do site num Custom Tab, nunca num WebView; "Atualizar" e "Desconectar" aparecem sempre para quem já tem banco. Os textos de cada módulo têm nomes próprios: um teste do app falha se dois módulos usarem o mesmo nome com textos diferentes (aconteceu com `contas_titulo`).
- Alternativas: esperar a página do site.
- Consequências: quando `/conectar-banco` chegar à `develop`, o Android faz como o iOS: `POST /api/banking/connect-token`, a página num Custom Tab e a volta por `piggu://banco-conectado`.
- Onde ver: `android-nativo/feature/contas/`, `android-nativo/core/ui/.../formatos/Fatura.kt`, `TextosDosModulosTest.kt`

## D-118: Tour do Android em diálogos, no primeiro acesso e pelo Perfil
- Data: 28/10/2026
- Situação: Substituída por D-130
- Contexto: etapa A6. O tour do site (`tutorial.ts`) destaca cada parte da tela; no Compose, apontar para a aba, o + e a busca em cada tamanho de tela seria muito código para um passo a passo de oito cartões.
- Decisão: os oito passos, com os textos do site, aparecem em diálogos em sequência, com Próximo (Concluir no último) e Pular. Abre sozinho no primeiro acesso de cada pessoa no aparelho (a chave `piggu_tutorial_<id>` do site, no DataStore) e pelo "Tour do Piggu" do Perfil.
- Alternativas: destacar os alvos na tela como o site; só abrir pelo Perfil.
- Consequências: o tour não aponta onde está cada coisa; o texto de cada passo diz.
- Onde ver: `android-nativo/app/.../moldura/Tour.kt`, `AppViewModel.tourNovo`

## D-119: O ícone do app troca quando a pessoa sai do app
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A6. O seletor de ícone liga um dos 12 activity-alias do Capacitor e desliga os outros. Desligar o alias que abriu a tarefa faz o Android fechar o app na hora, como acontecia no Capacitor.
- Decisão: a escolha fica guardada e a tela já mostra o ícone marcado. A troca no lançador só acontece quando o app sai da frente (`ProcessLifecycleOwner`, `onStop`), e o texto do Perfil avisa. Os ícones do Premium voltam ao Neon quando o Premium acaba, como a cor (D-101).
- Alternativas: trocar na hora e deixar o app fechar; reabrir o app pelo link `piggu://perfil` (a tarefa nova também morria).
- Consequências: alguns lançadores levam uns segundos para mostrar o ícone novo.
- Onde ver: `android-nativo/feature/perfil/.../IconesDoApp.kt`, `PigguApplication.kt`

## D-120: Metas, Prêmios e Girls Math do Android: criar e editar numa folha
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A6. No site, o formulário é um cartão acima da lista; no celular ele empurra a lista para baixo.
- Decisão: criar e editar abrem numa folha, como no iOS. A foto do Girls Math é posta em pé pelo EXIF, reduzida a 1600 px no lado maior e regravada em JPEG (some a localização) antes de ir para a API. Todo papel usa o Girls Math, o membro também, com foto, como no site e no backend. O Planejar só tem atalhos, como no site, e por isso não tem estados de carregando nem de erro.
- Alternativas: o cartão do site; mandar a foto original.
- Consequências: a foto que sai do aparelho é menor que a original.
- Onde ver: `android-nativo/feature/metas/`, `feature/premios/`, `feature/girlsmath/`, `feature/planejar/`

## D-121: Premium do Android sem a Stripe: "Em breve" até a A8
- Data: 28/10/2026
- Situação: Substituída por D-133
- Contexto: etapa A6. A regra de pagamentos do Google Play não deixa o app abrir outro meio de pagamento para um produto digital. A compra pelo Google Play Billing fica para a A8.
- Decisão: a tela do Premium (`features/plan`) é só do titular, como o `perfilGuard` do site. Ela mostra o plano e os preços, e onde o site tem "Assinar" mostra "Em breve". O checkout e o Portal do Cliente da Stripe nunca abrem no app: quem assinou pelo site lê que deve trocar o cartão ou cancelar pelo navegador. O cancelamento com reembolso nos 7 dias (CDC art. 49) continua no app, como no site.
- Alternativas: abrir o checkout num Custom Tab (contra a regra do Google Play); esconder a tela até a A8.
- Consequências: até a A8, ninguém assina pelo app Android.
- Onde ver: `android-nativo/feature/plano/`

## D-122: Perfil do Android com o que estava no Mais
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A6. Até a A5, o Mais tinha o aviso de confirmar o e-mail, o CPF e o "Excluir minha conta", porque o Perfil abria no site.
- Decisão: com o Perfil nativo, esses três voltam para o Perfil, como no site e no iOS. "Organizar o Painel" abre o Painel com a folha de organizar. Definir ou trocar a senha passa pela sessão (`Sessao.definirSenha`), que manda o refresh token e guarda a sessão nova. A família do Perfil busca o histórico só quando a pessoa pede.
- Alternativas: deixar os três também no Mais.
- Consequências: o Mais fica só com o menu, os textos legais, o Sair e a versão.
- Onde ver: `android-nativo/feature/perfil/`, `android-nativo/app/.../moldura/Mais.kt`

## D-123: "Sair" do Android guarda a fila, como no iOS
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A6. O D-108 fazia o "Sair" apagar os gastos guardados sem internet. O Dudu preferiu o comportamento do iOS (D-090 de lá).
- Decisão: sair, pelo Mais, pelo Perfil ou pelo Aceite, não apaga a fila. Cada pessoa só vê e envia os seus gastos guardados, quando entrar de novo neste aparelho.
- Alternativas: apagar ao sair (D-108).
- Consequências: o gasto lançado sem internet não se perde ao sair da conta; ele fica no aparelho até a mesma pessoa entrar de novo.
- Onde ver: `android-nativo/core/data/.../FilaNoRoom.kt`, `AppViewModel.sair`

## D-124: Compras do Android com leitor de código de barras
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. O site busca no catálogo aberto (`/api/shopping/catalog`) só pelo nome.
- Decisão: um botão de câmera no campo de busca abre o leitor de código de barras do Google Play services (`GmsBarcodeScanning`, só EAN-13, EAN-8, UPC-A e UPC-E), que não pede permissão de câmera ao app; o código lido vai para a busca. Se o leitor não abrir (sem Google Play services), aparece um aviso para buscar pelo nome. Compras e Desejos ficam em botões segmentados, a linha inteira marca e desmarca, e apagar não pede confirmação, como no site.
- Alternativas: a câmera do próprio app com o ML Kit Barcode Scanning (pediria permissão de câmera).
- Consequências: o leitor depende do Google Play services; sem ele, só a busca pelo nome.
- Onde ver: `android-nativo/feature/listas/.../compras/`

## D-125: Lugares e Filmes do Android: mapa pelo app de mapas, formulário numa folha
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. Ainda não há chave do Google Maps para o Android, e o `Lugar` não tem latitude e longitude no contrato.
- Decisão: "Abrir no mapa" abre o app de mapas do aparelho por `geo:0,0?q=<nome>, <localização>`, e só aparece quando há localização. Criar e editar um lugar abrem numa folha, como Prêmios (D-120). A foto do lugar é reduzida a 1600 px e regravada sem EXIF. Os gêneros do sorteio de Filmes ficam em chips, e a nota de cada pessoa aparece separada, com "Ex-membro" para quem saiu da família. Lugares e marcadores ficam guardados juntos, então os marcadores também aparecem sem internet.
- Alternativas: Maps Compose com uma chave do Google Maps.
- Consequências: quando a chave existir, o mapa pode entrar dentro do app (README, seção do mapa).
- Onde ver: `android-nativo/feature/listas/.../lugares/`, `.../filmes/`

## D-126: Fotos do Android: foto reduzida e legenda que se salva sozinha
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. O site manda o arquivo original (até 5 MB) e salva a legenda ao sair do campo; o iOS salva no "Concluir".
- Decisão: a foto (Photo Picker ou câmera) é posta em pé pelo EXIF, reduzida a 1600 px e regravada em JPEG antes de enviar, então o limite de 5 MB não aparece e a localização não sai do aparelho. A legenda se salva 800 ms depois da última letra, no escopo do app, para não se perder se a pessoa sair da tela. O seletor de mês não passa do mês atual. Sem Premium, a pessoa vê, legenda e apaga, e "Adicionar fotos" leva ao Premium.
- Alternativas: mandar o arquivo original; salvar só ao sair do campo.
- Consequências: a foto guardada tem menos resolução que a original.
- Onde ver: `android-nativo/feature/fotos/`

## D-127: Imagens do Android pelo Coil, com o token só para a API do Piggu
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. As fotos de Lugares e do mural vêm de `/api/assets/{id}/content`, que pede a sessão; as do catálogo de compras e os pôsteres são de outros servidores.
- Decisão: o Coil carrega e guarda em cache todas as imagens. Uma imagem do Piggu (`AssetDoPiggu`) vai pelo OkHttp da API, com o token; qualquer outro endereço vai por um cliente sem a sessão, e um teste confere que o token nunca sai para outro servidor. O SVG das fotos da demonstração é lido pelo `coil-svg`.
- Alternativas: o mesmo cliente para tudo (mandaria o token para o servidor de fora).
- Consequências: quem mostrar imagem nova usa `ImagemDoAsset` ou `AsyncImage`, nunca o OkHttp da API direto.
- Onde ver: `android-nativo/app/.../PigguApplication.kt`, `core/ui/.../ImagemDoAsset.kt`, `ImagensSemTokenTest.kt`

## D-128: Foto do recibo no Android: os itens lidos preenchem o formulário
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. No site, "Lançar N itens" do "Conferir recibo" grava na hora.
- Decisão: "Usar N itens" leva os itens, o estabelecimento e a data para o formulário de gasto, e só o "Lançar" grava, com `reciboId` e `origem: "Foto"`. Assim valem a conta ou cartão, as parcelas, a fila sem internet e o `idDoEnvio`. "Tirar foto" usa o recorte do ML Kit Document Scanner, sem permissão de câmera; sem ele, a câmera do sistema com a foto inteira. A galeria manda a foto inteira. A foto é reduzida a 1600 px, sem EXIF. A autorização do Gemini aparece quando o servidor responde `CONSENTIMENTO_NECESSARIO`, com a `versaoDoAviso` do site.
- Alternativas: lançar direto do "Conferir recibo", como o site.
- Consequências: um toque a mais para gravar; o link do Premium com as leituras esgotadas abre o site (conferir a regra do Google Play antes de publicar, A8).
- Onde ver: `android-nativo/feature/gastos/.../formulario/FotoDoRecibo.kt`

## D-129: Widgets, atalhos e bloco das Configurações rápidas do Android
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. O iOS tem os widgets de gasto do mês e de meta e o controle "Lançar gasto" (D-093 de lá).
- Decisão: três widgets do Jetpack Glance: Gasto do mês, Meta do mês e Lançar gasto (o iOS tem esse só como controle), em tamanho pequeno e médio. Eles leem só um resumo já calculado, num DataStore próprio, sem token nem lista de gastos. O resumo é gravado ao abrir o app, ao voltar para a frente e depois de lançar, e some ao sair da conta. O widget segue o claro ou escuro do aparelho e mostra "Atualizado às HH:mm". Os atalhos "Lançar gasto" e "Ver gastos" (os ids do Capacitor) estão nos 12 activity-alias do ícone, e o bloco "Lançar gasto" das Configurações rápidas abre o formulário. O "Quanto gastei este mês" da Siri não tem equivalente; o widget cobre.
- Alternativas: widgets da tela de bloqueio (o Android não tem para apps de terceiros).
- Consequências: o widget só mostra números depois que a pessoa abre o app logada.
- Onde ver: `android-nativo/widget/`, `app/src/main/res/xml/shortcuts.xml`

## D-130: Tour do Android em balões que saem da moldura
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. O D-118 deixou o tour em diálogos, sem apontar onde fica cada coisa. O Material 3 1.4 tem o `TooltipBox` com o `RichTooltip`, que ancora o balão num elemento.
- Decisão: os oito passos do site (`tutorial.ts`), com os mesmos textos e alvos, viram balões persistentes com seta, Pular, Próximo e Concluir. Cada balão sai do alvo que está na tela, na ordem de preferência do site: o +, a aba, o item do trilho ou da gaveta, a busca e o assistente. No celular e no trilho, Planejar e Família apontam para o Mais, com a dica "Fica dentro de Mais"; na gaveta, cada um tem o seu item, e a gaveta rola até ele. O "Bem-vindo" não tem alvo e fica num diálogo no centro. Tocar fora fecha o tour, como o fundo do site. Abre no primeiro acesso de cada pessoa (`piggu_tutorial_<id>`), só depois de desbloquear a trava (D-131), e pelo Perfil, que volta ao Painel antes.
- Alternativas: manter os diálogos (D-118); desenhar o recorte escuro do site por cima da tela.
- Consequências: o fundo não escurece em volta do alvo como no site. O contador "1/8", o "Voltar" e o "Lançar meu primeiro gasto" do site ficaram de fora.
- Onde ver: `android-nativo/app/.../moldura/Tour.kt`, `Moldura.kt`, `AppLogado`

## D-131: Trava do Android com 1 minuto fora e esconder nos recentes
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A7. O iOS trava com Face ID a cada ida ao fundo (`TravaDoApp`). No Android, o site em Custom Tab, a câmera, a galeria e a tela de PIN do próprio BiometricPrompt também tiram o app da frente.
- Decisão: "Travar com a biometria ou o bloqueio de tela" no Perfil vale para este aparelho, com a chave do iOS. Pede a prova ao abrir o app e ao voltar depois de 1 minuto ou mais fora, com BIOMETRIC_STRONG ou o bloqueio de tela (antes do Android 11, a biometria fraca). Ligar pede a prova antes; sem biometria nem bloqueio de tela, a opção fica desligada com a explicação. A trava é uma janela por cima de tudo, com o porquinho e "Desbloquear", e um link (widget, atalho, bloco) que chega com o app travado espera o desbloqueio. "Esconder nos apps recentes", só no Android, liga o `FLAG_SECURE`, e o texto avisa que isso também bloqueia o print e a gravação da tela.
- Alternativas: travar a cada ida ao fundo, como no iOS (travaria na volta do site e da câmera, e o PIN entraria em laço); travar ao sair, não ao voltar.
- Consequências: até 1 minuto fora, o app volta sem pedir a prova. Sem o "Esconder nos recentes", a miniatura dos recentes mostra os valores.
- Onde ver: `android-nativo/app/.../Trava.kt`, `feature/perfil/.../Biometria.kt`

## D-132: Premium no Android pela compra do Google Play, conferida no backend com a Play Developer API
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A8. No app, a assinatura digital tem que ser pela compra do Google Play. O caminho pelo RevenueCat (`/api/billing/stores/play-store/purchases`) nunca foi configurado, e para o iPhone a D-094 do iOS (trazida para esta branch junto com o backend da Apple) já trocou o intermediário pela biblioteca da Apple.
- Decisão: o app compra `premium_mensal` ou `premium_anual` pelo Play Billing, com o id da conta Piggu no `obfuscatedExternalAccountId`, e manda o `purchaseToken` para `POST /api/billing/google-play/purchases`. Os avisos em tempo real (RTDN) chegam pelo push autenticado do Pub/Sub em `POST /api/billing/webhooks/google-play`, provado pelo token OIDC do Google (`aud` e `email` configurados) com o mesmo `GoogleIdTokenVerifier` do login. O aviso só diz qual token consultar: o estado sempre vem de `purchases.subscriptionsv2.get` (biblioteca oficial `google-api-services-androidpublisher`, conta de serviço). A compra grava no mesmo plano da casa que a Stripe atualiza, com origem `PLAY_STORE` e `premiumAte` = `expiryTime`. Dão Premium ACTIVE, IN_GRACE_PERIOD e CANCELED (até o fim do período). A compra só vale para a conta do `obfuscatedExternalAccountId` (409 `OUTRA_CONTA`). Depois de aplicar, o backend faz o acknowledge, se pendente; se falhar, a compra vale e o próximo aviso tenta de novo. Premium vigente de outra origem (site ou Apple) não é trocado nem derrubado. Sem configuração, as rotas respondem 501 `EM_BREVE`.
- Alternativas: o RevenueCat (mais um operador de dados e uma conta paga); conferir só no aparelho (qualquer um forjaria o Premium); confiar no conteúdo do RTDN (o aviso não traz validade nem dono).
- Consequências: o servidor precisa de `GOOGLE_PLAY_PACOTE`, `GOOGLE_PLAY_CREDENCIAIS`, `GOOGLE_PLAY_PUSH_AUDIENCIA` e `GOOGLE_PLAY_PUSH_EMAIL`, e o Google Cloud e o Play Console precisam da conta de serviço, do tópico e da assinatura push (README do android-nativo, seção Premium). Casal e família continuam só no site.
- Onde ver: `backend/piggu-identity/src/main/java/com/piggu/identity/billing/GooglePlayLoja.java`, `PlayDeveloperApi.java`, `CompraNoGoogleTest.java`

## D-133: Premium do Android pelo Google Play Billing, e o Stripe fora do app
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A8. A D-121 deixava "Em breve" no lugar de assinar até a compra pelo Google Play. No Brasil, o Google Play aceita o faturamento alternativo (user choice billing) para apps que não são jogos: o sistema próprio ao lado do Google Play, com a tela de escolha, as APIs de faturamento alternativo, o relatório de cada venda em até 24 h, e a taxa só 4 pontos menor (cerca de 11% em vez de 15% nas assinaturas). O programa novo de 2026 (links externos com 10%) ainda não inclui o Brasil.
- Decisão: o app usa a Google Play Billing Library 9.1.0 com as assinaturas `premium_mensal` e `premium_anual`, cada uma com um plano básico, e a oferta de teste grátis quando a pessoa tem direito. A compra leva o id da conta Piggu no `obfuscatedAccountId` e o `purchaseToken` vai ao backend (D-132); a tela só mostra o Premium depois da resposta dele, e o app nunca faz acknowledge. A compra que não chegou ao backend vai sozinha quando a tela abre, e "Restaurar compras" manda todas as ativas. Pagamento pendente só avisa; cancelar não é erro. O preço é o do Google Play (sem ele, o `app` do `/billing/plan`). Quem já é Premium pelo site, pela Apple ou pela casa vê o plano e nenhum botão de compra; quem assinou pelo Google Play gerencia nas assinaturas do Google Play. Casal e família continuam só no site, e o app diz que não os vende, sem apontar o site. O Stripe não é oferecido no app, e nenhum link do app leva ao `/plano` do site: os pedidos de "/plano" abrem a tela nativa.
- Alternativas: oferecer o Stripe no app pelo faturamento alternativo do Brasil (pagaria cerca de 11% ao Google mais a taxa da Stripe, quase o mesmo que os 15% do Google Play, com a tela de escolha, o relatório diário e o PCI por nossa conta); o RevenueCat.
- Consequências: no Play Console falta criar as duas assinaturas com os preços do app (R$ 22,89 e R$ 228,85). Até lá, a compra responde "Não foi possível falar com o Google Play". Substitui a D-121.
- Onde ver: `android-nativo/feature/plano/` (`LojaDoGooglePlay.kt`, `PlanoViewModel.kt`), `android-nativo/app/src/{producao,demo}/.../ModuloDaVariante.kt`

## D-134: A cor do texto do tema no Android passa no WCAG AA
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A9. O rosa do Chiclete do site (`#D1006B`), usado como texto, fica com 4,14:1 sobre o fundo dos cartões do Material 3 (surfaceContainerHighest), abaixo do 4,5:1 do WCAG AA. O mesmo acontece no Chiclete escuro e no Tangerina e Turquesa claros.
- Decisão: no Android, a `primary` dessas variantes é a cor do site com a luminosidade ajustada só o necessário para 4,5:1 sobre o fundo e todos os containers: Chiclete `#C60065` (claro) e `#FF64B2` (escuro), Tangerina `#B73A00` e Turquesa `#00707B` (claros). O resto do esquema continua saindo da cor do site (`texto` em `gerar-cores.mjs`), e o `ContrasteTest` falha se uma cor nova ficar abaixo de 4,5:1. Os títulos das telas são anunciados como título, a barra de abas diminui o rótulo em vez de cortar, e o `AcessibilidadeTest` falha se um nó tocável não tiver rótulo.
- Alternativas: manter a cor do site no texto (abaixo do AA em 4 variantes); escurecer também os botões e os containers.
- Consequências: o rosa do app fica um pouco mais escuro que o do site nessas variantes; o site não muda.
- Onde ver: `android-nativo/core/designsystem/` (`Cores.kt`, `gerar-cores.mjs`, `ContrasteTest.kt`), `app/src/testDemo/.../AcessibilidadeTest.kt`

## D-135: Release do Android: chave de upload por variável, AAB no CI e abertura medida no Painel
- Data: 28/10/2026
- Situação: Valendo
- Contexto: etapa A9. A 2.0.0 nativa substitui o app Capacitor no mesmo pacote (D-057), cujo único APK distribuído tem `versionCode` 1.
- Decisão: a versão é 2.0.0 com `versionCode` 200. A release usa R8 e o Baseline Profile gerado na demo (entrar, Painel e abas), que vale para as duas variantes (`mergeIntoMain`). A chave de upload é lida de variáveis de ambiente (`PIGGU_UPLOAD_*`) e nunca entra no repositório; no CI, um job gera o AAB de produção como artefato, e o envio ao Play Console é manual, com a confirmação do Dudu. O fim da abertura é o Painel com os números (`ReportDrawnWhen`), não o primeiro quadro: num emulador de API 26 com 2 GB, com o perfil compilado como o Play compila, o Painel abre em ~500 ms (mediana de 8 aberturas a frio). Os links do app para "/plano", "/contas-e-cartoes" e "/privacidade" abrem a tela nativa (`LocalAbrirCaminho`), e a marca do topo segue o ícone escolhido no Perfil, como no iOS (D-097 de lá).
- Alternativas: publicar pelo CI direto no Play Console (decidido manual até o primeiro lançamento); medir só o primeiro quadro.
- Consequências: gerar o perfil de novo quando a abertura mudar (`./gradlew :app:generateBaselineProfile`, com um emulador do Android 13 ou mais novo).
- Onde ver: `android-nativo/app/build.gradle.kts`, `.github/workflows/android-nativo.yml`, `android-nativo/baselineprofile/`, README do android-nativo (Lançamento)
## D-139: Push no Android pela API HTTP v1 do FCM, sem o SDK do Firebase
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa A1 do plano Android. O app Android já registrava o token do FCM (identity V36), mas o lembrete diário só mandava para o iPhone.
- Decisão: `CanalFcm` assina um JWT RS256 com a chave da conta de serviço (o JSON em `FCM_CONTA_DE_SERVICO`), troca por um access token no Google (reaproveitado por 50 minutos) e chama `projects/{id}/messages:send`. O lembrete manda para cada aparelho da plataforma pelo canal dela. O token é apagado só quando o FCM responde `UNREGISTERED` ou um 400 que aponta o token; um 404 sem isso (projeto errado) não apaga nada.
- Alternativas: o Firebase Admin SDK, que traria dezenas de dependências para uma chamada HTTP que o serviço já sabe fazer, como faz com o APNs.
- Consequências: o envio só liga com `FCM_CONTA_DE_SERVICO` preenchida. A troca do token sempre vai para `oauth2.googleapis.com`, ignorando o `token_uri` do JSON. A mensagem é só notificação (título e resumo), sem dados para o app abrir uma tela.
- Onde ver: `backend/piggu-identity/.../avisos/CanalFcm.java`, `LembretesDiarios.java`, [implantacao-producao.md](implantacao-producao.md)

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

## D-089: App iOS nativo junta na `feature/ios-nativo` antes da `develop`
- Data: 27/10/2026
- Situação: Valendo
- Contexto: as etapas do iOS nativo saem uma por PR, empilhadas umas nas outras, e o Dudu quer testar o app com tudo junto antes de levar para a `develop`.
- Decisão: os PRs das etapas (`swift/etapa-N`) vão para a `feature/ios-nativo`, criada a partir da `develop`. Quando o conjunto estiver testado, um PR leva a `feature/ios-nativo` para a `develop`, pelo caminho normal da D-061.
- Alternativas: cada etapa direto na `develop`, que levaria app pela metade para a branch que junta as features.
- Consequências: a `feature/ios-nativo` precisa receber a `develop` de tempos em tempos para não divergir. O CI do iOS (`ios-nativo.yml`) roda em PR para qualquer branch.
- Onde ver: [ios-nativo/CLAUDE.md](../ios-nativo/CLAUDE.md), [branches.md](branches.md)

---

## D-137: Etapas do Android juntam em `feature/android-nativo`
- Data: 27/10/2026
- Situação: Valendo
- Contexto: o app Kotlin vai crescer por várias etapas (A0 a A9) antes de estar pronto para substituir o Capacitor (D-057).
- Decisão: cada etapa sai numa `android/etapa-N` a partir da `feature/android-nativo`, com PR para ela. A `feature/android-nativo` vai para a `develop` por PR quando o Dudu pedir.
- Alternativas: PR de cada etapa direto para a `develop`, como no plano original.
- Consequências: a `feature/android-nativo` precisa receber a `develop` de tempos em tempos para não divergir. O workflow `android-nativo.yml` roda em PR para qualquer branch.
- Onde ver: `android-nativo/CLAUDE.md`, `docs/branches.md`

# Perguntas para o Dudu

Decisões que parecem tomadas, mas sem o porquê escrito em lugar nenhum. Quando a resposta
vier, ela entra como Contexto ou Alternativas da decisão citada (ou vira uma decisão nova).

1. **O que precisa acontecer para religar o Open Finance e o leitor próprio de nota?** Por
   exemplo, um número de assinantes Premium ou uma receita mínima. (D-045, D-047)

As oito perguntas de 25/10/2026 foram respondidas em 26/10/2026 e estão em D-001, D-006,
D-008, D-045, D-047, D-049, D-069, D-070 e D-071.

## D-095: Etapa 9 do iOS: abrir com o perfil guardado e empilhar no texto grande
- Data: 27/10/2026
- Situação: Valendo
- Contexto: etapa 9 do plano iOS (acabamento antes do TestFlight 2.0.0). Abrindo o app, a sessão esperava a renovação do token na rede antes de mostrar qualquer tela, então nem o Painel guardado aparecia antes da resposta do servidor; o plano pede menos de 1 segundo até o Painel com cache. A auditoria de acessibilidade (a mesma do Accessibility Inspector) e os prints no maior tamanho de texto mostraram valores e palavras quebrados no meio em linhas lado a lado, o calendário com os dias partidos e o anel das metas estourando.
- Decisão: com o perfil guardado no aparelho, o app abre logado na hora, com o que cada tela guardou, e a renovação segue por trás; só o refresh recusado (401) encerra a sessão, e sem rede ou com o servidor fora a sessão continua. As chamadas feitas antes de o token chegar esperam a renovação em curso, em vez de levar um 401 cada uma. Nos tamanhos de texto de acessibilidade, as linhas lado a lado viram uma embaixo da outra (`LadoALado`), o seletor de mês encurta o nome ("Out. de 2026" ou "10/2026", lendo o nome inteiro no VoiceOver), e o calendário e o número dentro do anel param no tamanho xxLarge e xxxLarge (o VoiceOver lê cada dia e o percentual). As estrelas viram um só elemento ajustável no VoiceOver, e os avisos que somem sozinhos são lidos na hora. O alvo `PigguUITests` roda a auditoria em todas as telas (texto padrão, maior e escuro), tira os prints e mede a abertura pelo intervalo "Abrir ate o Painel" (os_signpost). A versão nativa é a 2.0.0 (D-057), build 2.
- Alternativas: manter a espera pela renovação (o Painel guardado só servia sem internet); limitar o tamanho do texto no app inteiro, que tira o Dynamic Type de quem precisa dele.
- Consequências: quem teve a sessão revogada em outro lugar vê por um instante o que estava guardado antes de voltar ao login, como já acontecia sem internet; a trava com Face ID continua cobrindo a tela. Medido no simulador com a build otimizada e o cache cheio: 0,63 a 0,75 s do `init` do app ao Painel com dados, também com 3.000 gastos no mês; o carregamento do sistema antes do `init` no simulador não representa o aparelho e precisa ser conferido no iPhone pelo Instruments (App Launch). Os avisos de contraste em textos sob a barra de abas de vidro e nos rótulos de seção do sistema ficam como estão.
- Onde ver: `ios-nativo/Piggu/Core/Auth/Sessao.swift`, `ios-nativo/Piggu/Core/Design/LadoALado.swift`, `ios-nativo/Piggu/Core/Sistema/Desempenho.swift`, `ios-nativo/PigguUITests/`

## D-096: CI do iOS nativo no Xcode 27
- Data: 27/10/2026
- Situação: Valendo
- Contexto: o `macos-latest` do GitHub ainda vem com o Xcode 26.6 (SDK do iOS 26.5), e o app usa APIs do iOS 27 desde a etapa 4 (arrastar os cartões do Painel). O build da CI quebrava em todos os PRs das etapas 4 a 9, embora compilasse no Mac do Dudu, que tem o Xcode 27. O archive da 2.0.0 também sai do Xcode 27.
- Decisão: o job do iOS nativo roda no runner `xcode-27` do GitHub, com o mesmo Xcode do archive.
- Alternativas: cercar as APIs do iOS 27 com `#if compiler(>=6.4)`, o que faz a CI testar um app diferente do enviado.
- Consequências: o `xcode-27` ainda é uma imagem de prévia do GitHub e pode demorar mais na fila. Quando o `macos-latest` passar a ter o Xcode 27, o job volta para ele.
- Onde ver: `.github/workflows/ios-nativo.yml`

## D-097: Topo das abas com a marca, a busca e o assistente; trava em janela própria
- Data: 27/10/2026
- Situação: Valendo
- Contexto: no primeiro teste no iPhone (build 2.0.0 Ad Hoc), o Dudu achou o topo vazio, sentiu falta da IA e não conseguiu fechar o teclado do valor depois de o aparelho bloquear. A revisão de paridade da etapa 9 percorreu só as rotas, e o assistente e a busca do site ficam no cabeçalho, sem rota. O SwiftUI não fecha o teclado ao tocar fora, e o teclado numérico não tem tecla para fechar. A trava do Face ID era um overlay na raiz, e folhas como o lançar gasto abrem por cima dele. O Dudu também pediu para criar categoria nas regras de categoria e para a barra de abas não sumir ao rolar.
- Decisão: cada aba ganha o cabeçalho do site: o ícone do app que a pessoa escolheu no Perfil, à esquerda, ocupando uma bolha de vidro, sem o nome ao lado (volta ao Painel) e, à direita, a busca global (telas, gastos de todos os meses pelo servidor, lugares, filmes e compras) e o assistente de IA, numa folha, com a mesma autorização, os mesmos limites e a conversa só no aparelho por 30 dias (no cache, que sai ao sair da conta). Tocar fora de um campo ou arrastar a lista fecha o teclado no app inteiro. A trava abre numa janela própria, acima de qualquer folha, e fecha o teclado ao travar. As regras de categoria usam o mesmo campo do lançar gasto, com "+ Nova categoria…". A barra de abas fica sempre inteira, e encolher ao rolar vira um ajuste no Perfil, desligado.
- Alternativas: uma aba própria para o assistente, que tirava espaço de outra aba; o botão OK sobre o teclado numérico, que o iOS 27 não mostrou numa folha (`ToolbarItemGroup(placement: .keyboard)`).
- Consequências: o link de um gasto aceita o mês (`piggu://gastos?id=...&mes=aaaa-mm`); sem ele, continua o mês atual, como no Spotlight. Na demonstração o assistente responde com um texto de exemplo.
- Onde ver: `ios-nativo/Piggu/Telas/Topo/`, `ios-nativo/Piggu/Core/Sistema/Janela.swift`, `ios-nativo/PigguUITests/TopoETecladoUITests.swift`

## D-098: O + do iOS nativo abre as opções, como no site
- Data: 28/10/2026
- Situação: Valendo
- Contexto: no app Capacitor, o + abre um balão com Lançar gasto, Lançar receita e Foto do recibo. No nativo, o + abria direto o gasto, e a receita só saía pelo botão da aba Receitas. O Dudu sentiu falta no teste do iPhone.
- Decisão: o + vira um menu com Lançar gasto e Lançar receita, de qualquer aba. A receita abre a mesma folha da aba Receitas, que agora busca a família ela mesma, para saber se mostra "Compartilhada". A foto do recibo fica para um PR à parte, porque precisa de câmera, permissão e consentimento, e no gasto o nativo já lê a nota pela câmera.
- Alternativas: tocar no + lança gasto e segurar abre o menu, que é mais rápido para o gasto, mas esconde a receita; o + da aba Receitas lançar receita, que muda o botão conforme a aba.
- Consequências: o gasto passa a pedir um toque a mais pelo +. O atalho, a Siri, o widget e o link `piggu://gasto` continuam abrindo o gasto direto.
- Onde ver: `ios-nativo/Piggu/Core/Design/Vidro.swift` (`BotaoDeLancar`), `ios-nativo/Piggu/Telas/PrincipalView.swift`, `ios-nativo/PigguUITests/TopoETecladoUITests.swift`

## D-099: Mês que acompanha o puxar, barras por categoria num desenho só e vazios dos cartões no centro
- Data: 28/10/2026
- Situação: Valendo
- Contexto: os primeiros testers do TestFlight acharam três coisas. Ao puxar para recarregar, a lista descia e o seletor de mês ficava parado no topo. Nos gráficos por categoria (Relatórios do mês e do ano, Por categoria no Painel), o valor ficava colado no fim da barra e o nome solto em cima, com folgas diferentes em cada gráfico. E os botões de primeiro passo dos cartões do Painel não tinham padrão: "Fazer o primeiro depósito" no centro e "Criar o primeiro objetivo" à esquerda.
- Decisão: o seletor de mês continua flutuando no topo, mas desce junto com a lista ao puxar (`comSeletorDeMes`, pela geometria da rolagem, no iOS 18 ou mais novo), nas sete telas com mês. As barras por categoria viram um componente só (`BarrasPorCategoria`): o nome à esquerda e o valor à direita na mesma linha, e a barra embaixo, de 8 pt, contra a maior, com o traço do mês anterior quando houver. O desenho é o mesmo das barras de orçamento e de objetivo. O vazio de um cartão do Painel (`VazioDoCartao`) fica no centro, como o vazio das listas: ícone com título, texto curto e o botão pequeno.
- Alternativas: o mês como primeiro item da lista, que desce ao puxar mas some ao rolar (o Dudu preferiu fixo); manter o Swift Charts com mais folga, que segue cortando o valor com nomes longos e textos grandes; os vazios à esquerda, como o resto do cartão.
- Consequências: no iOS 17 o mês segue parado ao puxar. O gráfico do ano, mês a mês, continua no Swift Charts.
- Onde ver: `ios-nativo/Piggu/Core/Design/SeletorDeMes.swift`, `ios-nativo/Piggu/Telas/Componentes/Componentes.swift`

## D-140: Importar extrato no app iOS, sem passar pelo site
- Data: 29/10/2026
- Situação: Valendo
- Contexto: pela D-090, "Importar extrato" (vazio de Gastos e guia de primeiros passos) abria o `piggu.com.br/gastos#importar` no Safari. Quem testou achou que era bug: tocava num botão do app e caía no site.
- Decisão: a importação de OFX ou CSV fica nativa, numa folha (`ImportarExtratoView`) aberta pelos Gastos (uma linha acima das regras de categoria) e pelo guia do Painel. Segue o `importar-extrato.ts`: o arquivo vem do app Arquivos (`fileImporter`), até 2 MB, lido em UTF-8 e, se não for UTF-8 válido, em Latin-1; a prévia (`POST /expenses/import/preview`) vem com o já importado desmarcado; cada linha troca de categoria e todas podem ir para uma conta; `POST /expenses/import` grava os marcados e mostra importados e pulados. Substitui essa parte da D-090. Com isso o `LinkDoSite` ficou sem uso e saiu.
- Alternativas: esconder o botão no iOS (a pessoa teria que lembrar do site); manter o link com um aviso de que abre o site.
- Consequências: o OFX não tem tipo do sistema, então o `Info.plist` declara o `com.intuit.ofx` (`.ofx` e `.qfx`); sem isso, o `.ofx` poderia aparecer apagado no app Arquivos. O "Trazer do banco" (Open Finance, Premium) e o "Exportar CSV do mês" desse bloco do site não entram aqui: o CSV já sai pelos Relatórios, e o do banco fica para depois. Nenhuma rota nova no backend.
- Onde ver: `ios-nativo/Piggu/Telas/Gastos/ImportarExtratoView.swift`, `ios-nativo/Piggu/Telas/GastosView.swift`, `ios-nativo/Piggu/Telas/Painel/PrimeirosPassos.swift`

## D-141: iPhone deitado, aba e pilhas por janela (iPhone Duo)
- Data: 29/10/2026
- Situação: Valendo
- Contexto: o iPhone Duo aberto pede a tela deitada, e o projeto do iOS nativo só aceitava retrato no iPhone. Ao abrir e fechar o aparelho, ou com duas janelas do Piggu lado a lado, a aba e a tela aberta não podiam se perder nem ser a mesma nas duas janelas.
- Decisão: o iPhone aceita retrato e as duas paisagens. Sem o retrato de cabeça para baixo, que é o padrão da Apple no iPhone. A aba e as pilhas do Painel e do Mais ficam no `@SceneStorage` de cada janela. As pilhas são guardadas como texto (`orcamento,cartoes`), e uma tela que não existe mais some da pilha. A sessão continua sendo uma só para o app: a segunda janela não restaura de novo. Na tela estreita (a de fora do Duo e o iPhone mini), quando não cabem lado a lado, as ações da linha do filme descem para baixo do título e os botões de foto (galeria e câmera) ficam um embaixo do outro. O cartão do leitor de nota tem no máximo 520 pt de largura com a tela deitada.
- Alternativas: só retrato, que deixa o Duo aberto de lado; guardar a aba no `@AppStorage`, que faria as duas janelas pularem juntas.
- Consequências: o simulador do iPhone Duo do Xcode 27.1 não dobra nem gira, então a troca de tamanho é testada girando o iPhone (`GirarUITests`), e abrir e fechar o Duo fica para o aparelho. As pilhas das abas Gastos, Receitas e Relatórios não são guardadas, porque não empilham telas. As folhas abertas (lançar gasto, receita) não voltam se o sistema fechar a janela.
- Onde ver: `ios-nativo/Piggu.xcodeproj/project.pbxproj` (`INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone`), `ios-nativo/Piggu/Telas/PrincipalView.swift`, `ios-nativo/Piggu/Telas/TelaDoMais.swift` (`Binding.guardada`), `ios-nativo/Piggu/Telas/Estilo/FilmesView.swift`

## D-142: Revogar o Entrar com Apple ao excluir a conta
- Data: 29/10/2026
- Situação: Valendo
- Contexto: a Apple exige, para app com o Entrar com Apple, que excluir a conta revogue o acesso dado ao app (Diretriz 5.1.1(v)). A D-072 deixou isso para depois, e a revisão da Apple recusaria o app sem ele.
- Decisão: o app manda o `authorizationCode` em `codigo` no `POST /api/auth/apple`. O identity troca o código por um refresh token da Apple (`/auth/token`) e o guarda na conta (`users.apple_refresh_token`, com o Bundle ID em `apple_client_id`). Ao excluir a conta, manda esse token para `/auth/revoke`. Os dois pedidos levam um client secret: um JWT ES256 de 5 minutos assinado com uma chave `.p8` com o Entrar com Apple ligado (`APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_CHAVE`). Pode ser a mesma chave do APNs, se ela tiver o Entrar com Apple marcado. A troca é refeita a cada login com a Apple, então fica sempre o token mais novo. Se a Apple recusar ou estiver fora do ar, nem o login nem a exclusão param: a falha fica no log.
- Alternativas: pedir o Entrar com Apple de novo na hora de excluir e trocar o código ali, sem guardar token, o que não cobre quem exclui pelo site; guardar o token cifrado como o CPF, o que não vale a pena, porque o token sozinho não serve sem a chave `.p8` do Piggu.
- Consequências: contas que entraram com a Apple antes desta mudança só ganham o token no próximo login com a Apple. Sem as três variáveis, o servidor não guarda token nem revoga, e o login segue igual. O token não vai no arquivo de "exportar meus dados", porque é uma credencial e não um dado da pessoa.
- Onde ver: `backend/piggu-identity/.../apple/RevogacaoDaApple.java`, `.../domain/MinhaContaService.java` (`excluir`), `.../domain/AuthService.java` (`entrarComApple`), `ios-nativo/Piggu/Core/Auth/EntradaComApple.swift`
