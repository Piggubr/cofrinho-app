import { Directive, input } from '@angular/core';

/**
 * Botao de estado vazio que leva ao campo onde a coisa se cria.
 *
 * <p>O formulario costuma estar na mesma tela, acima da lista vazia; no celular ele
 * fica fora da vista, e o botao rola ate ele e ja deixa o cursor no campo.</p>
 */
@Directive({
  selector: 'button[appFocar]',
  host: { type: 'button', '(click)': 'focar()' },
})
export class Focar {
  /** Id do campo (ou da secao) de destino. */
  readonly appFocar = input.required<string>();

  protected focar(): void {
    const destino = document.getElementById(this.appFocar());
    destino?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
    destino?.focus({ preventScroll: true });
  }
}

/**
 * Celular ou tablet (dedo como ponteiro principal). Ai o app nao poe o cursor num campo
 * sozinho: no iPhone isso sobe o teclado de surpresa, antes de a pessoa pedir.
 */
export function toqueNaTela(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;
}
