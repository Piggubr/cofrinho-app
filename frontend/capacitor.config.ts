import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Apps iOS e Android do Piggu (Capacitor). Por enquanto empacotam o build "demo" (dados de
 * exemplo, sem backend): npm run build:demo && npx cap sync. O IPA sai do workflow ios-demo
 * e o APK do android-demo.
 */
const config: CapacitorConfig = {
  appId: 'br.com.piggu.app',
  appName: 'Piggu',
  webDir: 'dist/demo/browser',
  ios: {
    // A pagina ocupa a tela inteira, por baixo da barra de status e da barra de gestos: o
    // vidro do cabecalho sobe ate o alto (sem faixa preta) e o CSS reserva as areas
    // seguras com env(safe-area-inset-*). Com 'always' o iOS somava a barra de gestos as
    // alturas que o CSS ja descontava, e a folha do "Mais" ficava alta demais.
    contentInset: 'never',
  },
};

export default config;
