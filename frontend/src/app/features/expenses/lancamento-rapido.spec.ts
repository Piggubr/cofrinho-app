import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso } from '../../testing/auth-falso';
import { Expenses } from './expenses';

/** O atalho "Lançar gasto" (icone, Siri, piggu://) chega em /gastos?rapido=1, com ou sem valor. */
describe('Lancamento rapido pelo atalho', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'gastos', component: Expenses }]),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: new AuthFalso() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** As listas que a tela pede ao abrir, no formato de cada uma. */
  function responderListas(): void {
    http.expectOne((pedido) => pedido.url === '/api/expenses').flush([]);
    http.expectOne('/api/categories').flush({ categorias: ['Mercado'] });
    http.expectOne((pedido) => pedido.url === '/api/expenses/splits').flush([]);
    http
      .expectOne('/api/family')
      .flush({ id: 'f', nome: 'Casa', plano: 'PREMIUM', membros: [], convites: [] });
    http.expectOne('/api/categories/rules').flush([]);
    http.expectOne('/api/accounts').flush([]);
    // Premium: sem cota de leituras, e o importador pergunta pelo banco.
    http.expectOne('/api/banking/status').flush({ habilitado: false });
  }

  it('com o valor dito a Siri, o campo ja vem preenchido e com o cursor', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/gastos?rapido=1&valor=12.50', Expenses);
    responderListas();
    harness.detectChanges();
    await harness.fixture.whenStable();

    const valor = harness.routeNativeElement!.querySelector('#valor') as HTMLInputElement;
    expect(valor.value).toBe('12,50');
    expect(document.activeElement).toBe(valor);
  });
});
