import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AReceber, ContaFixa, ContaOuCartao, Orcamento } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { dataIso, hojeIso } from '../../core/ui/datas';
import { AuthFalso } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA } from '../../testing/tela';
import { AvisosDoMes } from './avisos-do-mes';

const MES = '2026-10';
const daqui = (dias: number) => dataIso(new Date(Date.now() + dias * 86_400_000));

function conta(mudancas: Partial<ContaFixa>): ContaFixa {
  return {
    id: 'c1',
    descricao: 'Aluguel',
    categoria: 'Casa',
    valor: 1500,
    dia: 5,
    automatico: false,
    vencimento: '2026-10-05',
    situacao: 'PENDENTE',
    ...mudancas,
  };
}

function cartao(id: string, vencimento: string, total: number): ContaOuCartao {
  const fatura = {
    mes: MES,
    inicio: MES + '-01',
    ultimaCompra: MES + '-25',
    fechamento: MES + '-26',
    vencimento,
    total,
  };
  return {
    id,
    nome: `Cartão ${id}`,
    tipo: 'CARTAO',
    fechamento: 26,
    vencimento: 5,
    faturaAberta: null,
    faturaAPagar: fatura,
  };
}

/** Para ficar de olho: orcamento em alerta, fatura perto de vencer, conta em aberto e quem deve. */
describe('AvisosDoMes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AvisosDoMes],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: new AuthFalso() }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(respostas: {
    contas?: ContaFixa[] | null;
    orcamentos?: Orcamento[] | null;
    cartoes?: ContaOuCartao[] | null;
    aReceber?: AReceber[] | null;
  }): HTMLElement {
    const tela = TestBed.createComponent(AvisosDoMes);
    tela.componentRef.setInput('mes', MES);
    tela.detectChanges();
    const responder = (url: string, corpo: object | null | undefined) => {
      const pedido = http.expectOne((r) => r.url === url);
      if (corpo === null) {
        pedido.flush({ erro: 'Fora.' }, { status: 500, statusText: 'Erro' });
      } else {
        pedido.flush(corpo ?? []);
      }
    };
    responder('/api/bills', respostas.contas);
    responder('/api/budgets', respostas.orcamentos);
    responder('/api/accounts', respostas.cartoes);
    responder('/api/incomes/receivables', respostas.aReceber);
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('sem nada para avisar, nao mostra o cartao (nem com erro da API)', () => {
    expect(abrir({}).querySelector('section')).toBeNull();
    expect(
      abrir({ contas: null, orcamentos: null, cartoes: null, aReceber: null }).querySelector(
        'section',
      ),
    ).toBeNull();
  });

  it('avisa orcamento estourado e em atencao, mas nao o que esta ok', () => {
    const pagina = abrir({
      orcamentos: [
        {
          id: 'o1',
          categoria: 'Mercado',
          limite: 500,
          gasto: 600,
          percentual: 120,
          alerta: 'ESTOUROU',
        },
        {
          id: 'o2',
          categoria: 'Lazer',
          limite: 200,
          gasto: 170,
          percentual: 85,
          alerta: 'ATENCAO',
        },
        { id: 'o3', categoria: 'Casa', limite: 900, gasto: 10, percentual: 1, alerta: 'OK' },
      ],
    });
    expect(pagina.textContent).toContain('Mercado passou do limite');
    expect(pagina.textContent).toContain('Lazer já usou 85% do limite');
    expect(pagina.textContent).not.toContain('Casa');
    expect(pagina.querySelectorAll('a.atrasada').length).toBe(1);
  });

  it('avisa so a fatura fechada com valor que vence em ate 10 dias', () => {
    const pagina = abrir({
      cartoes: [
        cartao('perto', daqui(3), 800),
        cartao('longe', daqui(30), 800),
        cartao('zerada', daqui(2), 0),
      ],
    });
    expect(pagina.textContent).toContain('Cartão perto vence em');
    expect(pagina.textContent).not.toContain('Cartão longe');
    expect(pagina.textContent).not.toContain('Cartão zerada');
  });

  it('avisa conta vencida e a vencer, mas nao a paga', () => {
    const pagina = abrir({
      contas: [
        conta({ id: 'c1', descricao: 'Aluguel', situacao: 'VENCIDA' }),
        conta({ id: 'c2', descricao: 'Internet', situacao: 'PENDENTE', vencimento: '2026-10-20' }),
        conta({ id: 'c3', descricao: 'Luz', situacao: 'PAGA' }),
      ],
    });
    expect(pagina.textContent).toContain('Venceu em 05/10/2026');
    expect(pagina.textContent).toContain('Vence em 20/10/2026');
    expect(pagina.textContent).not.toContain('Luz');
  });

  it('avisa quem passou da data e ainda deve, mas nao quem esta no prazo', () => {
    const pagina = abrir({
      aReceber: [
        { id: 'r1', quem: 'Caio', descricao: 'Jantar', valor: 50, data: '2020-01-10' },
        { id: 'r2', quem: 'Duda', descricao: 'Show', valor: 80, data: hojeIso() },
      ],
    });
    expect(pagina.textContent).toContain('Caio');
    expect(pagina.textContent).toContain('Está te devendo desde 10/01/2020');
    expect(pagina.textContent).not.toContain('Duda');
  });
});
