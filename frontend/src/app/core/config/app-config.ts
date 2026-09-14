import { InjectionToken } from '@angular/core';

/**
 * Configuracao que muda entre ambientes.
 *
 * <p>Fica em um token injetavel, e nao em um arquivo de environment, para que os
 * testes possam trocar os valores sem recompilar e para que o mesmo build sirva a
 * mais de um ambiente.</p>
 */
export interface AppConfig {
  /** Raiz da API. Em desenvolvimento o proxy do Angular encaminha para o gateway. */
  readonly apiUrl: string;
  /** ID OAuth do Google usado no botao de login. */
  readonly googleClientId: string;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

export const appConfigPadrao: AppConfig = {
  apiUrl: '/api',
  googleClientId: '532290439779-olhgd7m1hssj4o30gjs3ngrcs7ga48aa.apps.googleusercontent.com',
};
