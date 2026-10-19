const SELETOR = '.aviso.erro';

/**
 * As telas mostram o erro do formulario no topo ("Informe o dia do vencimento"). Numa tela
 * comprida, quem tocou em Salvar la embaixo nao via o aviso e achava que nada aconteceu.
 * Aqui, em um lugar so para todas as telas: quando um erro aparece fora da vista, a tela
 * rola ate ele e ele pisca. Tocar de novo no mesmo botao, com o mesmo erro, leva de novo.
 *
 * @returns funcao que desliga a observacao
 */
export function levarAoErro(raiz: HTMLElement): () => void {
  const observador = new MutationObserver((mudancas) => {
    for (const mudanca of mudancas) {
      const alvo = mudanca.target instanceof Element ? mudanca.target : mudanca.target.parentElement;
      const erro =
        alvo?.closest<HTMLElement>(SELETOR) ??
        [...mudanca.addedNodes]
          .filter((no): no is HTMLElement => no instanceof HTMLElement)
          .map((no) => (no.matches(SELETOR) ? no : no.querySelector<HTMLElement>(SELETOR)))
          .find((achado) => achado);
      if (erro) {
        mostrar(erro);
        return;
      }
    }
  });
  observador.observe(raiz, { childList: true, subtree: true, characterData: true });

  // Mesmo erro de novo: o texto nao muda e o observador nao ve nada.
  const aoTocar = (evento: Event) => {
    const botao = (evento.target as Element | null)?.closest('.acoes button, button[type="submit"]');
    if (botao) {
      setTimeout(() => {
        const erro = raiz.querySelector<HTMLElement>(SELETOR);
        if (erro) mostrar(erro);
      });
    }
  };
  raiz.addEventListener('click', aoTocar);

  return () => {
    observador.disconnect();
    raiz.removeEventListener('click', aoTocar);
  };
}

function mostrar(erro: HTMLElement): void {
  const caixa = erro.getBoundingClientRect();
  if (caixa.top >= 0 && caixa.bottom <= window.innerHeight) {
    return;
  }
  erro.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  erro.classList.remove('chamando');
  void erro.offsetWidth; // reinicia a animacao
  erro.classList.add('chamando');
}
