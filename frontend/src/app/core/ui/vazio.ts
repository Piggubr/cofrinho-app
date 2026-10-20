import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icone, NomeDoIcone } from './icone';

/**
 * Estado vazio das listas.
 *
 * <p>Um icone que diz do que e a lista, um titulo curto e, por dentro, o que fazer para
 * sair dele: uma frase e os botoes em .vazio-acao. Compacto e para dentro de cartoes
 * pequenos do Painel, onde o desenho grande pesaria.</p>
 *
 * <pre>
 * &lt;app-vazio icone="recibo" titulo="Nenhum gasto neste mes"&gt;
 *   &lt;p&gt;Lance o primeiro ou traga o extrato do banco.&lt;/p&gt;
 *   &lt;p class="vazio-acao"&gt;&lt;button ...&gt;Lancar um gasto&lt;/button&gt;&lt;/p&gt;
 * &lt;/app-vazio&gt;
 * </pre>
 */
@Component({
  selector: 'app-vazio',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.compacto]': 'compacto()' },
  template: `
    <span class="selo" aria-hidden="true">
      <app-icone [nome]="icone()" [tamanho]="compacto() ? 20 : 28" [traco]="1.7" />
    </span>
    <p class="titulo">{{ titulo() }}</p>
    <div class="texto"><ng-content /></div>
  `,
  styles: `
    :host {
      display: grid;
      justify-items: center;
      padding: 1.75rem 1rem 1.5rem;
      text-align: center;
    }

    .selo {
      position: relative;
      display: grid;
      place-items: center;
      width: 68px;
      height: 68px;
      margin-bottom: 0.9rem;
      border-radius: 24px;
      background: var(--rose-soft);
      color: var(--primaria);
      transform: rotate(-6deg);
    }

    .selo app-icone {
      transform: rotate(6deg);
    }

    /* Duas bolinhas de enfeite, na cor de destaque do tema. */
    .selo::before,
    .selo::after {
      content: '';
      position: absolute;
      border-radius: 50%;
      background: var(--rose);
      opacity: 0.45;
    }

    .selo::before {
      width: 10px;
      height: 10px;
      top: -6px;
      right: -8px;
    }

    .selo::after {
      width: 6px;
      height: 6px;
      bottom: 2px;
      left: -10px;
    }

    .titulo {
      margin: 0 0 0.3rem;
      color: var(--ink);
      font-size: 0.98rem;
      font-weight: 600;
    }

    .texto {
      max-width: 340px;
      color: var(--muted);
      font-size: 0.86rem;
    }

    :host(.compacto) {
      padding: 1rem 0.5rem 0.75rem;
    }

    :host(.compacto) .selo {
      width: 44px;
      height: 44px;
      margin-bottom: 0.6rem;
      border-radius: 16px;
    }

    :host(.compacto) .titulo {
      font-size: 0.9rem;
    }
  `,
})
export class Vazio {
  readonly titulo = input.required<string>();
  readonly icone = input<NomeDoIcone>('cofrinho');
  readonly compacto = input(false);
}
