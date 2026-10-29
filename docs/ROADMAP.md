# Roadmap do Piggu

Última atualização: 27/10/2026.

**Regra:** concluiu um item? Atualize este arquivo no mesmo PR que o conclui.

Este arquivo é o resumo. Os detalhes, as etapas e os prompts ficam nos planos:

- [Plano do app nativo iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y) (etapas 0 a 9)
- [Plano do app nativo Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G) (etapas A0 a A9)
- [Planos Premium](planos-premium.md)

## Agora

- Android nativo, etapa A0: criar `android-nativo/` com módulos, tema, CI e `CLAUDE.md` ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).
- Google Play: abrir a conta e começar o teste fechado com 12 pessoas por 14 dias, que conta pessoal nova precisa antes de publicar ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).
- Documentos do repositório: `docs/API_SPEC.md`, `docs/DATA_MODEL.md` e `docs/DECISIONS.md`, nas branches `docs/contrato-da-api`, `docs/modelo-de-dados` e `docs/decisoes`.

## Depois (próximos 1 a 2 meses)

- Entrar com Apple: pôr o Bundle ID em `APPLE_CLIENT_IDS` no servidor e revogar o token na Apple quando a conta é apagada, que a App Store exige antes da revisão (D-072).
- Abrir um grupo "Nativo" no TestFlight, agora que a etapa 4 do iOS está pronta.
- App Store Connect: grupo "Piggu Premium" com os produtos `br.com.piggu.premium.mensal` e `.anual`, preços, notificações V2 em `/api/billing/webhooks/app-store` e `APP_STORE_APP_APPLE_ID` no servidor (D-094).
- Mandar o 2.0.0 (build 2) do iOS nativo ao TestFlight e liberar para o grupo de testes, quando o Dudu pedir.
- Medir a abertura do 2.0.0 num iPhone de verdade (Instruments, App Launch): a meta é menos de 1 segundo até o Painel com cache (D-095).
- Abrir o acesso fechado (Cloudflare Access) antes de mandar o app para a revisão da Apple.
- Android, etapa A1: backend para Android (push pelo Firebase e ID do Google do Android).
- Android, etapas A2 a A7: as telas, na mesma ordem do iOS.
- Android, etapa A8: Premium pelo Google Play Billing, com validação no backend.
- Android, etapa A9: acabamento, versão 2.0.0 e teste interno no Play.
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

- 28/10/2026: iOS nativo, ajustes dos testers: o mês desce junto ao puxar para recarregar, as barras por categoria num desenho só e os vazios dos cartões do Painel centralizados (D-099).
- 28/10/2026: iOS nativo: o + abre as opções, como no site, e lança receita de qualquer aba (D-098).
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
