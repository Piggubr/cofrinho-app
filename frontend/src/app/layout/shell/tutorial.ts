import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  output,
  signal,
} from '@angular/core';
import { Icone } from '../../core/ui/icone';
import { PassoDoTutorial, Tutorial } from '../../core/ui/tutorial';

/** Folga entre o alvo, o recorte em volta dele e o balao. */
const FOLGA = 6;
const DISTANCIA = 14;
const MARGEM = 16;
const LARGURA = 320;

interface Posicao {
  /** Recorte claro em volta do botao ou aba. */
  readonly recorte: { top: number; left: number; width: number; height: number };
  /** Balao acima ou abaixo do alvo; `top` ou `bottom`, conforme o lado. */
  readonly balao: { left: number; width: number; top?: number; bottom?: number };
  readonly seta: number;
  readonly lado: 'acima' | 'abaixo';
  /** A tela do passo fica dentro do "Mais" (celular); o balao aponta para ele. */
  readonly noMais: boolean;
}

/**
 * Tour de primeiro acesso em baloes que saem dos proprios botoes e abas.
 *
 * <p>Cada passo aponta para o elemento com o `data-tour` do passo que estiver na tela:
 * a aba no celular, o item do menu lateral no computador. O fundo escurece tudo menos
 * esse elemento. Sem alvo visivel (boas-vindas, ou as abas nativas do iOS, que nao sao
 * HTML), o balao aparece no centro. O estado fica em {@link Tutorial}.</p>
 */
@Component({
  selector: 'app-tutorial',
  imports: [Icone],
  template: `
    @if (tutorial.passo(); as passo) {
      <div class="fundo" aria-hidden="true" [class.escuro]="!posicao()" (click)="tutorial.concluir()"></div>
      @if (posicao(); as p) {
        <div
          class="recorte"
          aria-hidden="true"
          [style.top.px]="p.recorte.top"
          [style.left.px]="p.recorte.left"
          [style.width.px]="p.recorte.width"
          [style.height.px]="p.recorte.height"
        ></div>
      }
      <section
        class="balao"
        [class.solto]="!posicao()"
        [class.acima]="posicao()?.lado === 'acima'"
        [class.abaixo]="posicao()?.lado === 'abaixo'"
        [style.left.px]="posicao()?.balao?.left"
        [style.width.px]="posicao()?.balao?.width"
        [style.top.px]="posicao()?.balao?.top"
        [style.bottom.px]="posicao()?.balao?.bottom"
        [style.--seta.px]="posicao()?.seta"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-tour"
        aria-describedby="texto-tour"
        tabindex="-1"
        (keydown.escape)="tutorial.concluir()"
      >
        <div class="topo">
          <span class="simbolo"><app-icone [nome]="passo.icone" [tamanho]="18" [traco]="2" /></span>
          <h2 id="titulo-tour">{{ passo.titulo }}</h2>
          <span class="contagem">{{ tutorial.indice() + 1 }}/{{ tutorial.passos().length }}</span>
        </div>
        <p id="texto-tour">{{ passo.texto }}</p>
        @if (posicao()?.noMais) {
          <p i18n class="dica">Fica dentro de "Mais".</p>
        }
        @if (tutorial.ultimo()) {
          <p i18n class="dica">Para rever este tour, vá em Perfil.</p>
        }

        <div class="acoes">
          <button i18n type="button" class="botao fantasma pequeno" (click)="tutorial.concluir()">
            Pular
          </button>
          <span class="espaco"></span>
          @if (tutorial.indice() > 0) {
            <button i18n type="button" class="botao contorno pequeno" (click)="tutorial.voltar()">
              Voltar
            </button>
          }
          @if (tutorial.ultimo() && podeLancar()) {
            <button i18n type="button" class="botao pequeno" (click)="lancarAgora()">
              Lançar meu primeiro gasto
            </button>
          } @else {
            <button type="button" class="botao pequeno" (click)="tutorial.avancar()">
              @if (tutorial.ultimo()) {
                <ng-container i18n>Começar</ng-container>
              } @else {
                <ng-container i18n>Próximo</ng-container>
              }
            </button>
          }
        </div>
      </section>
    }
  `,
  styles: `
    .fundo {
      position: fixed;
      inset: 0;
      z-index: 40;
    }
    .fundo.escuro {
      background: rgba(43, 30, 50, 0.5);
    }
    /* A sombra enorme escurece a tela toda menos o recorte. */
    .recorte {
      position: fixed;
      z-index: 41;
      border-radius: 16px;
      box-shadow:
        0 0 0 3px var(--primaria),
        0 0 0 9999px rgba(43, 30, 50, 0.5);
      pointer-events: none;
      transition: all 0.25s ease;
    }
    .balao {
      position: fixed;
      z-index: 43;
      background: var(--surface);
      border-radius: var(--raio);
      box-shadow: var(--shadow-float);
      padding: 0.9rem 1rem 0.8rem;
      outline: none;
    }
    .balao.solto {
      top: calc(14vh + env(safe-area-inset-top, 0px));
      left: 50%;
      transform: translateX(-50%);
      width: min(320px, calc(100vw - 32px));
    }
    /* Seta: um quadrado girado na borda do balao, na direcao do alvo. */
    .balao.acima::after,
    .balao.abaixo::after {
      content: '';
      position: absolute;
      left: calc(var(--seta) - 7px);
      width: 14px;
      height: 14px;
      background: var(--surface);
      transform: rotate(45deg);
    }
    .balao.acima::after {
      bottom: -7px;
    }
    .balao.abaixo::after {
      top: -7px;
    }
    .topo {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.4rem;
    }
    .simbolo {
      display: inline-grid;
      place-items: center;
      width: 30px;
      height: 30px;
      flex: none;
      border-radius: 50%;
      background: color-mix(in srgb, var(--primaria) 14%, transparent);
      color: var(--primaria);
    }
    h2 {
      margin: 0;
      flex: 1;
      font-size: 1rem;
    }
    .contagem {
      font-size: 0.8rem;
      color: var(--muted);
    }
    p {
      margin: 0 0 0.6rem;
      color: var(--ink-soft);
      font-size: 0.92rem;
    }
    .dica {
      font-size: 0.82rem;
      color: var(--muted);
    }
    .acoes {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .espaco {
      flex: 1;
    }
    @media (prefers-reduced-motion: reduce) {
      .recorte {
        transition: none;
      }
    }
  `,
})
export class TutorialDePrimeiroAcesso {
  protected readonly tutorial = inject(Tutorial);
  private readonly elemento: HTMLElement = inject(ElementRef).nativeElement;
  /** "Lançar meu primeiro gasto": fecha o tour e abre o painel do "+". */
  readonly lancar = output<void>();

  protected readonly posicao = signal<Posicao | null>(null);
  protected readonly podeLancar = computed(() =>
    this.tutorial.passos().some((passo) => passo.id === 'lancar'),
  );

  constructor() {
    // A cada passo, espera a tela desenhar, mede o alvo e leva o foco para o balao.
    effect((onCleanup) => {
      const passo = this.tutorial.passo();
      const quadro = requestAnimationFrame(() => {
        this.medir(passo);
        this.elemento.querySelector<HTMLElement>('.balao')?.focus();
      });
      onCleanup(() => cancelAnimationFrame(quadro));
    });

    // Girar o celular ou mudar a janela muda onde o alvo esta.
    const remedir = () => this.medir(this.tutorial.passo());
    window.addEventListener('resize', remedir);
    window.addEventListener('scroll', remedir, true);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('resize', remedir);
      window.removeEventListener('scroll', remedir, true);
    });
  }

  protected lancarAgora(): void {
    this.tutorial.concluir();
    this.lancar.emit();
  }

  private medir(passo: PassoDoTutorial | undefined): void {
    const alvo = passo ? alvoNaTela(passo) : null;
    this.posicao.set(
      alvo
        ? {
            ...posicionar(alvo.getBoundingClientRect()),
            noMais: alvo.dataset['tour'] === 'mais' && passo?.id !== 'mais',
          }
        : null,
    );
  }
}

/** O primeiro elemento do passo que esta de fato na tela. */
function alvoNaTela(passo: PassoDoTutorial): HTMLElement | null {
  const candidatos = (passo.alvos ?? []).flatMap((id) =>
    [...document.querySelectorAll<HTMLElement>(`[data-tour="${id}"]`)].map((candidato) =>
      // Componentes (busca, assistente) apontam pelo botao que desenham.
      candidato.tagName.includes('-')
        ? (candidato.querySelector<HTMLElement>('button') ?? candidato)
        : candidato,
    ),
  );
  const naTela = candidatos.find(visivel);
  if (naTela) {
    return naTela;
  }
  // No computador baixo, o item pode estar no fim do menu lateral, fora da vista: rola ate ele.
  for (const candidato of candidatos) {
    if (candidato.getBoundingClientRect().width > 0) {
      candidato.scrollIntoView?.({ block: 'center' });
      if (visivel(candidato)) {
        return candidato;
      }
    }
  }
  return null;
}

/** Tem tamanho e esta dentro da janela (o menu fechado do celular fica fora dela). */
function visivel(elemento: HTMLElement): boolean {
  const caixa = elemento.getBoundingClientRect();
  return (
    caixa.width > 0 &&
    caixa.height > 0 &&
    caixa.top >= 0 &&
    caixa.left >= 0 &&
    caixa.bottom <= window.innerHeight + 1 &&
    caixa.right <= window.innerWidth + 1 &&
    getComputedStyle(elemento).visibility !== 'hidden'
  );
}

/** Balao abaixo de alvos na metade de cima da tela, acima dos da metade de baixo. */
function posicionar(caixa: DOMRect): Omit<Posicao, 'noMais'> {
  const larguraDaJanela = window.innerWidth;
  const largura = Math.min(LARGURA, larguraDaJanela - MARGEM * 2);
  const centro = caixa.left + caixa.width / 2;
  const left = Math.min(Math.max(centro - largura / 2, MARGEM), larguraDaJanela - largura - MARGEM);
  const seta = Math.min(Math.max(centro - left, 20), largura - 20);
  const recorte = {
    top: caixa.top - FOLGA,
    left: caixa.left - FOLGA,
    width: caixa.width + FOLGA * 2,
    height: caixa.height + FOLGA * 2,
  };
  if (caixa.top + caixa.height / 2 > window.innerHeight / 2) {
    return {
      recorte,
      balao: { left, width: largura, bottom: window.innerHeight - recorte.top + DISTANCIA },
      seta,
      lado: 'acima',
    };
  }
  return {
    recorte,
    balao: { left, width: largura, top: recorte.top + recorte.height + DISTANCIA },
    seta,
    lado: 'abaixo',
  };
}
