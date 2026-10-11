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
    // O conteudo comeca abaixo da barra de status e acima da barra de gestos.
    contentInset: 'always',
  },
};

export default config;
