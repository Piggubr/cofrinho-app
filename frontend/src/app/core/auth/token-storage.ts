import { Injectable } from '@angular/core';
import { ParDeTokens } from '../api/models';

/** Chaves que versoes antigas do app usavam no localStorage/sessionStorage. */
const CHAVES_ANTIGAS = ['piggu_access_token', 'piggu_refresh_token'];

/**
 * Guarda o access token so em memoria.
 *
 * <p>O refresh nao passa pelo JavaScript: o identity o entrega num cookie HttpOnly;
 * Secure; SameSite=Strict; Path=/api/auth, que o navegador manda sozinho na renovacao.
 * Um XSS nao consegue ler nem levar a sessao de 30 dias. O access token, de 30 minutos,
 * some ao recarregar a pagina, e a renovacao pelo cookie devolve um novo.</p>
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private access: string | null = null;

  constructor() {
    // Quem usou uma versao antiga ainda tem tokens no armazenamento do navegador.
    for (const chave of CHAVES_ANTIGAS) {
      try {
        localStorage.removeItem(chave);
        sessionStorage.removeItem(chave);
      } catch {
        // Armazenamento bloqueado: nao ha o que limpar.
      }
    }
  }

  guardar(tokens: ParDeTokens): void {
    this.access = tokens.accessToken;
  }

  get accessToken(): string | null {
    return this.access;
  }

  limpar(): void {
    this.access = null;
  }
}
