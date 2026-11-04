import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ContaFixa } from '../../core/api/models';
import { PROVEDORES_DE_TELA } from '../../testing/tela';
import { AindaDaParaGastar, calcularContaDoMes } from './ainda-da-para-gastar';

function conta(
  valor: number,
  situacao: ContaFixa['situacao'] = 'PENDENTE',
  tipo: ContaFixa['tipo'] = 'GASTO',
): ContaFixa {
  return {
    id: String(valor),
    descricao: 'Luz',
    categoria: 'Casa',
    valor,
    dia: 10,
    automatico: false,
    vencimento: '2026-11-10',
    situacao,
    tipo,
  };
}

/** 02/11/2026: faltam 29 dias no mes, hoje incluido. */
const HOJE = new Date(2026, 10, 2);

/** O numero fixo no topo do Painel (D-164), com a mesma conta dos apps nativos. */
describe('Ainda da para gastar', () => {
  it('com limite, tira o gasto e so as contas de gasto que faltam pagar', () => {
    const c = calcularContaDoMes(
      '2026-11',
      4500,
      9000,
      3260,
      // Paga e receita fixa nao sao contas a pagar.
      [conta(187), conta(119, 'VENCIDA'), conta(50, 'PAGA'), conta(6200, 'PENDENTE', 'RECEITA')],
      HOJE,
    )!;
    expect(c.tetoEhOLimite).toBe(true);
    expect(c.contasAPagar).toBe(306);
    expect(c.resta).toBe(934);
    expect(c.porDia).toBe(32.2);
  });

  it('sem limite, o teto sao as receitas; sem os dois, nao ha numero', () => {
    const c = calcularContaDoMes('2026-11', 0, 6200, 1000, [conta(500)], HOJE)!;
    expect(c.tetoEhOLimite).toBe(false);
    expect(c.resta).toBe(4700);
    expect(calcularContaDoMes('2026-11', 0, 0, 10, [], HOJE)).toBeNull();
  });

  it('passou do teto ou mes passado: sem valor por dia', () => {
    expect(calcularContaDoMes('2026-11', 100, 0, 150, [], HOJE)).toEqual(
      expect.objectContaining({ resta: -50, porDia: null }),
    );
    expect(calcularContaDoMes('2026-10', 100, 0, 10, [], HOJE)!.porDia).toBeNull();
  });

  describe('cartao', () => {
    let http: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [AindaDaParaGastar],
        providers: PROVEDORES_DE_TELA,
      });
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    function abrir(
      limite: number,
      gasto: number,
      receitas: number,
      contas: ContaFixa[],
    ): HTMLElement {
      const tela = TestBed.createComponent(AindaDaParaGastar);
      tela.componentRef.setInput('mes', '2020-01');
      tela.componentRef.setInput('limite', limite);
      tela.componentRef.setInput('gasto', gasto);
      tela.detectChanges();
      http.expectOne((r) => r.url.endsWith('/reports/month')).flush({ receitas });
      http.expectOne((r) => r.url.endsWith('/bills')).flush(contas);
      tela.detectChanges();
      return tela.nativeElement;
    }

    it('mostra o valor negativo com sinal, o aviso e a conta', () => {
      const texto = abrir(100, 150, 0, [conta(20)]).textContent!;
      expect(texto).toContain('Sobrou em janeiro de 2020');
      expect(texto).toMatch(/-\D*70,00/);
      expect(texto).toContain('Já passou do que dava para gastar este mês.');
      expect(texto).toContain('de contas a pagar');
    });

    it('sem teto, leva ao limite e as receitas', () => {
      const pagina = abrir(0, 10, 0, []);
      const links = [...pagina.querySelectorAll('a')].map((a) => a.getAttribute('href'));
      expect(links).toEqual(['/orcamentos', '/receitas']);
    });
  });
});
