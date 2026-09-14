import { Injectable } from '@angular/core';
import { ParDeTokens } from '../api/models';

const CHAVE_ACCESS = 'piggu_access_token';
const CHAVE_REFRESH = 'piggu_refresh_token';

/**
 * Guarda os tokens no navegador.
 *
 * <p>Onde guardar depende de o usuario ter pedido para continuar conectado:
 * localStorage sobrevive a fechar o navegador, sessionStorage some junto com a aba.
 * O app antigo ja oferecia essa escolha na tela de login, com a caixa Lembrar acesso.</p>
 *
 * <p>Toda leitura e escrita esta protegida: em janela anonima, com dados de site
 * bloqueados, o proprio acesso ao storage lanca excecao.</p>
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private memoria: { access?: string; refresh?: string } = {};

  guardar(tokens: ParDeTokens, lembrar: boolean): void {
    this.limpar();
    this.memoria = { access: tokens.accessToken, refresh: tokens.refreshToken };

    const destino = lembrar ? this.local() : this.sessao();
    this.tentar(() => {
      destino?.setItem(CHAVE_ACCESS, tokens.accessToken);
      destino?.setItem(CHAVE_REFRESH, tokens.refreshToken);
    });
  }

  /** Troca apenas o par de tokens, preservando onde eles ja estavam guardados. */
  atualizar(tokens: ParDeTokens): void {
    const lembrado = this.tentar(() => this.local()?.getItem(CHAVE_REFRESH)) != null;
    this.guardar(tokens, lembrado);
  }

  get accessToken(): string | null {
    return this.memoria.access ?? this.ler(CHAVE_ACCESS);
  }

  get refreshToken(): string | null {
    return this.memoria.refresh ?? this.ler(CHAVE_REFRESH);
  }

  limpar(): void {
    this.memoria = {};
    this.tentar(() => {
      this.local()?.removeItem(CHAVE_ACCESS);
      this.local()?.removeItem(CHAVE_REFRESH);
      this.sessao()?.removeItem(CHAVE_ACCESS);
      this.sessao()?.removeItem(CHAVE_REFRESH);
    });
  }

  private ler(chave: string): string | null {
    return this.tentar(() => this.local()?.getItem(chave) ?? this.sessao()?.getItem(chave)) ?? null;
  }

  private local(): Storage | null {
    return typeof localStorage === 'undefined' ? null : localStorage;
  }

  private sessao(): Storage | null {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage;
  }

  private tentar<T>(acao: () => T): T | null {
    try {
      return acao();
    } catch {
      // Armazenamento bloqueado: o app continua funcionando so nesta aba.
      return null;
    }
  }
}
