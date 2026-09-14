import { Injectable, inject } from '@angular/core';
import { APP_CONFIG } from '../config/app-config';

/** Recorte minimo da API do Google Identity Services que este app usa. */
interface GoogleAccounts {
  id: {
    initialize(opcoes: {
      client_id: string;
      callback: (resposta: { credential: string }) => void;
      auto_select?: boolean;
      cancel_on_tap_outside?: boolean;
    }): void;
    renderButton(elemento: HTMLElement, opcoes: Record<string, unknown>): void;
    prompt(): void;
    disableAutoSelect(): void;
  };
}

declare global {
  interface Window {
    google?: { accounts: GoogleAccounts };
  }
}

const URL_SCRIPT = 'https://accounts.google.com/gsi/client';

/**
 * Carrega e opera o botao de login do Google.
 *
 * <p>O script e carregado sob demanda, uma vez so: colocar a tag no index.html faria
 * todo mundo baixar o cliente do Google mesmo ja estando autenticado.</p>
 */
@Injectable({ providedIn: 'root' })
export class GoogleIdentityService {
  private readonly config = inject(APP_CONFIG);
  private carregamento?: Promise<GoogleAccounts>;

  /**
   * Desenha o botao do Google no elemento informado.
   *
   * @param aoReceberCredencial recebe o ID token que o backend vai conferir
   */
  async renderizarBotao(
    destino: HTMLElement,
    aoReceberCredencial: (idToken: string) => void,
  ): Promise<void> {
    const accounts = await this.carregar();

    accounts.id.initialize({
      client_id: this.config.googleClientId,
      callback: (resposta) => aoReceberCredencial(resposta.credential),
      auto_select: false,
      cancel_on_tap_outside: true,
    });

    destino.replaceChildren();
    accounts.id.renderButton(destino, {
      theme: 'outline',
      size: 'large',
      text: 'signin_with',
      shape: 'pill',
      locale: 'pt-BR',
      width: 260,
    });
  }

  /** Impede o Google de reentrar sozinho depois de o usuario sair. */
  async esquecerConta(): Promise<void> {
    const accounts = await this.carregar().catch(() => null);
    accounts?.id.disableAutoSelect();
  }

  private carregar(): Promise<GoogleAccounts> {
    if (this.carregamento) {
      return this.carregamento;
    }

    this.carregamento = new Promise<GoogleAccounts>((resolver, rejeitar) => {
      if (window.google?.accounts) {
        resolver(window.google.accounts);
        return;
      }

      const script = document.createElement('script');
      script.src = URL_SCRIPT;
      script.async = true;
      script.defer = true;
      script.onload = () => {
        if (window.google?.accounts) {
          resolver(window.google.accounts);
        } else {
          rejeitar(new Error('O cliente do Google carregou incompleto.'));
        }
      };
      script.onerror = () => rejeitar(new Error('Nao consegui carregar o login do Google.'));
      document.head.appendChild(script);
    });

    return this.carregamento;
  }
}
