import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { routes } from './app.routes';
import { APP_CONFIG, appConfigPadrao } from './core/config/app-config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { INTERCEPTORES_DA_DEMO } from './demo/modo-demo';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Na demo (build "demo"), a API falsa responde antes de qualquer chamada sair.
    provideHttpClient(withInterceptors([...INTERCEPTORES_DA_DEMO, authInterceptor])),
    { provide: APP_CONFIG, useValue: appConfigPadrao },
  ],
};
