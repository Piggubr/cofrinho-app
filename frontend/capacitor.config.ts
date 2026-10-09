import type { CapacitorConfig } from '@capacitor/cli';

/**
 * App iOS do Piggu (Capacitor). Por enquanto empacota o build "demo" (dados de exemplo,
 * sem backend): npm run build:demo && npx cap sync ios. O IPA sai do workflow ios-demo.
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
