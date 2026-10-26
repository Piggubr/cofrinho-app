import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';

/** Rotas que nao levam token: sao justamente as que servem para obter um. */
const ROTAS_ABERTAS = [
  '/auth/google',
  '/auth/entrar',
  '/auth/cadastro',
  '/auth/refresh',
  '/auth/logout',
];

/**
 * Anexa o token em cada chamada e renova quando ele vence.
 *
 * <p>Um 401 dispara uma renovacao e a chamada e repetida uma unica vez. Se o servidor
 * recusar a renovacao (401 ou 403), a sessao acabou de verdade e o app volta para o login.</p>
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

      // So a renovacao recusada encerra a sessao; o erro da chamada repetida segue para quem chamou.
      return auth.renovar().pipe(
        catchError((falhaNaRenovacao: unknown) => {
          // Sem sinal ou servidor fora nao e sessao vencida: o app segue com o perfil guardado.
          if (sessaoRecusada(falhaNaRenovacao)) {
            void auth.encerrarLocalmente();
          }
          return throwError(() => falhaNaRenovacao);
        }),
        switchMap((tokens) => proximo(comToken(pedido, tokens.accessToken))),
      );
    }),
  );
}

function sessaoRecusada(erro: unknown): boolean {
  return erro instanceof HttpErrorResponse && (erro.status === 401 || erro.status === 403);
}

function comToken(pedido: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) {
    return pedido;
  }
  return pedido.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
