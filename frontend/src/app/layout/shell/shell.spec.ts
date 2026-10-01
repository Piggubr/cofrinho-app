import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso } from '../../testing/auth-falso';
import { Shell } from './shell';

/** A cotacao do topo segue as preferencias de moeda da pessoa. */
describe('Shell', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Shell],
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
    const tela = TestBed.createComponent(Shell);
    tela.detectChanges();
    return tela;
  }

  it('consulta o par escolhido e mostra a taxa na moeda de conversao', () => {
    auth.preferir({ moeda: 'USD', moedaConversao: 'BRL' });
    const tela = abrir();

    const pedido = http.expectOne((req) => req.url === '/api/exchange-rate');
    expect(pedido.request.params.get('de')).toBe('USD');
    expect(pedido.request.params.get('para')).toBe('BRL');
    pedido.flush({
      de: 'USD',
      para: 'BRL',
      taxa: 5.4,
      data: '',
      fonte: '',
      estimativa: true,
      desatualizada: false,
    });
    tela.detectChanges();

    const texto = tela.nativeElement.querySelector('.cotacao').textContent;
    expect(texto).toContain('USD →');
    expect(texto).toContain('R$');
    expect(texto).toContain('5,40');
  });

  it('com a cotacao desligada nao pede nada e nao mostra nada', () => {
    auth.preferir({ mostrarCotacao: false });
    const tela = abrir();

    http.expectNone((req) => req.url === '/api/exchange-rate');
    expect(tela.nativeElement.querySelector('.cotacao')).toBeNull();
  });

  it('trocar a moeda no perfil refaz a consulta', () => {
    const tela = abrir();
    http
      .expectOne((req) => req.url === '/api/exchange-rate')
      .flush({
        de: 'EUR',
        para: 'BRL',
        taxa: 6.15,
        data: '',
        fonte: '',
        estimativa: true,
        desatualizada: false,
      });

    auth.preferir({ moedaConversao: 'USD' });
    tela.detectChanges();

    const novo = http.expectOne((req) => req.url === '/api/exchange-rate');
    expect(novo.request.params.get('para')).toBe('USD');
    novo.flush({
      de: 'EUR',
      para: 'USD',
      taxa: 1.1,
      data: '',
      fonte: '',
      estimativa: true,
      desatualizada: false,
    });
  });
});
