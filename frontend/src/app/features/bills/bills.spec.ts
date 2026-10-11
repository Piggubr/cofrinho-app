import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ContaFixa } from '../../core/api/models';
import { APP_CONFIG } from '../../core/config/app-config';
import { Bills } from './bills';

const VENCIDA: ContaFixa = {
  id: 'c1',
  descricao: 'Aluguel',
  categoria: 'Casa',
  valor: 1500,
  dia: 5,
  automatico: false,
  vencimento: '2026-10-05',
  situacao: 'VENCIDA',
};

/** Marcar como paga lanca o gasto do mes mostrado na tela. */
describe('Bills', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Bills],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('conta vencida aparece e, marcada como paga, vai para o backend com o mes da tela', () => {
    const tela = TestBed.createComponent(Bills);
    tela.detectChanges();
    http.expectOne('/api/categories').flush({ categorias: ['Casa'] });
    const lista = http.expectOne((r) => r.url === '/api/bills' && r.method === 'GET');
    const mes = lista.request.params.get('mes')!;
    lista.flush([VENCIDA]);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Vencida');

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Marcar como paga'))!
      .click();
    const pagamento = http.expectOne((r) => r.url === '/api/bills/c1/pay');
    expect(pagamento.request.params.get('mes')).toBe(mes);
    pagamento.flush({});
    http.expectOne((r) => r.url === '/api/bills').flush([{ ...VENCIDA, situacao: 'PAGA' }]);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Conta lançada nos gastos.');
  });
});
