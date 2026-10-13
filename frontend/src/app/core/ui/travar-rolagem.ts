import { DestroyRef, Signal, effect, inject } from '@angular/core';

const CLASSE = 'rolagem-travada';

/**
 * Enquanto uma janela por cima da tela estiver aberta (busca, assistente), a pagina de
 * tras nao rola: no iPhone o dedo arrastando o fundo levava a tela junto e a janela
 * ficava parada. Chamar no construtor do componente.
 */
export function travarRolagemEnquanto(aberta: Signal<boolean>): void {
  const raiz = document.documentElement;
  effect(() => raiz.classList.toggle(CLASSE, aberta()));
  inject(DestroyRef).onDestroy(() => raiz.classList.remove(CLASSE));
}
