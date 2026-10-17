import { DestroyRef, Signal, effect, inject } from '@angular/core';

const CLASSE = 'rolagem-travada';

/**
 * Enquanto uma janela por cima da tela estiver aberta (busca, assistente), a pagina de
 * tras nao rola: no iPhone o dedo arrastando o fundo levava a tela junto e a janela
 * ficava parada. Chamar no construtor do componente.
 *
 * Tambem acompanha a parte visivel da tela: com o teclado aberto o iPhone desloca a
 * tela para cima e a janela fixa ia parar embaixo do relogio. As variaveis --tela-topo e
 * --tela-altura (no html) dizem onde a parte visivel comeca e quanto ela mede.
 */
export function travarRolagemEnquanto(aberta: Signal<boolean>): void {
  const raiz = document.documentElement;
  const tela = window.visualViewport;
  const medir = () => {
    if (tela) {
      raiz.style.setProperty('--tela-topo', `${tela.offsetTop}px`);
      raiz.style.setProperty('--tela-altura', `${tela.height}px`);
    }
  };
  const soltar = () => {
    raiz.classList.remove(CLASSE);
    tela?.removeEventListener('resize', medir);
    tela?.removeEventListener('scroll', medir);
    raiz.style.removeProperty('--tela-topo');
    raiz.style.removeProperty('--tela-altura');
  };
  effect(() => {
    if (!aberta()) {
      soltar();
      return;
    }
    raiz.classList.add(CLASSE);
    medir();
    tela?.addEventListener('resize', medir);
    tela?.addEventListener('scroll', medir);
  });
  inject(DestroyRef).onDestroy(soltar);
}
