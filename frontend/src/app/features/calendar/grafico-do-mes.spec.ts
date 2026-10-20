import { TestBed } from '@angular/core/testing';
import { Receita } from '../../core/api/models';
import { PROVEDORES_DE_TELA, gastoDeTeste } from '../../testing/tela';
import { GraficoDoMes, serieDoMes, topoDaEscala } from './grafico-do-mes';

function receita(mudancas: Partial<Receita> = {}): Receita {
  return {
    id: 'r1',
    data: '2026-09-05',
    descricao: 'Salário',
    categoria: 'Salário',
    valor: 3000,
    usuario: null,
    ...mudancas,
  };
}

/** Receitas e gastos acumulados no mes, com a tabela por semana. */
describe('GraficoDoMes', () => {
  const gastos = [
    gastoDeTeste({ data: '2026-09-02', valor: 100 }),
    gastoDeTeste({ id: 'g2', data: '2026-09-02', valor: 50 }),
    gastoDeTeste({ id: 'g3', data: '2026-09-20', valor: 400 }),
    gastoDeTeste({ id: 'g4', data: '2026-08-31', valor: 999 }),
  ];
  const receitas = [receita(), receita({ id: 'r2', data: '2026-09-15', valor: 200 })];

  describe('serieDoMes', () => {
    it('mes passado vai ate o ultimo dia, acumulando e ignorando o que e de outro mes', () => {
      const serie = serieDoMes('2026-09', gastos, receitas, '2026-10-20');

      expect(serie).toHaveLength(30);
      expect(serie[1]).toMatchObject({
        dia: 2,
        iso: '2026-09-02',
        gastos: 150,
        gastosAteAqui: 150,
      });
      expect(serie[4]).toMatchObject({ receitas: 3000, receitasAteAqui: 3000 });
      expect(serie.at(-1)).toMatchObject({ receitasAteAqui: 3200, gastosAteAqui: 550 });
    });

    it('mes corrente para em hoje e mes futuro fica vazio', () => {
      expect(serieDoMes('2026-09', gastos, receitas, '2026-09-10')).toHaveLength(10);
      expect(serieDoMes('2026-11', gastos, receitas, '2026-10-20')).toEqual([]);
    });
  });

  it('a escala termina num numero redondo acima do maior valor', () => {
    expect(topoDaEscala(0)).toBe(1);
    expect(topoDaEscala(3200)).toBe(4000);
    expect(topoDaEscala(10700)).toBe(12000);
    expect(topoDaEscala(1800)).toBe(2000);
    expect(topoDaEscala(2100)).toBe(2500);
    expect(topoDaEscala(100)).toBe(100);
  });

  function abrir(mes = '2026-09', comGastos = gastos, comReceitas = receitas) {
    TestBed.configureTestingModule({ imports: [GraficoDoMes], providers: PROVEDORES_DE_TELA });
    const tela = TestBed.createComponent(GraficoDoMes);
    tela.componentRef.setInput('mes', mes);
    tela.componentRef.setInput('gastos', comGastos);
    tela.componentRef.setInput('receitas', comReceitas);
    tela.detectChanges();
    return tela;
  }

  it('mostra os totais, as duas linhas e a tabela por semana', () => {
    const pagina: HTMLElement = abrir().nativeElement;

    expect(pagina.querySelector('.numeros')?.textContent).toContain('3.200,00');
    expect(pagina.querySelector('.numeros')?.textContent).toContain('550,00');
    expect(pagina.querySelector('.sobra')?.textContent).toContain('2.650,00');
    expect(pagina.querySelector('path.receita')?.getAttribute('d')).toMatch(
      /^M[\d.]+,[\d.]+(L[\d.]+,[\d.]+){29}$/,
    );
    expect(pagina.querySelector('path.gasto')?.getAttribute('d')).toBeTruthy();

    const linhas = [...pagina.querySelectorAll('tbody tr')].map((tr) =>
      tr.textContent!.replace(/\s+/g, ' '),
    );
    expect(linhas).toHaveLength(5);
    expect(linhas[0]).toContain('1 a 7');
    expect(linhas[0]).toContain('3.000,00');
    expect(linhas[4]).toContain('29 a 30');
  });

  it('sem nada no mes, avisa em vez de desenhar', () => {
    const pagina: HTMLElement = abrir('2026-09', [], []).nativeElement;

    expect(pagina.querySelector('svg')).toBeNull();
    expect(pagina.textContent).toContain('Nenhuma receita ou gasto');
  });

  /** Sem ResizeObserver no teste, o grafico tem 600px: o dia 15 de 30 fica em x = 10 + 14/29 * 580. */
  function apontar(
    tela: ReturnType<typeof abrir>,
    tipo: string,
    clientX: number,
    pointerType = 'mouse',
  ) {
    const evento = new MouseEvent(tipo, { clientX });
    Object.defineProperty(evento, 'pointerType', { value: pointerType });
    (tela.nativeElement as HTMLElement).querySelector('.grafico')!.dispatchEvent(evento);
    tela.detectChanges();
  }

  it('passar o mouse num dia mostra o acumulado ate ele e o que entrou e saiu no dia', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    apontar(tela, 'pointermove', 10 + (14 / 29) * 580);

    const dica = pagina.querySelector('.dica')!.textContent!.replace(/\s+/g, ' ');
    expect(dica).toContain('Até o dia 15');
    expect(dica).toContain('3.200,00');
    expect(dica).toContain('150,00');
    expect(dica).toContain('Entrou no dia +');
    expect(dica).not.toContain('Saiu no dia');
    expect(pagina.querySelectorAll('circle.ponto')).toHaveLength(2);

    apontar(tela, 'pointerleave', 0);
    expect(pagina.querySelector('.dica')).toBeNull();
  });

  it('no toque a dica fica depois de tirar o dedo, e fora do grafico vale o dia mais perto', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    apontar(tela, 'pointerdown', 2000, 'touch');
    apontar(tela, 'pointerleave', 2000, 'touch');
    expect(pagina.querySelector('.dica')?.textContent).toContain('Até o dia 30');

    apontar(tela, 'pointermove', -50);
    expect(pagina.querySelector('.dica')?.textContent).toContain('Até o dia 1');
  });

  it('num mes futuro nada aconteceu ainda, mesmo com receita lancada para la', () => {
    const pagina: HTMLElement = abrir(
      '2099-01',
      [],
      [receita({ data: '2099-01-02' })],
    ).nativeElement;

    expect(pagina.textContent).toContain('Nenhuma receita ou gasto');
  });

  it('o dia aberto no calendario fica marcado, sem a dica', () => {
    const tela = abrir();
    tela.componentRef.setInput('diaSelecionado', '2026-09-02');
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.querySelector('line.cursor')).not.toBeNull();
    expect(pagina.querySelector('.dica')).toBeNull();
  });

  it('as setas andam pelos dias', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const grafico = pagina.querySelector('.grafico')!;
    const tecla = (key: string) => {
      grafico.dispatchEvent(new KeyboardEvent('keydown', { key }));
      tela.detectChanges();
    };

    tecla('ArrowLeft');
    expect(pagina.querySelector('.dica')?.textContent).toContain('Até o dia 29');
    tecla('Home');
    expect(pagina.querySelector('.dica')?.textContent).toContain('Até o dia 1');
    tecla('ArrowRight');
    tecla('a');
    expect(pagina.querySelector('.dica')?.textContent).toContain('Até o dia 2');
  });
});
