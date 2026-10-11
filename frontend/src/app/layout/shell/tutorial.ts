import { Component, ElementRef, afterNextRender, inject, output } from '@angular/core';
import { Icone } from '../../core/ui/icone';
import { Tutorial } from '../../core/ui/tutorial';

/** Cartao do tour de primeiro acesso, por cima da tela. O estado fica em {@link Tutorial}. */
@Component({
  selector: 'app-tutorial',
  imports: [Icone],
  template: `
    @if (tutorial.passo(); as passo) {
      <div class="fundo" (click)="tutorial.concluir()"></div>
      <section
        class="cartao-tour"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-tour"
        aria-describedby="texto-tour"
        tabindex="-1"
        (keydown.escape)="tutorial.concluir()"
      >
        <button
          i18n
          type="button"
          class="botao fantasma pequeno pular"
          (click)="tutorial.concluir()"
        >
          Pular
        </button>
        <span class="simbolo"><app-icone [nome]="passo.icone" [tamanho]="28" [traco]="2" /></span>
        <h2 id="titulo-tour">{{ passo.titulo }}</h2>
        <p id="texto-tour">{{ passo.texto }}</p>
        @if (tutorial.ultimo()) {
          <p i18n class="dica">Para rever este tour, vá em Perfil.</p>
        }

        <div class="pontos" aria-hidden="true">
          @for (item of tutorial.passos(); track item.id; let i = $index) {
            <span class="ponto" [class.atual]="i === tutorial.indice()"></span>
          }
        </div>

        <div class="acoes">
          @if (tutorial.indice() > 0) {
            <button i18n type="button" class="botao contorno" (click)="tutorial.voltar()">
              Voltar
            </button>
          }
          @if (tutorial.ultimo() && podeLancar()) {
            <button i18n type="button" class="botao" (click)="lancarAgora()">
              Lançar meu primeiro gasto
            </button>
          } @else {
            <button type="button" class="botao" (click)="tutorial.avancar()">
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
      background: rgba(43, 30, 50, 0.45);
      z-index: 40;
    }
    .cartao-tour {
      position: fixed;
      top: calc(12vh + env(safe-area-inset-top, 0px));
      left: 50%;
      transform: translateX(-50%);
      width: min(400px, calc(100vw - 32px));
      background: var(--surface);
      border-radius: var(--raio);
      box-shadow: var(--shadow-float);
      padding: 1.5rem 1.25rem 1.25rem;
      text-align: center;
      z-index: 41;
      outline: none;
    }
    .pular {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
    }
    .simbolo {
      display: inline-grid;
      place-items: center;
      width: 56px;
      height: 56px;
      border-radius: 50%;
      background: color-mix(in srgb, var(--primaria) 14%, transparent);
      color: var(--primaria);
      margin-bottom: 0.75rem;
    }
    h2 {
      margin: 0 0 0.5rem;
      font-size: 1.2rem;
    }
    p {
      margin: 0 0 0.75rem;
      color: var(--ink-soft);
    }
    .dica {
      font-size: 0.85rem;
      color: var(--muted);
    }
    .pontos {
      display: flex;
      justify-content: center;
      gap: 0.35rem;
      margin: 0.25rem 0 1rem;
    }
    .ponto {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--border);
      transition: width 0.2s ease;
    }
    .ponto.atual {
      width: 18px;
      border-radius: 4px;
      background: var(--primaria);
    }
    .acoes {
      display: flex;
      justify-content: center;
      gap: 0.5rem;
    }
  `,
})
export class TutorialDePrimeiroAcesso {
  protected readonly tutorial = inject(Tutorial);
  /** "Lançar meu primeiro gasto": fecha o tour e abre o painel do "+". */
  readonly lancar = output<void>();

  constructor() {
    // Foco no cartao para o leitor de tela anunciar e o Esc funcionar de cara.
    const elemento = inject(ElementRef<HTMLElement>);
    afterNextRender(() => elemento.nativeElement.querySelector('.cartao-tour')?.focus());
  }

  protected podeLancar(): boolean {
    return this.tutorial.passos().some((passo) => passo.id === 'lancar');
  }

  protected lancarAgora(): void {
    this.tutorial.concluir();
    this.lancar.emit();
  }
}
