import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ContaBancaria } from '../../core/api/models';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { PluggyConnectService } from '../../core/banking/pluggy-connect.service';
import { mesKey } from '../../core/ui/datas';
import { AuthFalso } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, clicar, digitar, gastoDeTeste } from '../../testing/tela';
import { Dashboard } from './dashboard';

const NUCONTA: ContaBancaria = {
  id: 'c1',
  conexaoId: 'x1',
  instituicao: 'Nubank',
  nome: 'NuConta',
  tipo: 'CHECKING_ACCOUNT',
  numero: '1234',
  saldo: 100,
  moeda: 'BRL',
  status: 'UPDATED',
  atualizadoEm: null,
};

/** Painel: cofrinho, limite do mes, troca de mes e os bancos do Open Finance. */
describe('Dashboard (acoes)', () => {
  let http: HttpTestingController;
  const pluggy = { conectar: vi.fn() };

  beforeEach(() => {
    pluggy.conectar.mockReset();
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        ...PROVEDORES_DE_TELA,
        { provide: AuthService, useValue: new AuthFalso() },
        {
          provide: PessoasDaFamilia,
          useValue: { temCompanhia: signal(false), nome: () => 'Titular' },
        },
        { provide: PluggyConnectService, useValue: pluggy },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function responderCards(): void {
    for (const extra of http.match((r) =>
      /\/api\/(reports|bills|budgets|accounts|incomes\/receivables|piggy-bank\/goals|personal-goals|girls-math|notes)/.test(
        r.url,
      ),
    )) {
      extra.flush(extra.request.url.includes('reports') ? null : []);
    }
  }

  function responderPainel(
    gastos = [
      gastoDeTeste({ valor: 60 }),
      gastoDeTeste({ id: 'g2', categoria: 'Lazer', valor: 40 }),
    ],
    meta = 80,
  ) {
    http.expectOne('/api/piggy-bank').flush({
      depositos: [{ id: 'd1', valor: 30, data: '2026-10-02', usuario: null }],
      totalDepositos: 30,
      totalGastos: 0,
      saldo: 30,
    });
    http.expectOne((r) => r.url === '/api/expenses').flush(gastos);
    http.expectOne('/api/monthly-goals').flush({ [mesKey(new Date())]: meta });
  }

  async function abrir(
    bancos: { habilitado: boolean; contas?: ContaBancaria[] | null } = { habilitado: false },
  ) {
    const tela = TestBed.createComponent(Dashboard);
    tela.detectChanges();
    responderPainel();
    tela.detectChanges();
    responderCards();
    http.expectOne('/api/banking/status').flush({ habilitado: bancos.habilitado });
    if (bancos.habilitado) {
      const pedido = http.expectOne('/api/banking/accounts');
      if (bancos.contas === null) {
        pedido.flush({ erro: 'Banco fora.' }, { status: 502, statusText: 'Erro' });
      } else {
        pedido.flush(bancos.contas ?? []);
      }
    }
    tela.detectChanges();
    await tela.whenStable();
    tela.detectChanges();
    return tela;
  }

  it('mostra o limite estourado e o gasto por categoria', async () => {
    const pagina: HTMLElement = (await abrir()).nativeElement;
    expect(pagina.textContent).toContain('100% do limite');
    expect(pagina.textContent).toContain('limite ultrapassado');
  });

  it('deposito: recusa valor vazio, deposita e recarrega o cofrinho', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Depositar');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Digite um valor de depósito válido.');

    digitar(pagina, '#valorDeposito', '25');
    clicar(pagina, 'Depositar');
    const pedido = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/piggy-bank/deposits',
    );
    expect(pedido.request.body.valor).toBe(25);
    pedido.flush({ id: 'd2' });
    http
      .expectOne('/api/piggy-bank')
      .flush({ depositos: [], totalDepositos: 55, totalGastos: 0, saldo: 55 });
    tela.detectChanges();
    expect(pagina.querySelector('[role="alert"]')).toBeNull();
  });

  it('deposito com erro da API mostra o aviso', async () => {
    const tela = await abrir();
    digitar(tela.nativeElement, '#valorDeposito', '25');
    clicar(tela.nativeElement, 'Depositar');
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ erro: 'Nao deu.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Nao deu.');
  });

  it('apagar deposito pede confirmacao', async () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    const tela = await abrir();
    const apagar = () =>
      tela.nativeElement.querySelector('.lista-de-depositos button[aria-label="Apagar"]').click();
    apagar();
    expect(confirmar).toHaveBeenCalled();
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    apagar();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/piggy-bank/deposits/d1')
      .flush(null);
    http.expectOne('/api/piggy-bank').flush({ erro: 'Fora.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('trocar de mes busca os gastos do outro mes; erro aparece', async () => {
    const tela = await abrir();
    clicar(tela.nativeElement, 'Próximo mês');
    http
      .expectOne('/api/piggy-bank')
      .flush({ depositos: [], totalDepositos: 0, totalGastos: 0, saldo: 0 });
    const gastos = http.expectOne((r) => r.url === '/api/expenses');
    const proximo = new Date();
    proximo.setDate(1);
    proximo.setMonth(proximo.getMonth() + 1);
    expect(gastos.request.params.get('mes')).toBe(mesKey(proximo));
    http.expectOne('/api/monthly-goals').flush({});
    gastos.flush({ erro: 'Fora.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    responderCards();
    expect(tela.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('bancos: erro ao listar aparece no card', async () => {
    const tela = await abrir({ habilitado: true, contas: null });
    expect(tela.nativeElement.textContent).toContain('Banco fora.');
  });

  it('bancos: atualizar saldos, com sucesso e com erro', async () => {
    const tela = await abrir({ habilitado: true, contas: [NUCONTA] });
    clicar(tela.nativeElement, 'Atualizar saldos');
    http.expectOne('/api/banking/sync').flush([{ ...NUCONTA, saldo: 250 }]);
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('250,00');

    clicar(tela.nativeElement, 'Atualizar saldos');
    http
      .expectOne('/api/banking/sync')
      .flush({ erro: 'Banco lento.' }, { status: 502, statusText: 'Erro' });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Banco lento.');
  });

  it('bancos: desconectar cancelado nao chama a API; erro aparece', async () => {
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    const tela = await abrir({ habilitado: true, contas: [NUCONTA] });
    clicar(tela.nativeElement, 'Desconectar');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    clicar(tela.nativeElement, 'Desconectar');
    http
      .expectOne('/api/banking/connections/x1')
      .flush({ erro: 'Nao desconectou.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Nao desconectou.');
  });

  it('bancos: conectar pede autorizacao e mostra as contas novas', async () => {
    pluggy.conectar.mockResolvedValue([NUCONTA]);
    const tela = await abrir({ habilitado: true, contas: [] });
    clicar(tela.nativeElement, 'Conectar banco');
    tela.detectChanges();
    clicar(tela.nativeElement, 'Cancelar');
    tela.detectChanges();
    expect(pluggy.conectar).not.toHaveBeenCalled();

    clicar(tela.nativeElement, 'Conectar banco');
    tela.detectChanges();
    clicar(tela.nativeElement, 'Autorizo');
    await tela.whenStable();
    tela.detectChanges();
    expect(pluggy.conectar).toHaveBeenCalled();
    expect(tela.nativeElement.textContent).toContain('NuConta');
  });

  it('bancos: erro do widget aparece com o texto dele; fechar sem conectar nao muda nada', async () => {
    pluggy.conectar
      .mockRejectedValueOnce(new Error('Widget fechou com erro.'))
      .mockResolvedValueOnce(null);
    const tela = await abrir({ habilitado: true, contas: [] });
    for (let vez = 0; vez < 2; vez++) {
      clicar(tela.nativeElement, 'Conectar banco');
      tela.detectChanges();
      clicar(tela.nativeElement, 'Autorizo');
      await tela.whenStable();
      tela.detectChanges();
      if (vez === 0) {
        expect(tela.nativeElement.textContent).toContain('Widget fechou com erro.');
      }
    }
    expect(tela.nativeElement.textContent).toContain('Nenhum banco conectado');
  });
});
