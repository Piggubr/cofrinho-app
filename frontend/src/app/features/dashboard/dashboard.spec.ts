import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Dashboard } from './dashboard';

/**
 * Painel: o Open Finance e opcional. Desligado, ou com o servico fora do ar, o card
 * de bancos nao aparece e o resto do painel segue normal.
 */
describe('Dashboard', () => {
  let http: HttpTestingController;

  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir() {
    const tela = TestBed.createComponent(Dashboard);
    tela.detectChanges();
    http
      .expectOne('/api/piggy-bank')
      .flush({ depositos: [], totalDepositos: 100, totalGastos: 0, saldo: 100 });
    http.expectOne((req) => req.url === '/api/expenses').flush([]);
    http.expectOne('/api/monthly-goals').flush({});
    // Cards que buscam o proprio dado: aqui sem conteudo, cada um tem o proprio teste.
    tela.detectChanges();
    for (const extra of http.match((req) => /\/api\/(reports|bills|budgets|accounts)/.test(req.url))) {
      extra.flush(extra.request.url.includes('reports') ? null : []);
    }
    return tela;
  }

  function cardDeBancos(tela: ReturnType<typeof abrir>): Element | null {
    return tela.nativeElement.querySelector('#titulo-bancos');
  }

  it('Open Finance desligado: sem card e sem buscar contas', () => {
    const tela = abrir();
    http.expectOne('/api/banking/status').flush({ habilitado: false });
    tela.detectChanges();

    expect(cardDeBancos(tela)).toBeNull();
    http.expectNone('/api/banking/accounts');
  });

  it('servico de bancos fora do ar: sem card e sem mensagem de erro', () => {
    const tela = abrir();
    http.expectOne('/api/banking/status').flush(null, { status: 503, statusText: 'Indisponivel' });
    tela.detectChanges();

    expect(cardDeBancos(tela)).toBeNull();
    expect(tela.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('Open Finance ligado: mostra as contas com o saldo na moeda da conta', () => {
    const tela = abrir();
    http.expectOne('/api/banking/status').flush({ habilitado: true });
    http.expectOne('/api/banking/accounts').flush([
      {
        id: 'c1',
        conexaoId: 'x1',
        instituicao: 'Nubank',
        nome: 'NuConta',
        tipo: 'CHECKING_ACCOUNT',
        numero: '',
        saldo: 1520.35,
        moeda: 'BRL',
        status: 'UPDATED',
        atualizadoEm: null,
      },
    ]);
    tela.detectChanges();

    expect(cardDeBancos(tela)).not.toBeNull();
    const texto = tela.nativeElement.textContent;
    expect(texto).toContain('NuConta');
    expect(texto).toContain('R$');
    expect(texto).toContain('1.520,35');
  });

  it('valores do cofrinho aparecem na moeda escolhida pela pessoa', () => {
    const tela = abrir();
    http.expectOne('/api/banking/status').flush({ habilitado: false });
    tela.detectChanges();

    expect(tela.nativeElement.textContent).toContain('€');
  });

  it('no gratuito o banco ja conectado ainda pode ser desconectado', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = abrir();
    http.expectOne('/api/banking/status').flush({ habilitado: true });
    http
      .expectOne('/api/banking/accounts')
      .flush([
        {
          id: 'c1',
          conexaoId: 'x1',
          instituicao: 'Nubank',
          nome: 'NuConta',
          tipo: '',
          numero: '',
          saldo: 1,
          moeda: 'BRL',
          status: 'UPDATED',
          atualizadoEm: null,
        },
      ]);
    tela.detectChanges();

    [...tela.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Desconectar'))!
      .click();
    const pedido = http.expectOne('/api/banking/connections/x1');
    expect(pedido.request.method).toBe('DELETE');
    pedido.flush([]);
    tela.detectChanges();

    expect(tela.nativeElement.textContent).toContain('Nenhum banco conectado');
  });
});
