# Roadmap do Piggu

Última atualização: 30/10/2026.

**Regra:** concluiu um item? Atualize este arquivo no mesmo PR que o conclui.

Este arquivo é o resumo. Os detalhes, as etapas e os prompts ficam nos planos:

- [Plano do app nativo iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y) (etapas 0 a 9)
- [Plano do app nativo Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G) (etapas A0 a A9)
- [Planos Premium](planos-premium.md)

## Agora

- Google Play: abrir a conta e começar o teste fechado com 12 pessoas por 14 dias, que conta pessoal nova precisa antes de publicar ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).
- Documentos do repositório: `docs/API_SPEC.md`, `docs/DATA_MODEL.md` e `docs/DECISIONS.md`, nas branches `docs/contrato-da-api`, `docs/modelo-de-dados` e `docs/decisoes`.

## Depois (próximos 1 a 2 meses)

- Entrar com Apple: pôr no servidor o Bundle ID em `APPLE_CLIENT_IDS` e a chave `.p8` com o Entrar com Apple em `APPLE_TEAM_ID`, `APPLE_KEY_ID` e `APPLE_CHAVE`, sem a qual excluir a conta não revoga na Apple (D-072 e D-142).
- Abrir um grupo "Nativo" no TestFlight, agora que a etapa 4 do iOS está pronta.
- App Store Connect: grupo "Piggu Premium" com os produtos `br.com.piggu.premium.mensal` e `.anual`, preços, notificações V2 em `/api/billing/webhooks/app-store` e `APP_STORE_APP_APPLE_ID` no servidor (D-094).
- Mandar o 2.0.0 (build 2) do iOS nativo ao TestFlight e liberar para o grupo de testes, quando o Dudu pedir.
- Medir a abertura do 2.0.0 num iPhone de verdade (Instruments, App Launch): a meta é menos de 1 segundo até o Painel com cache (D-095).
- Abrir o acesso fechado (Cloudflare Access) antes de mandar o app para a revisão da Apple.
- Android: push pelo Firebase no app nativo (o item 8 da A7), com o backend da A1 já na `develop` (D-139), quando o projeto do Firebase existir.
- Android: mapa dentro do app (Maps Compose) quando houver a chave do Google Maps (D-125).
- Android: criar no Play Console e no Google Cloud as assinaturas, a conta de serviço e o Pub/Sub da compra (D-132, D-133).
- Android: enviar o 2.0.0 ao teste interno do Play Console (só com a confirmação do Dudu), criar o ID OAuth do Android com a chave do Play (D-104) e, depois, apagar `frontend/android` e o `android-demo.yml`.
- Stripe de verdade: conta, produto com os dois preços, webhook e Portal do Cliente ([planos Premium](planos-premium.md#falta-antes-de-cobrar-de-verdade)).
- Religar o Open Finance e o leitor próprio de nota, desligados antes da 1.0.0 ([planos Premium](planos-premium.md#desligado-por-enquanto-lembrar-de-reverter)).
- Um mês depois do 2.0.0 estar na loja sem problemas: apagar `frontend/ios` e os workflows `ios-demo.yml` e `ios-producao.yml`.

## Ideias (sem compromisso)

- Importar o histórico da planilha antiga, porque o banco sobe vazio ([backend/README](../backend/README.md#o-que-ainda-falta)).
- Academia, que só existia no app antigo e ficou de fora ([frontend/README](../frontend/README.md#o-que-ainda-falta)).
- Premium vencido: desconectar o banco sozinho 30 dias depois, com aviso ([planos Premium](planos-premium.md#falta-antes-de-cobrar-de-verdade)).
- Aviso de "atualize o app" quando a versão instalada for velha demais ([plano iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y)).
- Congelar features novas no site enquanto a migração para o nativo anda ([plano iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y)).
- Dividir código entre iOS e Android com Kotlin Multiplatform, se manter duas camadas de rede virar problema ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).
- Oferecer o Stripe também dentro do app Android, se as regras do Google Play no Brasil deixarem ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).

## Feito recentemente

- 30/10/2026: Android: conectar banco pela página `/conectar-banco` do site, com a volta ao app (D-150), e o `assetlinks.json` dos App Links com as chaves de upload e de debug.
- 30/10/2026: Android: ID OAuth do Android no Google Cloud (chaves de upload e debug) e botão do Google ligado no app nativo (D-104).
- 29/10/2026: Entrar com Apple: excluir a conta revoga o acesso na Apple, como a App Store exige (D-142).
- 29/10/2026: iOS nativo: iPhone deitado (iPhone Duo aberto), aba e pilhas guardadas por janela, prints revistos no iPhone mini e build 3 (D-141).
- 29/10/2026: iOS nativo: importar extrato OFX ou CSV no próprio app, sem abrir o site (D-140).
- 28/10/2026: Android nativo, etapa A9: paridade conferida, acessibilidade (TalkBack, fonte grande, contraste), tamanhos do celular pequeno à tela grande, R8 e Baseline Profile (Painel em ~500 ms num aparelho simples), versão 2.0.0 e AAB no CI, na branch `feature/android-nativo` (D-134 e D-135).
- 28/10/2026: Android nativo, etapa A8: Premium pelo Google Play Billing no app e no backend (Play Developer API e RTDN), no mesmo plano da Stripe e da Apple, na branch `feature/android-nativo` (D-132 e D-133).
- 28/10/2026: Android nativo, etapa A7: Compras, Lugares, Filmes, Fotos, widgets, atalhos, bloco das Configurações rápidas, trava com a biometria, tour em balões e foto do recibo, na branch `feature/android-nativo` (D-124 a D-131). O push ficou para depois da A1.
- 28/10/2026: Android nativo, etapa A6: Minhas metas, Girls Math, Planejar, Prêmios, Família, Premium (sem compra até a A8) e Perfil, com o ícone do app e o tour, na branch `feature/android-nativo` (D-118 a D-122).
- 28/10/2026: Android nativo, etapa A5: Contas do mês, Orçamento, Calendário, Relatórios e Cartões e bancos, na branch `feature/android-nativo` (D-113 a D-117).
- 28/10/2026: Android nativo, etapa A4: Painel, Gastos, Receitas, o + com os formulários, a fila sem internet, importar e exportar, a busca e o assistente, na branch `feature/android-nativo` (D-108 a D-112).
- 28/10/2026: Android nativo, etapa A3: a entrada inteira (login, Google, criar conta, aceite, Sobre você, CPF, links do e-mail, termos e excluir a conta), na branch `feature/android-nativo` (D-104 a D-107).
- 28/10/2026: Android nativo, etapa A2: modelos e rede, sessão, 13 cores, componentes, moldura com abas, Mais e gaveta, cache offline, login por e-mail e o modo demonstração, na branch `feature/android-nativo` (D-138 e D-100 a D-103).
- 28/10/2026: iOS nativo, ajustes dos testers: o mês desce junto ao puxar para recarregar, as barras por categoria num desenho só e os vazios dos cartões do Painel centralizados (D-099).
- 28/10/2026: iOS nativo: o + abre as opções, como no site, e lança receita de qualquer aba (D-098).
- 27/10/2026: Android nativo, etapa A0: `android-nativo/` com módulos, tema do Piggu, abas e CI, na branch `feature/android-nativo` ([#106](https://github.com/Piggubr/cofrinho-app/pull/106)).
- 27/10/2026: Android, etapa A1: push no Android pelo FCM (API HTTP v1) (D-139).
- 27/10/2026: iOS nativo, ajustes do primeiro teste no iPhone: logo, busca global e assistente de IA no topo das abas, teclado que fecha ao tocar fora, trava do Face ID acima das folhas, nova categoria nas regras e a barra de abas fixa por padrão (D-097).
- 27/10/2026: iOS nativo, etapa 9: paridade com todas as rotas do site, "Sobre você" pedindo CPF e celular (D-088), acessibilidade revista pela auditoria em todas as telas (texto grande, VoiceOver, Reduzir Movimento, escuro), iPad do mini ao Pro 13, abrir com o perfil guardado e a versão 2.0.0 (D-095).
- 27/10/2026: iOS nativo, etapa 8: Premium pela compra da Apple (StoreKit 2) com a transação e as notificações V2 conferidas no backend pela biblioteca da Apple, no mesmo plano da Stripe; quem é Premium pelo site não vê a loja (D-094).
- 27/10/2026: iOS nativo, etapa 7: Compras, Lugares (com mapa), Filmes e Fotos; widgets de gasto do mês e de meta (tela inicial e de bloqueio) e o controle "Lançar gasto"; Siri ("Lançar gasto" e "Quanto gastei este mês"); Face ID; notificações; dicas com TipKit; leitura da nota pela câmera; gastos no Spotlight (D-093).
- 27/10/2026: iOS nativo, etapa 6: Minhas metas com o anel animado, Girls Math com foto, Planejar, Prêmios, Família (convites, parceria, histórico e a visão de membro) e Perfil (foto pela galeria e câmera, moeda, avisos, compartilhamento, telas do menu, aparência, cor e ícone do app, senha, meus dados), D-092.
- 27/10/2026: iOS nativo, etapa 5: Contas do mês, Orçamento, Calendário (lado a lado no iPad), Relatórios com o ano e o CSV, Cartões e bancos com a fatura paga (D-085) e conectar banco pelo navegador do sistema (D-091); salário em duas parcelas nas Receitas (D-087).
- 27/10/2026: iOS nativo, etapa 4: Painel com todos os cartões do site, Gastos (busca, filtros, editar, apagar, parcelas, divisão e regras), lançamento rápido igual ao do site, Receitas e a receber, e fila de gastos sem internet (D-090). As etapas agora juntam na `feature/ios-nativo` (D-089).
- 26/10/2026: iOS nativo, etapa 3: entrada completa (Apple, Google, criar conta, confirmar e-mail, CPF, termos, Sobre você, esqueci e redefinir a senha), Termos e Aviso nativos e excluir conta (D-083).
- 26/10/2026: iOS nativo, etapa 2: cores e temas do site, Sora, vidro, abas, estados das telas, cache sem internet e sessão no Keychain atrás de uma flag ([#92](https://github.com/Piggubr/cofrinho-app/pull/92)).
- 26/10/2026: iOS nativo, etapa 0: `swift/inicio` no GitHub, `ios-nativo/CLAUDE.md` e o CI do app nativo ([#90](https://github.com/Piggubr/cofrinho-app/pull/90)).
- 26/10/2026: iOS nativo, etapa 1: login para apps nativos no backend, com o refresh no corpo da resposta e Entrar com Apple (D-072).
- 24/10/2026: login com Google no app iOS e Android pelo navegador do celular ([#79](https://github.com/Piggubr/cofrinho-app/pull/79)).
- 24/10/2026: dependências do `npm audit` atualizadas: undici, fast-uri e vitest ([#78](https://github.com/Piggubr/cofrinho-app/pull/78)).
- 24/10/2026: Sonar sem regex que volta atrás, topo do celular com cotação e tela Premium com o plano atual ([#68](https://github.com/Piggubr/cofrinho-app/pull/68)).
- 23/10/2026: deploy com imagens ARM para o servidor da Oracle ([#69](https://github.com/Piggubr/cofrinho-app/pull/69)).
- 23/10/2026: "Como pagou" não quebra mais ao escolher cartão de crédito ([#64](https://github.com/Piggubr/cofrinho-app/pull/64)).
- 23/10/2026: apontamentos do Sonar corrigidos e cobertura em cerca de 88% ([#66](https://github.com/Piggubr/cofrinho-app/pull/66)).
- 23/10/2026: CI/CD com lint, Sonar, CodeQL, Trivy, imagens no GHCR, rollback e versões ([#58](https://github.com/Piggubr/cofrinho-app/pull/58)).
- 22/10/2026: cotação na ordem escolhida, formulário completo no + e CPF único ([#62](https://github.com/Piggubr/cofrinho-app/pull/62)).
- 22/10/2026: nova categoria na lista, valor com vírgula e gasto mais recente em cima ([#59](https://github.com/Piggubr/cofrinho-app/pull/59)).
- 22/10/2026: plano casal e compartilhamento entre o casal e a família ([#57](https://github.com/Piggubr/cofrinho-app/pull/57)).
