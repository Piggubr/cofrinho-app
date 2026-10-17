/**
 * Como a tela nova entra.
 *
 * <ul>
 *   <li>aba: troca entre os atalhos da barra de baixo, sobe de leve e aparece;</li>
 *   <li>avancar: tela de dentro (Perfil, itens do "Mais"), entra pela direita, como o
 *   empurrar do iOS;</li>
 *   <li>voltar: o voltar do aparelho ou de uma tela de dentro para um atalho, entra pela
 *   esquerda.</li>
 * </ul>
 */
export type DirecaoDaTroca = 'aba' | 'avancar' | 'voltar';

/** So o caminho: filtro na query ou ancora na mesma tela nao e troca de tela. */
export function caminhoDe(url: string): string {
  return url.split(/[?#]/)[0] || '/';
}

/** Null quando a tela continua a mesma. */
export function direcaoDaTroca(
  de: string,
  para: string,
  atalhos: readonly string[],
  voltando: boolean,
): DirecaoDaTroca | null {
  const origem = caminhoDe(de);
  const destino = caminhoDe(para);
  if (origem === destino) {
    return null;
  }
  if (voltando) {
    return 'voltar';
  }
  const ehAtalho = (caminho: string) =>
    atalhos.some((rota) => caminho === rota || caminho.startsWith(rota + '/'));
  if (ehAtalho(destino)) {
    return ehAtalho(origem) ? 'aba' : 'voltar';
  }
  return 'avancar';
}

const QUADROS: Record<DirecaoDaTroca, Keyframe[]> = {
  aba: [
    { opacity: 0, transform: 'translateY(8px)' },
    { opacity: 1, transform: 'none' },
  ],
  avancar: [
    { opacity: 0, transform: 'translateX(36px)' },
    { opacity: 1, transform: 'none' },
  ],
  voltar: [
    { opacity: 0, transform: 'translateX(-36px)' },
    { opacity: 1, transform: 'none' },
  ],
};

/**
 * Anima a entrada do conteudo. So o miolo se mexe: cabecalho, abas e o "+" ficam
 * parados, que e o que da a sensacao de "a tela mudou, o app nao". Com "reduzir
 * movimento" ligado no aparelho, so aparece, sem deslizar.
 */
export function animarEntrada(elemento: HTMLElement, direcao: DirecaoDaTroca): void {
  if (typeof elemento.animate !== 'function') {
    return;
  }
  const semMovimento =
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  elemento.getAnimations?.().forEach((animacao) => animacao.cancel());
  elemento.animate(semMovimento ? [{ opacity: 0 }, { opacity: 1 }] : QUADROS[direcao], {
    duration: semMovimento ? 160 : direcao === 'aba' ? 240 : 320,
    easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
  });
}
