import { Injectable, inject } from '@angular/core';
import { Capacitor, registerPlugin, type PluginListenerHandle } from '@capacitor/core';
import { Temas } from '../ui/tema';

/** Uma aba da barra nativa. O simbolo e um nome de SF Symbol ("house", "chart.bar"...). */
export interface AbaNativa {
  readonly id: string;
  readonly titulo: string;
  readonly simbolo: string;
  /** Aba que so dispara uma acao (o "+"): depois do toque a marcacao volta para a tela atual. */
  readonly acao?: boolean;
}

/** Contrato do plugin Swift em ios/App/App/AbasNativasPlugin.swift. */
interface PluginAbasNativas {
  configurar(opcoes: {
    abas: readonly AbaNativa[];
    selecionada?: string | null;
    corDestaque?: string;
    corDestaqueEscura?: string;
  }): Promise<void>;
  selecionar(opcoes: { id: string | null }): Promise<void>;
  mostrar(): Promise<void>;
  esconder(): Promise<void>;
  addListener(
    evento: 'abaTocada',
    aoTocar: (dados: { id: string }) => void,
  ): Promise<PluginListenerHandle>;
  addListener(
    evento: 'alturaMudou',
    aoMudar: (dados: { altura: number }) => void,
  ): Promise<PluginListenerHandle>;
}

const NOME_DO_PLUGIN = 'AbasNativas';

/**
 * Barra de abas nativa do app iOS (UITabBar, com Liquid Glass a partir do iOS 26).
 *
 * <p>So existe no app iOS com o plugin compilado. No navegador, no computador ou num build
 * antigo do app, {@link disponivel} e falso e o shell segue com a barra HTML de sempre.</p>
 */
@Injectable({ providedIn: 'root' })
export class AbasNativas {
  private readonly plugin = registerPlugin<PluginAbasNativas>(NOME_DO_PLUGIN);
  private readonly temas = inject(Temas);

  readonly disponivel =
    Capacitor.getPlatform() === 'ios' && Capacitor.isPluginAvailable(NOME_DO_PLUGIN);

  configurar(abas: readonly AbaNativa[], selecionada: string | null): void {
    this.chamar(() =>
      this.plugin.configurar({
        abas,
        selecionada,
        // Le o tema dentro do effect do shell: trocar a cor no Perfil refaz a barra.
        corDestaque: this.temas.atual().destaque,
        corDestaqueEscura: this.temas.atual().destaqueEscuro,
      }),
    );
  }

  selecionar(id: string | null): void {
    this.chamar(() => this.plugin.selecionar({ id }));
  }

  /**
   * Mostra a barra so na largura de celular. No iPad largo o menu vira a coluna lateral,
   * como no computador, e a barra sai de cena. Devolve a funcao que para de acompanhar.
   */
  mostrarSoEmTelaEstreita(): () => void {
    const telaEstreita = matchMedia('(max-width: 899px)');
    const aplicar = () =>
      this.chamar(() => (telaEstreita.matches ? this.plugin.mostrar() : this.plugin.esconder()));
    aplicar();
    telaEstreita.addEventListener('change', aplicar);
    return () => telaEstreita.removeEventListener('change', aplicar);
  }

  /**
   * Avisa a cada toque e mantem a variavel CSS --abas-nativas-altura com o espaco que a barra
   * ocupa embaixo. Devolve a funcao que desfaz as duas coisas.
   */
  ouvir(aoTocar: (id: string) => void): () => void {
    const ouvintes = [
      this.plugin.addListener('abaTocada', ({ id }) => aoTocar(id)),
      this.plugin.addListener('alturaMudou', ({ altura }) =>
        document.documentElement.style.setProperty('--abas-nativas-altura', `${altura}px`),
      ),
    ];
    return () => ouvintes.forEach((ouvinte) => void ouvinte.then((h) => h.remove()));
  }

  /** Falha do lado nativo nao derruba a tela: no pior caso a barra fica como estava. */
  private chamar(acao: () => Promise<void>): void {
    acao().catch((erro) => console.warn('Abas nativas:', erro));
  }
}
