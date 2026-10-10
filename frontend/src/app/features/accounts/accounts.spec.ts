import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { Accounts } from './accounts';

const FATURA = { mes: '2026-10', inicio: '2026-09-06', fechamento: '2026-10-05', vencimento: '2026-10-12', total: 33.33 };

/** O cartao mostra a fatura aberta e abre os gastos dela, com a parcela. */
describe('Accounts', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('abre a fatura do cartao com as parcelas', () => {
    const tela = TestBed.createComponent(Accounts);
    http.expectOne('/api/accounts').flush([
      { id: 'c1', nome: 'Nubank', tipo: 'CARTAO', fechamento: 5, vencimento: 12, faturaAberta: FATURA, faturaAPagar: null },
    ]);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Fecha dia 5');

    const botao = [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Ver fatura')!;
    botao.click();
    http.expectOne((r) => r.url === '/api/accounts/c1/statement' && r.params.get('mes') === '2026-10').flush({
      fatura: FATURA,
      gastos: [
        {
          id: 'g1', data: '2026-09-10', reciboId: 'r', estabelecimento: '', item: 'Fone', categoria: 'Lazer',
          valor: 33.33, tipo: 'Variavel', origem: 'Manual', usuario: 'a', registradoEm: '', contaId: 'c1',
          parcela: 1, parcelas: 3,
        },
      ],
    });
    tela.detectChanges();
    expect(pagina.textContent).toContain('parcela 1/3');
  });

  it('cartao sem dias nao e enviado', () => {
    const tela = TestBed.createComponent(Accounts);
    http.expectOne('/api/accounts').flush([]);
    const tela$ = tela.componentInstance as unknown as { nome: { set(v: string): void }; salvar(): void };
    tela$.nome.set('Visa');
    tela$.salvar();
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('fechamento e de vencimento');
  });
});
