import { HttpClient } from '@angular/common/http';
import { DOCUMENT, Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { ATALHOS_NATIVOS } from '../nativo/atalhos';

const CHAVE = 'piggu.google-no-app';
const GOOGLE = 'https://accounts.google.com/o/oauth2/v2/auth';

/** O que fica no aparelho enquanto a pessoa escolhe a conta no navegador. */
interface Pendente {
  verificador: string;
  lembrar: boolean;
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/** SHA-256 do verificador em base64url, como o S256 do PKCE; o backend refaz a conta. */
export async function desafioDe(verificador: string): Promise<string> {
  const resumo = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verificador));
  return base64url(new Uint8Array(resumo));
}

/**
 * Login com Google no app instalado (iOS e Android), que abre o site num WebView.
 *
 * <p>O botao do Google abre uma janela que precisa falar de volta com a pagina. No app essa
 * janela ia para o Safari (ou o Chrome), e depois de escolher a conta a tela ficava branca: o
 * Google nao deixa logar dentro de WebView, e o navegador nao tinha como voltar ao app. Aqui o
 * login vai para o navegador do celular e volta pelo link piggu://entrar?google=codigo, que o
 * plugin de atalhos transforma em /entrar?google=codigo.</p>
 *
 * <p>O verificador sorteado nunca sai do aparelho: o Google recebe so o SHA-256 dele (nonce e
 * state), e o backend so devolve o ID token a quem mostra o verificador.</p>
 */
@Injectable({ providedIn: 'root' })
export class GoogleNoApp {
  private readonly config = inject(APP_CONFIG);
  private readonly http = inject(HttpClient);
  private readonly documento = inject(DOCUMENT);

  /**
   * So no app que abre o site no ar e tem o plugin dos links piggu://. A demonstracao roda em
   * localhost, sem backend, e segue com o botao normal.
   */
  readonly disponivel =
    inject(ATALHOS_NATIVOS).disponivel && this.documento.location.hostname !== 'localhost';

  /** Abre o Google no navegador do celular: o app nao deixa sair do site, entao o sistema abre. */
  async abrir(lembrar: boolean): Promise<void> {
    const verificador = base64url(crypto.getRandomValues(new Uint8Array(32)));
    const desafio = await desafioDe(verificador);
    this.guardar({ verificador, lembrar });

    const endereco = new URL(GOOGLE);
    endereco.search = new URLSearchParams({
      client_id: this.config.googleClientId,
      redirect_uri: new URL(`${this.config.apiUrl}/auth/google/app`, this.documento.location.href)
        .href,
      response_type: 'id_token',
      response_mode: 'form_post',
      scope: 'openid email profile',
      nonce: desafio,
      state: desafio,
      prompt: 'select_account',
    }).toString();
    this.documento.location.assign(endereco.href);
  }

  /** De volta ao app: troca o codigo do link pelo ID token, uma vez so. */
  async concluir(codigo: string): Promise<{ idToken: string; lembrar: boolean }> {
    const pendente = this.retirar();
    if (!pendente) {
      throw new Error($localize`O login com Google venceu. Tente de novo.`);
    }
    const { idToken } = await firstValueFrom(
      this.http.post<{ idToken: string }>(`${this.config.apiUrl}/auth/google/app/troca`, {
        codigo,
        verificador: pendente.verificador,
      }),
    );
    return { idToken, lembrar: pendente.lembrar };
  }

  private guardar(pendente: Pendente): void {
    try {
      localStorage.setItem(CHAVE, JSON.stringify(pendente));
    } catch {
      // Sem armazenamento a volta falha com "venceu", e a pessoa entra por e-mail e senha.
    }
  }

  private retirar(): Pendente | null {
    try {
      const guardado = localStorage.getItem(CHAVE);
      localStorage.removeItem(CHAVE);
      const pendente = guardado ? (JSON.parse(guardado) as Partial<Pendente>) : null;
      return typeof pendente?.verificador === 'string'
        ? { verificador: pendente.verificador, lembrar: pendente.lembrar !== false }
        : null;
    } catch {
      return null;
    }
  }
}
