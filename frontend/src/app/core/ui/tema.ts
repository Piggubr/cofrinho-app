import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export type IdDoTema =
  | 'chiclete'
  | 'rosa'
  | 'menta'
  | 'lilas'
  | 'azul'
  | 'coral'
  | 'mel'
  | 'vinho'
  | 'grafite'
  | 'tangerina'
  | 'eletrico'
  | 'turquesa'
  | 'cobalto';

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
  /** Chiclete, Rosa, Menta e Azul sao gratis; as outras cores sao do Premium. */
  premium: boolean;
}

/**
 * O Chiclete e o padrao; depois vem os quatro temas do app antigo, mais quatro e as cores
 * vibrantes. As cores de cada um ficam no styles.scss.
 */
export const TEMAS: readonly Tema[] = [
  {
    id: 'chiclete',
    nome: $localize`Chiclete`,
    descricao: $localize`Vibrante e divertido`,
    destaque: '#d1006b',
    destaqueEscuro: '#ff5cae',
    fundo: '#fff4f9',
    fundoEscuro: '#14060d',
    premium: false,
  },
  {
    id: 'rosa',
    nome: $localize`Rosa Piggu`,
    descricao: $localize`Delicado e acolhedor`,
    destaque: '#8e2f58',
    destaqueEscuro: '#f0a6c4',
    fundo: '#fcf8f9',
    fundoEscuro: '#161015',
    premium: false,
  },
  {
    id: 'menta',
    nome: $localize`Menta`,
    descricao: $localize`Leve e tranquilo`,
    destaque: '#1f6b5a',
    destaqueEscuro: '#9fe0c8',
    fundo: '#f7fbfa',
    fundoEscuro: '#0f1513',
    premium: false,
  },
  {
    id: 'azul',
    nome: $localize`Azul`,
    descricao: $localize`Sereno e organizado`,
    destaque: '#22608f',
    destaqueEscuro: '#a8d0ee',
    fundo: '#f7fafc',
    fundoEscuro: '#0f1317',
    premium: false,
  },
  {
    id: 'lilas',
    nome: $localize`Lilás`,
    descricao: $localize`Calmo e criativo`,
    destaque: '#6a3d94',
    destaqueEscuro: '#d2b8f2',
    fundo: '#faf8fc',
    fundoEscuro: '#131017',
    premium: true,
  },
  {
    id: 'coral',
    nome: $localize`Coral`,
    descricao: $localize`Quente e animado`,
    destaque: '#a8432c',
    destaqueEscuro: '#f5b8a6',
    fundo: '#fdf9f8',
    fundoEscuro: '#171110',
    premium: true,
  },
  {
    id: 'mel',
    nome: $localize`Mel`,
    descricao: $localize`Doce e ensolarado`,
    destaque: '#855a0b',
    destaqueEscuro: '#f3d38f',
    fundo: '#fcfaf5',
    fundoEscuro: '#15130e',
    premium: true,
  },
  {
    id: 'vinho',
    nome: $localize`Vinho`,
    descricao: $localize`Elegante e marcante`,
    destaque: '#7a1f2e',
    destaqueEscuro: '#f0aab3',
    fundo: '#fcf8f8',
    fundoEscuro: '#161011',
    premium: true,
  },
  {
    id: 'grafite',
    nome: $localize`Grafite`,
    descricao: $localize`Sóbrio e discreto`,
    destaque: '#363d4a',
    destaqueEscuro: '#d3dae4',
    fundo: '#f9fafb',
    fundoEscuro: '#111214',
    premium: true,
  },
  {
    id: 'tangerina',
    nome: $localize`Tangerina`,
    descricao: $localize`Quente e radiante`,
    destaque: '#c43e00',
    destaqueEscuro: '#ff9a3c',
    fundo: '#fff7f0',
    fundoEscuro: '#140a04',
    premium: true,
  },
  {
    id: 'eletrico',
    nome: $localize`Elétrico`,
    descricao: $localize`Ousado e intenso`,
    destaque: '#7426e0',
    destaqueEscuro: '#c69bff',
    fundo: '#f8f4ff',
    fundoEscuro: '#0c0716',
    premium: true,
  },
  {
    id: 'turquesa',
    nome: $localize`Turquesa`,
    descricao: $localize`Fresco e vivo`,
    destaque: '#007f8c',
    destaqueEscuro: '#3fe0d0',
    fundo: '#f2fcfb',
    fundoEscuro: '#041212',
    premium: true,
  },
  {
    id: 'cobalto',
    nome: $localize`Cobalto`,
    descricao: $localize`Forte e confiante`,
    destaque: '#2449e8',
    destaqueEscuro: '#8aa8ff',
    fundo: '#f4f7ff',
    fundoEscuro: '#050917',
    premium: true,
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

/** Chamado no main.ts antes do Angular subir, para a tela nao piscar no Chiclete. */
export function aplicarTemaSalvo(): void {
  aplicar(lerTemaSalvo());
}

/** Marca que a cor ou o icone voltaram ao padrao porque o Premium acabou. */
const CHAVE_DO_AVISO = 'piggu_personalizacao_voltou';

export function marcarQueVoltouAoPadrao(): void {
  try {
    localStorage.setItem(CHAVE_DO_AVISO, '1');
  } catch {
    // Sem armazenamento: o aviso so nao aparece.
  }
}

export function voltouAoPadrao(): boolean {
  try {
    return localStorage.getItem(CHAVE_DO_AVISO) === '1';
  } catch {
    return false;
  }
}

export function esquecerQueVoltouAoPadrao(): void {
  try {
    localStorage.removeItem(CHAVE_DO_AVISO);
  } catch {
    // Nada a fazer.
  }
}

/**
 * Cor escolhida no Perfil. As cores Premium so ficam enquanto o plano vale: quando alguem
 * entra sem Premium com uma delas salva, o app volta para o Chiclete. Antes do login nada muda,
 * porque ainda nao se sabe o plano. A trava e so no aparelho: e enfeite, nao dado.
 */
@Injectable({ providedIn: 'root' })
export class Temas {
  private readonly auth = inject(AuthService);
  readonly atual = signal<Tema>(lerTemaSalvo());

  constructor() {
    effect(() => {
      if (this.auth.autenticado() && !this.auth.ehPremium() && this.atual().premium) {
        untracked(() => {
          this.salvar(TEMAS[0]);
          marcarQueVoltouAoPadrao();
        });
      }
    });
  }

  /** Falso quando a cor e do Premium e a pessoa nao tem: a tela leva para os planos. */
  escolher(id: IdDoTema): boolean {
    const tema = temaPorId(id);
    if (tema.premium && !this.auth.ehPremium()) {
      return false;
    }
    this.salvar(tema);
    return true;
  }

  private salvar(tema: Tema): void {
    this.atual.set(tema);
    aplicar(tema);
    try {
      localStorage.setItem(CHAVE, tema.id);
    } catch {
      // Sem armazenamento (aba privada): o tema vale ate fechar a aba.
    }
  }
}
