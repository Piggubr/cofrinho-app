import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ContaOuCartao } from '../../core/api/models';
import { PessoasDaFamilia, nomeNaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso } from '../../testing/auth-falso';
import { ID_DA_BIA, ID_DA_TITULAR, PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Accounts } from './accounts';

const FATURA = {
  mes: '2026-10',
  inicio: '2026-09-05',
  ultimaCompra: '2026-10-04',
  fechamento: '2026-10-05',
  vencimento: '2026-10-12',
  total: 80,
};

function conta(mudancas: Partial<ContaOuCartao>): ContaOuCartao {
  return {
    id: 'c1',
    nome: 'Nubank',
    tipo: 'CARTAO',
    fechamento: 5,
    vencimento: 12,
    faturaAberta: FATURA,
    faturaAPagar: { ...FATURA, mes: '2026-09', total: 200 },
    usuario: ID_DA_TITULAR,
    ...mudancas,
  };
}

/** Cartoes e bancos de quem mora com alguem: criar, compartilhar, apagar e andar nas faturas. */
describe('Accounts: acoes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [
        ...PROVEDORES_DE_TELA,
        { provide: AuthService, useValue: new AuthFalso() },
        {
          provide: PessoasDaFamilia,
          useValue: {
            nome: (id: string | null) =>
              nomeNaFamilia(
                [
                  {
                    id: ID_DA_BIA,
                    nome: 'Bia',
                    email: 'b@x',
                    foto: null,
                    papel: 'PARCEIRO',
                    premium: false,
                  },
                ],
                id,
              ),
            temCompanhia: () => true,
            parceiro: () => null,
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function abrir(contas: ContaOuCartao[]) {
    const tela = TestBed.createComponent(Accounts);
    http.expectOne('/api/accounts').flush(contas);
    tela.detectChanges();
    return tela;
  }

  function alerta(tela: ComponentFixture<Accounts>) {
    tela.detectChanges();
    return (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent?.trim();
  }

  it('cartao novo: o fechamento acompanha o vencimento e vai compartilhado', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Nenhuma conta ainda.');

    clicar(pagina, 'Salvar');
    expect(alerta(tela)).toBe('Digite o nome da conta ou do cartão.');

    digitar(pagina, '#nome-conta', ' Nubank ');
    clicar(pagina, 'Salvar');
    expect(alerta(tela)).toBe('Informe o dia do vencimento da fatura (1 a 31).');

    digitar(pagina, '#vencimento', '12');
    tela.detectChanges();
    expect(tela.componentInstance['fechamento']()).toBe(5);
    digitar(pagina, '#vencimento', '20');
    expect(tela.componentInstance['fechamento']()).toBe(13);

    digitar(pagina, '#fechamento', '40');
    clicar(pagina, 'Salvar');
    expect(alerta(tela)).toBe('Informe o dia do fechamento (1 a 31).');
    digitar(pagina, '#vencimento', '25');
    expect(tela.componentInstance['fechamento']()).toBe(40);

    digitar(pagina, '#fechamento', '18');
    pagina.querySelector<HTMLInputElement>('label.dica input[type=checkbox]')!.click();
    clicar(pagina, 'Salvar');
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/accounts');
    expect(pedido.request.body).toEqual({
      nome: 'Nubank',
      tipo: 'CARTAO',
      fechamento: 18,
      vencimento: 25,
      compartilhado: true,
    });
    pedido.flush(conta({}));
    http.expectOne('/api/accounts').flush([conta({ compartilhado: true })]);
    tela.detectChanges();
    expect(pagina.textContent).toContain('do casal');
    expect(pagina.textContent).toContain('A pagar:');
    expect(tela.componentInstance['nome']()).toBe('');
  });

  it('conta bancaria vai sem fechamento; erro ao salvar aparece', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#nome-conta', 'Itaú');
    digitar(pagina, '#tipo-conta', 'CONTA');
    tela.detectChanges();
    expect(pagina.querySelector('#vencimento')).toBeNull();
    clicar(pagina, 'Salvar');

    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body).toMatchObject({
      tipo: 'CONTA',
      fechamento: null,
      vencimento: null,
      compartilhado: false,
    });
    pedido.flush({ erro: 'Nome repetido.' }, { status: 409, statusText: 'x' });
    expect(alerta(tela)).toBe('Nome repetido.');
  });

  it('so quem criou compartilha ou apaga; a conta da outra pessoa mostra de quem e', () => {
    const tela = abrir([
      conta({ compartilhado: true }),
      conta({
        id: 'b1',
        nome: 'Conta da Bia',
        tipo: 'CONTA',
        usuario: ID_DA_BIA,
        faturaAberta: null,
      }),
    ]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('De Bia');
    expect(pagina.querySelectorAll('button[aria-label=Apagar]').length).toBe(1);

    clicar(pagina, 'Deixar só meu');
    const pedido = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/accounts/c1/sharing',
    );
    expect(pedido.request.body).toEqual({ compartilhado: false });
    pedido.flush(null);
    http.expectOne('/api/accounts').flush([conta({ compartilhado: false })]);
    tela.detectChanges();

    clicar(pagina, 'Compartilhar');
    http
      .expectOne((r) => r.method === 'PUT')
      .flush({ erro: 'Só quem criou.' }, { status: 403, statusText: 'x' });
    expect(alerta(tela)).toBe('Só quem criou.');
  });

  it('apaga depois de confirmar e fecha a fatura aberta; desistir nao chama a API', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = abrir([conta({})]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    clicar(pagina, 'Ver fatura');
    http
      .expectOne((r) => r.url === '/api/accounts/c1/statement')
      .flush({ fatura: FATURA, gastos: [] });
    tela.detectChanges();
    expect(pagina.textContent).toContain('Nenhum gasto nesta fatura.');

    confirmar.mockReturnValue(true);
    clicar(pagina, 'Apagar');
    expect(confirmar.mock.calls[1][0]).toContain('Nubank');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/accounts/c1').flush(null);
    http.expectOne('/api/accounts').flush([]);
    tela.detectChanges();
    expect(pagina.querySelector('#titulo-fatura')).toBeNull();

    tela.componentInstance['contas'].set([conta({})]);
    tela.detectChanges();
    clicar(pagina, 'Apagar');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Não deu.' }, { status: 500, statusText: 'x' });
    expect(alerta(tela)).toBe('Não deu.');
  });

  it('anda para a fatura anterior e a seguinte, inclusive na virada do ano', () => {
    const tela = abrir([conta({ faturaAberta: { ...FATURA, mes: '2026-12' } })]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Ver fatura');
    http
      .expectOne((r) => r.params.get('mes') === '2026-12')
      .flush({ fatura: { ...FATURA, mes: '2026-12' }, gastos: [] });
    tela.detectChanges();

    clicar(pagina, 'Próxima fatura');
    http
      .expectOne((r) => r.params.get('mes') === '2027-01')
      .flush({ fatura: { ...FATURA, mes: '2027-01' }, gastos: [] });
    tela.detectChanges();
    expect(pagina.querySelector('#titulo-fatura')?.textContent).toContain('2027');

    clicar(pagina, 'Fatura anterior');
    http
      .expectOne((r) => r.params.get('mes') === '2026-12')
      .flush({ erro: 'Sem fatura.' }, { status: 404, statusText: 'x' });
    expect(alerta(tela)).toBe('Sem fatura.');
  });

  it('erro ao carregar as contas aparece', () => {
    const tela = TestBed.createComponent(Accounts);
    http
      .expectOne('/api/accounts')
      .flush({ erro: 'Fora do ar.' }, { status: 503, statusText: 'x' });
    expect(alerta(tela)).toBe('Fora do ar.');
  });
});
