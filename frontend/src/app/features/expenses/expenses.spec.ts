import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Expenses } from './expenses';

/** Leitura de nota no gratuito: mostra quantas sobram e, sem sobra, leva ao Premium. */
describe('Expenses', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.configureTestingModule({
      imports: [Expenses],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(restantes: number): HTMLElement {
    const tela = TestBed.createComponent(Expenses);
    tela.detectChanges();
    http.expectOne((pedido) => pedido.url.startsWith('/api/expenses')).flush([]);
    http.expectOne('/api/categories').flush({ categorias: ['Mercado'] });
    http.expectOne('/api/receipts/usage').flush({ usadas: 10 - restantes, limite: 10, restantes });
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('com leituras sobrando mostra o botao e quantas restam', () => {
    const pagina = abrir(3);
    expect(pagina.textContent).toContain('Restam 3 leituras');
    expect(pagina.querySelector('input[type="file"]')).not.toBeNull();
  });

  it('sem leituras no mes troca o botao pelo convite ao Premium', () => {
    const pagina = abrir(0);
    expect(pagina.querySelector('input[type="file"]')).toBeNull();
    expect(pagina.textContent).toContain('leituras grátis deste mês acabaram');
  });
});
