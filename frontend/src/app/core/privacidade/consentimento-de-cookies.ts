import { Injectable, computed, signal } from '@angular/core';
import { Capacitor } from '@capacitor/core';

/** Muda quando entrar no site algo novo que dependa da escolha: a pergunta volta. */
export const VERSAO_DOS_COOKIES = '2026-10-19';

const CHAVE = 'piggu.cookies';

export interface EscolhaDeCookies {
  versao: string;
  /** Medir o uso do site (analise e desempenho); o cookie do login e essencial e nao entra aqui. */
  analise: boolean;
  em: string;
}

/**
 * Escolha de cookies do site (LGPD art. 7, I e 8).
 *
 * <p>O essencial (o cookie que mantem a pessoa conectada) nao depende de escolha. O resto
 * so liga com {@link analiseLiberada}: qualquer medicao de uso ou desempenho que grave
 * identificador no navegador ou mande dado de uso para fora espera esse sinal. Recusar e
 * tao facil quanto aceitar, e a escolha muda a qualquer hora pelo Aviso de Privacidade.</p>
 *
 * <p>No app instalado da loja nao ha banner: ali nao ha cookie de terceiros, e o pedido
 * de rastreamento, se um dia existir, e o do sistema.</p>
 */
@Injectable({ providedIn: 'root' })
export class ConsentimentoDeCookies {
  private readonly naWeb = !Capacitor.isNativePlatform();
  private readonly escolha = signal<EscolhaDeCookies | null>(lerEscolha());
  private readonly reaberto = signal(false);

  /** Mostrar o banner: na web, sem escolha valida, ou quando a pessoa pediu para mudar. */
  readonly perguntar = computed(() => this.naWeb && (this.escolha() === null || this.reaberto()));

  readonly analiseLiberada = computed(() => this.escolha()?.analise === true);

  escolher(analise: boolean): void {
    const escolha: EscolhaDeCookies = { versao: VERSAO_DOS_COOKIES, analise, em: new Date().toISOString() };
    this.escolha.set(escolha);
    this.reaberto.set(false);
    try {
      localStorage.setItem(CHAVE, JSON.stringify(escolha));
    } catch {
      // Sem armazenamento (aba privada): a escolha vale ate fechar a aba.
    }
  }

  /** Volta a mostrar o banner, para mudar a escolha. */
  reabrir(): void {
    this.reaberto.set(true);
  }
}

function lerEscolha(): EscolhaDeCookies | null {
  try {
    const salva = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as EscolhaDeCookies | null;
    return salva?.versao === VERSAO_DOS_COOKIES && typeof salva.analise === 'boolean' ? salva : null;
  } catch {
    return null;
  }
}
