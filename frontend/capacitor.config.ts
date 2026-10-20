import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Apps iOS e Android do Piggu (Capacitor). Dois jeitos de empacotar:
 *
 * - demo (padrao): npm run build:demo && npx cap sync. Dados de exemplo, sem backend. O IPA
 *   sai do workflow ios-demo e o APK do android-demo.
 * - producao: PIGGU_SITE=https://piggu.com.br npm run build && npx cap sync. O app abre o
 *   site no ar dentro dele, com a conta e os dados reais, e acompanha cada deploy sem
 *   gerar outro IPA. O IPA sai do workflow ios-producao.
 */
const site = process.env['PIGGU_SITE'];

const config: CapacitorConfig = {
  appId: 'br.com.piggu.app',
  appName: 'Piggu',
  webDir: site ? 'dist/frontend/browser' : 'dist/demo/browser',
  ios: {
    // A pagina ocupa a tela inteira, por baixo da barra de status e da barra de gestos: o
    // vidro do cabecalho sobe ate o alto (sem faixa preta) e o CSS reserva as areas
    // seguras com env(safe-area-inset-*). Com 'always' o iOS somava a barra de gestos as
    // alturas que o CSS ja descontava, e a folha do "Mais" ficava alta demais.
    contentInset: 'never',
  },
  ...(site && {
    server: {
      url: site,
      // O alpha fica atras do Cloudflare Access: o login dele (codigo por e-mail) precisa
      // abrir dentro do app, e nao no Safari, para o cookie do Access valer aqui.
      allowNavigation: [new URL(site).hostname, '*.cloudflareaccess.com'],
    },
  }),
};

export default config;
