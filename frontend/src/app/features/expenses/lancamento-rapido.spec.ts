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

  /** As listas que a tela e o formulario pedem ao abrir, no formato de cada uma. */
  function responderListas(): void {
    const corpos: Record<string, unknown> = {
      '/api/expenses': [],
      '/api/categories': { categorias: ['Mercado'] },
      '/api/expenses/splits': [],
      '/api/family': { id: 'f', nome: 'Casa', plano: 'PREMIUM', membros: [], convites: [] },
      '/api/categories/rules': [],
      '/api/accounts': [],
      // Premium: sem cota de leituras, e o importador pergunta pelo banco.
      '/api/banking/status': { habilitado: false },
    };
    // Responder umas listas pede outras (o acerto vem depois dos gastos).
    for (let pedidos = http.match(() => true); pedidos.length; pedidos = http.match(() => true)) {
      for (const pedido of pedidos) {
        expect(corpos).toHaveProperty([pedido.request.url]);
        pedido.flush(corpos[pedido.request.url] as object);
      }
    }
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
