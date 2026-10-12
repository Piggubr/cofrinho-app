import { direcaoDaTroca } from './transicao-de-tela';

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
