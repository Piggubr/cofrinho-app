import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

/** Rotas que nao levam token: sao justamente as que servem para obter um. */
const ROTAS_ABERTAS = ['/auth/google', '/auth/refresh', '/auth/logout'];

/**
 * Anexa o token em cada chamada e renova quando ele vence.
 *
 * <p>Um 401 dispara uma renovacao e a chamada e repetida uma unica vez. Se a
 * renovacao tambem falhar, a sessao acabou de verdade e o app volta para o login.</p>
 */
export function authInterceptor(
  pedido: HttpRequest<unknown>,
  proximo: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  const config = inject(APP_CONFIG);
  const storage = inject(TokenStorage);
  const auth = inject(AuthService);

  const paraNossaApi = pedido.url.startsWith(config.apiUrl);
  const rotaAberta = ROTAS_ABERTAS.some((rota) => pedido.url.includes(rota));

  if (!paraNossaApi || rotaAberta) {
    return proximo(pedido);
  }

  return proximo(comToken(pedido, storage.accessToken)).pipe(
    catchError((erro: unknown) => {
      if (!(erro instanceof HttpErrorResponse) || erro.status !== 401) {
        return throwError(() => erro);
      }

      return auth.renovar().pipe(
        switchMap((tokens) => proximo(comToken(pedido, tokens.accessToken))),
        catchError((falhaNaRenovacao: unknown) => {
          void auth.encerrarLocalmente();
          return throwError(() => falhaNaRenovacao);
        }),
      );
    }),
  );
}

function comToken(pedido: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) {
    return pedido;
  }
  return pedido.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
