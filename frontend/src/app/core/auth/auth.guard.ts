import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
import { PigguRole } from '../api/models';
import { AuthService } from './auth.service';

/** Barra quem nao tem sessao, tentando antes recuperar a que estava guardada. */
export const autenticadoGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.autenticado()) {
    return true;
  }

  const restaurada = await auth.restaurarSessao();
  return restaurada ? true : router.createUrlTree(['/entrar']);
};

/** Termos novos ainda nao aceitos: so passa pela tela de aceite. */
export const termosGuard: CanActivateFn & CanActivateChildFn = () => {
  const auth = inject(AuthService);
  return auth.termosPendentes() ? inject(Router).createUrlTree(['/aceite']) : true;
};

/**
 * Restringe uma rota a perfis especificos.
 *
 * <p>Isto e conveniencia de navegacao, nao seguranca: quem decide de verdade e o
 * backend, que recusa a chamada com 403 mesmo se a tela abrir.</p>
 */
export function perfilGuard(...perfis: PigguRole[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.temPerfil(...perfis) ? true : router.createUrlTree(['/painel']);
  };
}
