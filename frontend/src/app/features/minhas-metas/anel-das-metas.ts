import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Anel de progresso das metas do mes, com o porquinho flutuando em cima.
 *
 * <p>Mesmo desenho do app antigo: um anel em gomos que enche na cor do tema e o
 * piggu-love no topo, balancando de leve. Quem prefere menos movimento (ajuste do
 * aparelho) ve o porquinho parado.</p>
 */
@Component({
  selector: 'app-anel-das-metas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="anel"
      role="meter"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-valuenow]="valor()"
      aria-label="Progresso das metas do mês"
      i18n-aria-label
      [style.--progresso]="valor() + '%'"
      [class.pequeno]="pequeno()"
    >
      <div class="gomos" aria-hidden="true"></div>
      <img class="porquinho" src="ilustracoes/piggu-love.svg" alt="" aria-hidden="true" />
      <div class="centro">
        <strong>{{ valor() }}%</strong>
        <span i18n>concluído</span>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: grid;
      place-items: center;
    }
    .anel {
      --tamanho: 176px;
      --espessura: 22px;
      position: relative;
      display: grid;
      width: var(--tamanho);
      aspect-ratio: 1;
      place-items: center;
      margin-top: 18px;
    }
    .anel.pequeno {
      --tamanho: 132px;
      --espessura: 16px;
      margin-top: 14px;
    }
    .gomos {
      position: absolute;
      inset: 8px;
      border-radius: 50%;
      background: conic-gradient(var(--primaria) var(--progresso), var(--track) 0);
      -webkit-mask: radial-gradient(
        farthest-side,
        transparent calc(100% - var(--espessura)),
        #000 calc(100% - var(--espessura) + 1px)
      );
      mask: radial-gradient(
        farthest-side,
        transparent calc(100% - var(--espessura)),
        #000 calc(100% - var(--espessura) + 1px)
      );
    }
    /* Os cortes entre os gomos, na cor do cartao. */
    .gomos::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      background: repeating-conic-gradient(transparent 0 42deg, var(--surface) 42deg 45deg);
    }
    .porquinho {
      position: absolute;
      z-index: 2;
      top: -22px;
      left: 50%;
      width: 40%;
      transform: translateX(-50%);
      filter: drop-shadow(0 4px 6px rgba(43, 30, 50, 0.12));
      animation: flutuar 3.2s ease-in-out infinite;
    }
    @keyframes flutuar {
      0%,
      100% {
        transform: translate(-50%, 0) rotate(-3deg);
      }
      50% {
        transform: translate(-50%, -6px) rotate(3deg);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .porquinho {
        animation: none;
      }
    }
    .centro {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding-top: 14px;
      text-align: center;
    }
    .centro strong {
      font-size: 2rem;
      font-weight: 700;
      line-height: 1.2;
      letter-spacing: -0.03em;
    }
    .pequeno .centro strong {
      font-size: 1.5rem;
    }
    .centro span {
      color: var(--muted);
      font-size: 0.8rem;
    }
  `,
})
export class AnelDasMetas {
  /** De 0 a 100. */
  readonly percentual = input.required<number>();
  readonly pequeno = input(false);

  protected readonly valor = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.percentual()))),
  );
}
