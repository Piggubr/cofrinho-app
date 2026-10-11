import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Budgets } from './budgets';

/** O alerta aparece na barra; no gratuito, o formulario vira convite ao Premium. */
describe('Budgets', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Budgets],
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

  function abrir(): HTMLElement {
    const tela = TestBed.createComponent(Budgets);
    tela.detectChanges();
    http.expectOne('/api/categories').flush({ categorias: ['Lazer', 'Mercado'] });
    http.expectOne((r) => r.url === '/api/budgets').flush([
      { id: 'o1', categoria: 'Mercado', limite: 100, gasto: 120, percentual: 120, alerta: 'ESTOUROU' },
    ]);
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('categoria estourada aparece com a barra marcada', () => {
    const pagina = abrir();
    expect(pagina.querySelector('.progresso.estourado')).not.toBeNull();
    expect(pagina.textContent).toContain('120%');
  });

  it('no gratuito nao ha formulario, so o convite', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const pagina = abrir();
    expect(pagina.querySelector('#limite-orcamento')).toBeNull();
    expect(pagina.textContent).toContain('Piggu Premium');
  });
});
