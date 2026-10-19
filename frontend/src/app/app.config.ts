import { ApplicationConfig, ErrorHandler, isDevMode, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { Capacitor } from '@capacitor/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { routes } from './app.routes';
import { APP_CONFIG, appConfigPadrao } from './core/config/app-config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { INTERCEPTORES_DA_DEMO } from './demo/modo-demo';
import { ErrosDoApp } from './core/erros/erros-do-app';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Erro de JavaScript no aparelho vai para o painel "Erros e logs", sem identificar ninguem.
    { provide: ErrorHandler, useClass: ErrosDoApp },
    provideRouter(
      routes,
      withComponentInputBinding(),
      // "Personalizar o Painel" leva direto ao cartao no Perfil. Tela nova abre no topo;
      // o voltar devolve a pessoa onde ela estava.
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
    ),
    // Na demo (build "demo"), a API falsa responde antes de qualquer chamada sair.
    provideHttpClient(withInterceptors([...INTERCEPTORES_DA_DEMO, authInterceptor])),
    { provide: APP_CONFIG, useValue: appConfigPadrao },
    // Instalar como app e abrir sem sinal (PWA). No app iOS os arquivos ja vem no pacote.
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode() && !Capacitor.isNativePlatform(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
