import { animarEntrada, direcaoDaTroca } from './transicao-de-tela';

/** A direcao da entrada e o que diz para a pessoa "fui para frente", "voltei" ou "troquei de aba". */
describe('direcaoDaTroca', () => {
  const atalhos = ['/painel', '/gastos', '/relatorios', '/planejar'];

  it('entre atalhos da barra de baixo e troca de aba', () => {
    expect(direcaoDaTroca('/painel', '/gastos', atalhos, false)).toBe('aba');
  });

  it('de um atalho para uma tela de dentro, avanca', () => {
    expect(direcaoDaTroca('/painel', '/perfil', atalhos, false)).toBe('avancar');
    expect(direcaoDaTroca('/perfil', '/familia', atalhos, false)).toBe('avancar');
  });

  it('de uma tela de dentro para um atalho, ou pelo voltar do aparelho, volta', () => {
    expect(direcaoDaTroca('/perfil', '/painel', atalhos, false)).toBe('voltar');
    expect(direcaoDaTroca('/painel', '/perfil', atalhos, true)).toBe('voltar');
  });

  it('filtro na query ou ancora na mesma tela nao anima', () => {
    expect(direcaoDaTroca('/gastos', '/gastos?rapido=1', atalhos, false)).toBeNull();
    expect(direcaoDaTroca('/perfil', '/perfil#painel', atalhos, false)).toBeNull();
  });
});

describe('animarEntrada', () => {
  function elementoFalso() {
    const chamadas: { quadros: Keyframe[]; opcoes: KeyframeAnimationOptions }[] = [];
    const elemento = {
      getAnimations: () => [],
      animate: (quadros: Keyframe[], opcoes: KeyframeAnimationOptions) => {
        chamadas.push({ quadros, opcoes });
        return {} as Animation;
      },
    } as unknown as HTMLElement;
    return { elemento, chamadas };
  }

  function reduzirMovimento(ligado: boolean) {
    vi.stubGlobal('matchMedia', () => ({ matches: ligado }));
  }

  afterEach(() => vi.unstubAllGlobals());

  it('desliza conforme a direcao, mais rapido entre abas', () => {
    reduzirMovimento(false);
    const { elemento, chamadas } = elementoFalso();
    animarEntrada(elemento, 'avancar');
    animarEntrada(elemento, 'aba');
    expect(chamadas[0].quadros[0]['transform']).toBe('translateX(36px)');
    expect(chamadas[1].opcoes.duration).toBeLessThan(chamadas[0].opcoes.duration as number);
  });

  it('com "reduzir movimento" so aparece, sem deslizar', () => {
    reduzirMovimento(true);
    const { elemento, chamadas } = elementoFalso();
    animarEntrada(elemento, 'voltar');
    expect(chamadas[0].quadros).toEqual([{ opacity: 0 }, { opacity: 1 }]);
  });

  it('sem animacoes no navegador, nao faz nada', () => {
    expect(() => animarEntrada({} as HTMLElement, 'aba')).not.toThrow();
  });
});
