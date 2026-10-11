import { Injectable, InjectionToken, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';

interface PluginAtalhos {
  pendente(): Promise<{ rota?: string | null }>;
  addListener(
    evento: 'abrir',
    ouvinte: (dados: { rota: string }) => void,
  ): Promise<PluginListenerHandle>;
}

/** O plugin do projeto (iOS e Android) e se ele existe aqui; os testes trocam pelo falso. */
export const ATALHOS_NATIVOS = new InjectionToken('atalhos nativos', {
  providedIn: 'root',
  factory: () => ({
    disponivel: Capacitor.isNativePlatform() && Capacitor.isPluginAvailable('Atalhos'),
    plugin: registerPlugin<PluginAtalhos>('Atalhos'),
  }),
});

/** So caminho interno do app: nada de endereco de fora vindo de um link. */
function rotaInterna(rota: string | null | undefined): rota is string {
  return !!rota && rota.startsWith('/') && !rota.startsWith('//');
}

/**
 * Atalhos do app instalado: "Lançar gasto" no toque longo do ícone, a Siri e os links piggu://.
 *
 * <p>O lado nativo guarda a rota quando o app abre pelo atalho e manda pelo evento "abrir"
 * quando ele ja esta aberto; aqui ela vira navegacao.</p>
 */
@Injectable({ providedIn: 'root' })
export class Atalhos {
  private readonly router = inject(Router);
  private readonly nativo = inject(ATALHOS_NATIVOS);
  private ligado = false;

  async ligar(): Promise<void> {
    if (!this.nativo.disponivel || this.ligado) {
      return;
    }
    this.ligado = true;
    await this.nativo.plugin.addListener('abrir', ({ rota }) => this.abrir(rota));
    const { rota } = await this.nativo.plugin.pendente();
    this.abrir(rota);
  }

  private abrir(rota: string | null | undefined): void {
    if (rotaInterna(rota)) {
      void this.router.navigateByUrl(rota);
    }
  }
}
