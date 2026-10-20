import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { AuthService } from '../auth/auth.service';
import { marcarQueVoltouAoPadrao } from '../ui/tema';

export type IdDoIcone =
  | 'padrao'
  | 'classico'
  | 'monograma'
  | 'silhueta'
  | 'azul'
  | 'menta'
  | 'lilas'
  | 'coral'
  | 'mel'
  | 'vinho'
  | 'grafite'
  | 'namorados';

export interface IconeDoApp {
  id: IdDoIcone;
  nome: string;
  /** Previa em public/icones-do-app, a mesma arte do icone de verdade. */
  previa: string;
  /** O padrao e o classico sao gratis; os outros sao do Premium. */
  premium: boolean;
}

/** O padrao e o Porquinho Neon; o Classico e o porquinho rosa claro de antes. */
export const ICONES_DO_APP: readonly IconeDoApp[] = [
  { id: 'padrao', nome: $localize`Neon`, previa: 'icones-do-app/padrao.png', premium: false },
  {
    id: 'classico',
    nome: $localize`Clássico`,
    previa: 'icones-do-app/classico.png',
    premium: false,
  },
  {
    id: 'monograma',
    nome: $localize`P de Piggu`,
    previa: 'icones-do-app/monograma.png',
    premium: true,
  },
  {
    id: 'silhueta',
    nome: $localize`Silhueta`,
    previa: 'icones-do-app/silhueta.png',
    premium: true,
  },
  { id: 'azul', nome: $localize`Azul`, previa: 'icones-do-app/azul.png', premium: true },
  { id: 'menta', nome: $localize`Menta`, previa: 'icones-do-app/menta.png', premium: true },
  { id: 'lilas', nome: $localize`Lilás`, previa: 'icones-do-app/lilas.png', premium: true },
  { id: 'coral', nome: $localize`Coral`, previa: 'icones-do-app/coral.png', premium: true },
  { id: 'mel', nome: $localize`Mel`, previa: 'icones-do-app/mel.png', premium: true },
  { id: 'vinho', nome: $localize`Vinho`, previa: 'icones-do-app/vinho.png', premium: true },
  { id: 'grafite', nome: $localize`Grafite`, previa: 'icones-do-app/grafite.png', premium: true },
  {
    id: 'namorados',
    nome: $localize`Namorados`,
    previa: 'icones-do-app/namorados.png',
    premium: true,
  },
];

/**
 * Contrato dos plugins IconeDoAppPlugin.swift e IconeDoAppPlugin.java. O nome e o id do
 * icone; null volta ao padrao.
 */
interface PluginIconeDoApp {
  atual(): Promise<{ nome: string | null }>;
  trocar(opcoes: { nome: string | null }): Promise<void>;
}

const NOME_DO_PLUGIN = 'IconeDoApp';

function ehPremium(id: IdDoIcone): boolean {
  return ICONES_DO_APP.find((icone) => icone.id === id)?.premium ?? true;
}

function idValido(nome: string | null): IdDoIcone {
  return ICONES_DO_APP.find((icone) => icone.id === nome)?.id ?? 'padrao';
}

/**
 * Icone do app na tela inicial (Premium). So existe no app do celular: no navegador
 * {@link disponivel} e falso e o Perfil nem mostra a escolha.
 *
 * <p>No iPhone cada icone tem a versao escura, que o proprio sistema usa no modo escuro
 * (iOS 18 ou mais novo), e o sistema mostra um aviso a cada troca. Quando o Premium acaba,
 * um icone do Premium volta ao padrao; o Classico fica.</p>
 */
@Injectable({ providedIn: 'root' })
export class IconesDoApp {
  private readonly auth = inject(AuthService);
  private readonly plugin = registerPlugin<PluginIconeDoApp>(NOME_DO_PLUGIN);

  readonly disponivel = Capacitor.isNativePlatform() && Capacitor.isPluginAvailable(NOME_DO_PLUGIN);
  readonly atual = signal<IdDoIcone>('padrao');
  /** So depois de ler o icone do aparelho da para saber se precisa voltar ao padrao. */
  private readonly lido = signal(false);

  constructor() {
    if (!this.disponivel) {
      return;
    }
    this.plugin
      .atual()
      .then(({ nome }) => this.atual.set(idValido(nome)))
      .catch((erro) => console.warn('Ícone do app:', erro))
      .finally(() => this.lido.set(true));

    effect(() => {
      if (
        this.lido() &&
        this.auth.autenticado() &&
        !this.auth.ehPremium() &&
        ehPremium(this.atual())
      ) {
        untracked(() => {
          void this.aplicar('padrao');
          marcarQueVoltouAoPadrao();
        });
      }
    });
  }

  /** Falso quando a pessoa nao tem Premium: a tela leva para os planos. */
  async escolher(id: IdDoIcone): Promise<boolean> {
    if (ehPremium(id) && !this.auth.ehPremium()) {
      return false;
    }
    await this.aplicar(id);
    return true;
  }

  private async aplicar(id: IdDoIcone): Promise<void> {
    if (!this.disponivel || id === this.atual()) {
      return;
    }
    try {
      await this.plugin.trocar({ nome: id === 'padrao' ? null : id });
      this.atual.set(id);
    } catch (erro) {
      // Pessoa recusou ou o sistema nao deixou agora: o icone fica como estava.
      console.warn('Ícone do app:', erro);
    }
  }
}
