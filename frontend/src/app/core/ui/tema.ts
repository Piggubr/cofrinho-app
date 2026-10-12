import { Injectable, signal } from '@angular/core';

export type IdDoTema = 'rosa' | 'menta' | 'lilas' | 'azul';

export interface Tema {
  id: IdDoTema;
  nome: string;
  descricao: string;
  /** Cor de destaque no tema claro e no escuro (abas nativas do iOS e amostra). */
  destaque: string;
  destaqueEscuro: string;
  /** Fundo da pagina, para a barra do navegador (meta theme-color). */
  fundo: string;
  fundoEscuro: string;
}

/** Os mesmos quatro temas do app antigo. As cores de cada um ficam no styles.scss. */
export const TEMAS: readonly Tema[] = [
  {
    id: 'rosa',
    nome: $localize`Rosa Piggu`,
    descricao: $localize`Delicado e acolhedor`,
    destaque: '#8e2f58',
    destaqueEscuro: '#f0a6c4',
    fundo: '#fcf8f9',
    fundoEscuro: '#161015',
  },
  {
    id: 'menta',
    nome: $localize`Menta`,
    descricao: $localize`Leve e tranquilo`,
    destaque: '#1f6b5a',
    destaqueEscuro: '#9fe0c8',
    fundo: '#f7fbfa',
    fundoEscuro: '#0f1513',
  },
  {
    id: 'lilas',
    nome: $localize`Lilás`,
    descricao: $localize`Calmo e criativo`,
    destaque: '#6a3d94',
    destaqueEscuro: '#d2b8f2',
    fundo: '#faf8fc',
    fundoEscuro: '#131017',
  },
  {
    id: 'azul',
    nome: $localize`Azul`,
    descricao: $localize`Sereno e organizado`,
    destaque: '#22608f',
    destaqueEscuro: '#a8d0ee',
    fundo: '#f7fafc',
    fundoEscuro: '#0f1317',
  },
];

/** Fica no aparelho, como no app antigo, e vale antes do login tambem. */
const CHAVE = 'piggu_tema';

function temaPorId(id: string | null): Tema {
  return TEMAS.find((tema) => tema.id === id) ?? TEMAS[0];
}

function lerTemaSalvo(): Tema {
  try {
    return temaPorId(localStorage.getItem(CHAVE));
  } catch {
    return TEMAS[0];
  }
}

function aplicar(tema: Tema): void {
  const raiz = document.documentElement;
  if (tema.id === TEMAS[0].id) {
    delete raiz.dataset['tema'];
  } else {
    raiz.dataset['tema'] = tema.id;
  }
  document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]').forEach((meta) => {
    meta.content = meta.media.includes('dark') ? tema.fundoEscuro : tema.fundo;
  });
}

/** Chamado no main.ts antes do Angular subir, para a tela nao piscar em rosa. */
export function aplicarTemaSalvo(): void {
  aplicar(lerTemaSalvo());
}

@Injectable({ providedIn: 'root' })
export class Temas {
  readonly atual = signal<Tema>(lerTemaSalvo());

  escolher(id: IdDoTema): void {
    const tema = temaPorId(id);
    this.atual.set(tema);
    aplicar(tema);
    try {
      localStorage.setItem(CHAVE, tema.id);
    } catch {
      // Sem armazenamento (aba privada): o tema vale ate fechar a aba.
    }
  }
}
