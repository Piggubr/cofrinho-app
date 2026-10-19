import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { Capacitor } from '@capacitor/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  NavigationError,
  RedirectCommand,
  Router,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withNavigationErrorHandler,
} from '@angular/router';
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
    provideRouter(
      routes,
      withComponentInputBinding(),
      // "Personalizar o Painel" leva direto ao cartao no Perfil. Tela nova abre no topo;
      // o voltar devolve a pessoa onde ela estava.
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
      // Tela que nao abriu (arquivo dela nao chegou ou erro ao montar): mostra a 500 sem
      // trocar o endereco, e guarda o destino para o "Tentar de novo".
      withNavigationErrorHandler((falha: NavigationError) => {
        console.error(falha.error);
        const router = inject(Router);
        return new RedirectCommand(router.parseUrl('/erro'), {
          skipLocationChange: true,
          state: { destino: falha.url },
        });
      }),
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
