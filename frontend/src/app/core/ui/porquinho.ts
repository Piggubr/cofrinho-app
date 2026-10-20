import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type HumorDoPorquinho = 'feliz' | 'perdido' | 'tonto';

/**
 * O porquinho das telas de erro e de abertura.
 *
 * <p>Desenhado aqui em SVG, e nao num arquivo, para pintar com as cores do tema: cada
 * parte usa um token (corpo em --pink-bg, contorno em --rose-deep e assim por diante),
 * entao ele muda junto com a Cor do Piggu e com o modo escuro.</p>
 *
 * <ul>
 *   <li>feliz: sorrindo, para a abertura do app;</li>
 *   <li>perdido: olhando para cima, com uma interrogacao e a moeda fugindo (404);</li>
 *   <li>tonto: olhos em X, curativo e estrelinhas (500 e sem conexao).</li>
 * </ul>
 */
@Component({
  selector: 'app-porquinho',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 220 180"
      [attr.width]="tamanho()"
      [attr.height]="(tamanho() * 180) / 220"
    >
      <ellipse class="sombra" cx="106" cy="166" rx="64" ry="7" />

      @if (humor() === 'perdido') {
        <g class="moeda">
          <circle cx="196" cy="152" r="12" />
          <circle class="moeda-miolo" cx="196" cy="152" r="7" />
        </g>
        <text class="sinal" x="170" y="42">?</text>
        <text class="sinal pequeno" x="192" y="62">?</text>
      }
      @if (humor() === 'tonto') {
        <path class="estrela" d="M70 22l3 7 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z" />
        <path
          class="estrela"
          d="M150 14l2.4 5.4 5.6.8-4 4 .9 5.6-4.9-2.6-5 2.6 1-5.6-4-4 5.6-.8z"
        />
        <path class="estrela" d="M112 4l1.8 4 4.2.6-3 3 .7 4.2-3.7-2-3.7 2 .7-4.2-3-3 4.2-.6z" />
      }

      <g class="corpo-todo">
        <path class="rabo" d="M40 98c-14-1-17-15-7-18 8-2 9 9 0 10" />
        <rect class="perna" x="58" y="126" width="22" height="34" rx="10" />
        <rect class="perna" x="124" y="126" width="22" height="34" rx="10" />
        <path class="orelha fundo" d="M104 58c-4-20 6-32 20-28 2 12-2 22-8 30z" />
        <ellipse class="corpo" cx="102" cy="100" rx="66" ry="50" />
        <rect class="fenda" x="76" y="57" width="32" height="8" rx="4" />
        <path class="orelha" d="M124 60c-2-22 10-34 24-28 2 14-4 24-12 32z" />
        <ellipse class="bochecha" cx="136" cy="111" rx="9" ry="5.5" />
        <ellipse class="focinho" cx="166" cy="99" rx="16" ry="13" />
        <ellipse class="narina" cx="160" cy="99" rx="2.6" ry="4" />
        <ellipse class="narina" cx="171" cy="99" rx="2.6" ry="4" />

        @switch (humor()) {
          @case ('tonto') {
            <path class="olho-x" d="M131 80l10 10M141 80l-10 10" />
            <path class="boca" d="M144 121c3-3 5 3 8 0s5 3 8 0" />
            <g class="curativo" transform="rotate(-28 78 84)">
              <rect x="60" y="77" width="36" height="14" rx="7" />
              <rect class="curativo-miolo" x="72" y="77" width="12" height="14" />
            </g>
          }
          @case ('perdido') {
            <path class="boca" d="M128 72q8-7 17-3" />
            <circle class="olho" cx="136" cy="85" r="6" />
            <circle class="brilho" cx="137.5" cy="81.5" r="2" />
            <ellipse class="boca cheia" cx="153" cy="121" rx="3.5" ry="4.5" />
          }
          @default {
            <circle class="olho" cx="136" cy="86" r="6" />
            <circle class="brilho" cx="138" cy="84" r="2" />
            <path class="boca" d="M146 118q6 6 12 0" />
          }
        }
      </g>
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      line-height: 0;
    }

    svg {
      overflow: visible;
    }

    .sombra {
      fill: var(--track);
    }

    .corpo,
    .perna,
    .orelha,
    .focinho {
      stroke: var(--rose-deep);
      stroke-width: 3.5;
      stroke-linejoin: round;
    }

    .corpo {
      fill: var(--pink-bg);
    }

    .perna,
    .orelha,
    .focinho {
      fill: var(--rose);
    }

    .orelha.fundo {
      fill: var(--rose-deep);
    }

    .rabo,
    .boca,
    .olho-x {
      fill: none;
      stroke: var(--rose-deep);
      stroke-width: 3.5;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    .boca.cheia {
      fill: var(--rose-deep);
      stroke: none;
    }

    .fenda,
    .narina {
      fill: var(--rose-deep);
    }

    .bochecha {
      fill: var(--rose);
      opacity: 0.55;
    }

    .olho {
      fill: var(--ink);
    }

    .brilho {
      fill: var(--surface);
    }

    .moeda circle {
      fill: var(--ouro);
      stroke: var(--ouro-ink);
      stroke-width: 2.5;
    }

    .moeda .moeda-miolo {
      fill: none;
      stroke-width: 2;
      opacity: 0.6;
    }

    .sinal {
      fill: var(--primaria);
      font-family: var(--font-body);
      font-size: 34px;
      font-weight: 700;
    }

    .sinal.pequeno {
      font-size: 22px;
      opacity: 0.7;
    }

    .estrela {
      fill: var(--ouro);
    }

    .curativo rect {
      fill: var(--ouro);
      stroke: var(--ouro-ink);
      stroke-width: 2;
    }

    .curativo .curativo-miolo {
      fill: var(--ouro-ink);
      stroke: none;
      opacity: 0.3;
    }

    @media (prefers-reduced-motion: no-preference) {
      .corpo-todo {
        animation: flutua 3.2s ease-in-out infinite;
      }

      .moeda {
        animation: rola 2.6s ease-in-out infinite;
        transform-box: fill-box;
        transform-origin: center;
      }

      .estrela {
        animation: pisca 1.6s ease-in-out infinite;
      }

      .estrela:nth-of-type(2) {
        animation-delay: 0.5s;
      }

      .estrela:nth-of-type(3) {
        animation-delay: 1s;
      }
    }

    @keyframes flutua {
      50% {
        transform: translateY(-4px);
      }
    }

    @keyframes rola {
      50% {
        transform: translateX(8px) rotate(90deg);
      }
    }

    @keyframes pisca {
      50% {
        opacity: 0.35;
      }
    }
  `,
})
export class Porquinho {
  readonly humor = input<HumorDoPorquinho>('feliz');
  /** Largura em pixels; a altura acompanha a proporcao do desenho. */
  readonly tamanho = input(180);
}
