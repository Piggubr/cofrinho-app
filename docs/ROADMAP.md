# Roadmap do Piggu

Última atualização: 27/10/2026.

**Regra:** concluiu um item? Atualize este arquivo no mesmo PR que o conclui.

Este arquivo é o resumo. Os detalhes, as etapas e os prompts ficam nos planos:

- [Plano do app nativo iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y) (etapas 0 a 9)
- [Plano do app nativo Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G) (etapas A0 a A9)
- [Planos Premium](planos-premium.md)

## Agora

- iOS nativo, etapa 0: levar a branch `swift/inicio` do Mac para o GitHub, com o CI do app nativo ([plano iOS](https://claude.ai/artifact/EsG2Vns9DSeAbHqWJTDD3Y)).
- Google Play: abrir a conta e começar o teste fechado com 12 pessoas por 14 dias, que conta pessoal nova precisa antes de publicar ([plano Android](https://claude.ai/artifact/K5Cy7Xw1w3L71bEGpGzR9G)).
- Documentos do repositório: `docs/API_SPEC.md`, `docs/DATA_MODEL.md` e `docs/DECISIONS.md`, nas branches `docs/contrato-da-api`, `docs/modelo-de-dados` e `docs/decisoes`.

## Depois (próximos 1 a 2 meses)

- iOS, etapa 2: fundação e visual (cores, Sora, componentes de vidro, abas, telas de vazio, erro e sem internet).
- iOS, etapa 3: entrada completa (Google, Apple, criar conta, CPF, termos, esqueci a senha).
- Entrar com Apple: pôr o Bundle ID em `APPLE_CLIENT_IDS` no servidor e revogar o token na Apple quando a conta é apagada, que a App Store exige antes da revisão (D-072).
- iOS, etapa 4: dia a dia (Painel, Gastos e Receitas). Depois dela já dá para abrir um grupo "Nativo" no TestFlight.
- iOS, etapa 5: organização (contas fixas, orçamentos, calendário, relatórios, contas e cartões, com o "Marcar como paga" da fatura, D-085).
- iOS, etapa 6: metas e conta (metas, cofrinho, Girls Math, prêmios, família, perfil).
- iOS, etapa 7: extras e recursos da Apple (widgets, Siri, Face ID, push, leitura de nota).
- iOS, etapa 8: Premium pelo StoreKit 2, com validação no backend e o preço no iPhone decidido.
- iOS, etapa 9: acabamento e versão 2.0.0 no TestFlight. Pelo plano, de 4 a 5 semanas com uma sessão por dia.
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

- 27/10/2026: Android nativo, etapa A0: `android-nativo/` com módulos, tema do Piggu, abas e CI, na branch `feature/android-nativo` ([#106](https://github.com/Piggubr/cofrinho-app/pull/106)).
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
