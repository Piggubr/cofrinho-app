import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { Incomes } from './incomes';

/** Lancar receita e ver o mes fechar a conta. */
describe('Incomes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Incomes],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lanca a receita e mostra o resumo com a taxa de poupanca', () => {
    const tela = TestBed.createComponent(Incomes);
    tela.detectChanges();
    http.expectOne('/api/incomes/categories').flush(['Salário', 'Outros']);
    http.expectOne((r) => r.url === '/api/incomes').flush([]);
    http
      .expectOne((r) => r.url === '/api/reports/month')
      .flush({ mes: '2026-10', receitas: 5000, gastos: 1250, sobra: 3750, taxaDePoupanca: 75 });
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('75% poupado');

    const descricao = pagina.querySelector<HTMLInputElement>('#descricao-receita')!;
    descricao.value = 'Salário';
    descricao.dispatchEvent(new Event('input'));
    const valor = pagina.querySelector<HTMLInputElement>('#valor-receita')!;
    valor.value = '5000';
    valor.dispatchEvent(new Event('input'));
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes('Lançar receita'))!.click();

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/incomes');
    expect(pedido.request.body).toEqual(
      expect.objectContaining({ descricao: 'Salário', valor: 5000, categoria: 'Salário' }),
    );
    pedido.flush({ id: 'r1', usuario: 'a@x', ...pedido.request.body });
    tela.detectChanges();
    for (const recarga of http.match(() => true)) {
      recarga.flush(recarga.request.url.includes('reports') ? null : []);
    }
    expect(pagina.textContent).toContain('Receita lançada.');
  });
});
