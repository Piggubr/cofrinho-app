import { Injectable, computed, inject, linkedSignal } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export type IdDoBloco =
  | 'resumo'
  | 'avisos'
  | 'metas'
  | 'cofrinho'
  | 'objetivos'
  | 'mes'
  | 'girls-math'
  | 'notas'
  | 'categorias';

export interface BlocoDoPainel {
  readonly id: IdDoBloco;
  readonly visivel: boolean;
}

/** Nome de cada bloco na tela de organizar, na ordem padrao do Painel. */
export const BLOCOS_DO_PAINEL: readonly { readonly id: IdDoBloco; readonly nome: string }[] = [
  { id: 'resumo', nome: $localize`Resumo do mês` },
  { id: 'avisos', nome: $localize`Para ficar de olho` },
  { id: 'metas', nome: $localize`Minhas metas` },
  { id: 'cofrinho', nome: $localize`Cofrinho` },
  { id: 'objetivos', nome: $localize`Cofrinhos por objetivo` },
  { id: 'mes', nome: $localize`Gasto e limite do mês` },
  { id: 'girls-math', nome: $localize`Girls Math` },
  { id: 'notas', nome: $localize`Suas notas` },
  { id: 'categorias', nome: $localize`Por categoria` },
];

const PADRAO: BlocoDoPainel[] = BLOCOS_DO_PAINEL.map(({ id }) => ({ id, visivel: true }));

/**
 * Junta o que estava salvo com a lista atual: bloco que deixou de existir sai, bloco
 * novo entra no fim e visivel. Assim uma versao nova do app nunca esconde nada.
 */
export function ordemValida(salva: unknown): BlocoDoPainel[] {
  if (!Array.isArray(salva)) {
    return PADRAO;
  }
  const conhecidos = new Set<string>(PADRAO.map((b) => b.id));
  const vistos = new Set<string>();
  const blocos: BlocoDoPainel[] = [];
  for (const item of salva) {
    const id = (item as BlocoDoPainel)?.id;
    if (conhecidos.has(id) && !vistos.has(id)) {
      vistos.add(id);
      blocos.push({ id, visivel: (item as BlocoDoPainel).visivel !== false });
    }
  }
  return [...blocos, ...PADRAO.filter((b) => !vistos.has(b.id))];
}

/**
 * Ordem e visibilidade dos blocos do Painel, escolhidas no Perfil.
 *
 * <p>Fica neste aparelho e por pessoa, como o tema: o celular pode ter uma ordem e o
 * computador outra. O guia de primeiros passos fica sempre no topo e nao entra aqui.</p>
 */
@Injectable({ providedIn: 'root' })
export class PainelOrganizado {
  private readonly auth = inject(AuthService);

  private readonly chave = computed(() => `piggu_painel_${this.auth.usuario()?.id ?? 'anonimo'}`);

  /** Relido quando a pessoa logada muda. */
  readonly blocos = linkedSignal<BlocoDoPainel[]>(() => ler(this.chave()));

  /** Posicao para o CSS order; bloco escondido devolve null. */
  readonly posicoes = computed(() => {
    const mapa = new Map<IdDoBloco, number>();
    this.blocos().forEach((bloco, indice) => {
      if (bloco.visivel) {
        mapa.set(bloco.id, indice);
      }
    });
    return mapa;
  });

  mover(id: IdDoBloco, passo: -1 | 1): void {
    const blocos = [...this.blocos()];
    const de = blocos.findIndex((b) => b.id === id);
    const para = de + passo;
    if (de < 0 || para < 0 || para >= blocos.length) {
      return;
    }
    [blocos[de], blocos[para]] = [blocos[para], blocos[de]];
    this.gravar(blocos);
  }

  alternar(id: IdDoBloco): void {
    this.gravar(this.blocos().map((b) => (b.id === id ? { ...b, visivel: !b.visivel } : b)));
  }

  restaurar(): void {
    this.gravar(PADRAO);
  }

  private gravar(blocos: BlocoDoPainel[]): void {
    this.blocos.set(blocos);
    try {
      localStorage.setItem(this.chave(), JSON.stringify(blocos));
    } catch {
      // Sem armazenamento (aba privada): vale ate fechar a aba.
    }
  }
}

function ler(chave: string): BlocoDoPainel[] {
  try {
    const texto = localStorage.getItem(chave);
    return ordemValida(texto ? JSON.parse(texto) : null);
  } catch {
    return PADRAO;
  }
}
