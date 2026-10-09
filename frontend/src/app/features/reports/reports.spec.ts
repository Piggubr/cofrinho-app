import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Reports } from './reports';

const RESUMO = {
  mes: '2026-09',
  receitas: 1000,
  gastos: 300,
  sobra: 700,
  taxaDePoupanca: 70,
  gastosMesAnterior: 200,
  variacao: 50,
  projecaoDeGastos: 900,
  porCategoria: [{ categoria: 'Mercado', total: 300, anterior: 200 }],
};

const ANO = {
  ano: 2026,
  receitas: 1000,
  gastos: 300,
  sobra: 700,
  meses: Array.from({ length: 12 }, (_, i) => ({
    mes: `2026-${String(i + 1).padStart(2, '0')}`,
    receitas: i === 8 ? 1000 : 0,
    gastos: i === 8 ? 300 : 0,
    sobra: i === 8 ? 700 : 0,
  })),
  porCategoria: [{ categoria: 'Mercado', total: 300 }],
};

/** O mes e gratis; o ano e Premium e vem com grafico e tabela. */
describe('Reports', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Reports],
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

  it('mostra comparacao, projecao e o ano com 12 meses na tabela', () => {
    const tela = TestBed.createComponent(Reports);
    http.expectOne((r) => r.url === '/api/reports/month').flush(RESUMO);
    http.expectOne((r) => r.url === '/api/reports/year').flush(ANO);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('50% a mais');
    expect(pagina.textContent).toContain('Projeção do mês');
    expect(pagina.querySelectorAll('.tabela tbody tr').length).toBe(12);
    expect(pagina.querySelectorAll('path.barra.gasto[d^="M"]').length).toBe(1);
  });

  it('no gratuito o ano vira convite e nem e pedido', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const tela = TestBed.createComponent(Reports);
    http.expectOne((r) => r.url === '/api/reports/month').flush(RESUMO);
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Piggu Premium');
    expect(tela.nativeElement.querySelector('svg')).toBeNull();
  });
});
