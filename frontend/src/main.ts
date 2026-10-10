import { bootstrapApplication } from '@angular/platform-browser';
import { Capacitor } from '@capacitor/core';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// No app iOS o zoom de pinça nao faz sentido e o WebView as vezes deixa a tela
// ampliada depois que o teclado fecha. No navegador o zoom continua liberado.
if (Capacitor.isNativePlatform()) {
  document
    .querySelector('meta[name="viewport"]')
    ?.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover');
}

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
