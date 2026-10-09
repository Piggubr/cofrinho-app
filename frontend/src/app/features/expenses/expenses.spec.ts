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
    http.expectOne((pedido) => pedido.url === '/api/expenses').flush([]);
    http.expectOne('/api/categories').flush({ categorias: ['Mercado'] });
    http.expectOne((pedido) => pedido.url === '/api/expenses/splits').flush([]);
    http.expectOne('/api/family').flush({ id: 'f', nome: 'Casa', plano: 'GRATUITO', membros: [], convites: [] });
    http.expectOne('/api/categories/rules').flush([]);
    http.expectOne('/api/accounts').flush([]);
    http.expectOne('/api/receipts/usage').flush({ usadas: 10 - restantes, limite: 10, restantes });
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('com leituras sobrando mostra o botao e quantas restam', () => {
    const pagina = abrir(3);
    expect(pagina.textContent).toContain('Restam 3 leituras');
    expect(pagina.querySelector('input[type="file"][accept="image/*"]')).not.toBeNull();
  });

  it('sem leituras no mes troca o botao pelo convite ao Premium', () => {
    const pagina = abrir(0);
    expect(pagina.querySelector('input[type="file"][accept="image/*"]')).toBeNull();
    expect(pagina.textContent).toContain('leituras grátis deste mês acabaram');
  });

  it('corrigir a categoria de um gasto sugere criar a regra', () => {
    const tela = TestBed.createComponent(Expenses);
    tela.detectChanges();
    const gasto = {
      id: 'g1', data: '2026-09-10', reciboId: 'r1', estabelecimento: '', item: 'Uber', categoria: 'Outros',
      valor: 20, tipo: 'Variavel', origem: 'Manual', usuario: 'a', registradoEm: '',
    };
    http.expectOne((pedido) => pedido.url === '/api/expenses').flush([gasto]);
    http.expectOne('/api/categories').flush({ categorias: ['Outros', 'Transporte'] });
    http.expectOne((pedido) => pedido.url === '/api/expenses/splits').flush([]);
    http.expectOne('/api/family').flush({ id: 'f', nome: 'Casa', plano: 'GRATUITO', membros: [], convites: [] });
    http.expectOne('/api/categories/rules').flush([]);
    http.expectOne('/api/accounts').flush([]);
    http.expectOne('/api/receipts/usage').flush({ usadas: 0, limite: 10, restantes: 10 });

    const tela$ = tela.componentInstance as unknown as {
      abrirEdicao(g: typeof gasto): void;
      categoriaEditada: { set(v: string): void };
      salvarEdicao(id: string): void;
    };
    tela$.abrirEdicao(gasto);
    tela$.categoriaEditada.set('Transporte');
    tela$.salvarEdicao('g1');
    http.expectOne('/api/expenses/g1').flush({ ...gasto, categoria: 'Transporte' });
    http.expectOne((pedido) => pedido.url === '/api/expenses').flush([]);
    http.expectOne((pedido) => pedido.url === '/api/expenses/splits').flush([]);
    tela.detectChanges();

    const botao = [...tela.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Criar regra',
    ) as HTMLButtonElement;
    expect(tela.nativeElement.textContent).toContain('Sempre usar Transporte para "Uber"?');
    botao.click();
    const pedido = http.expectOne('/api/categories/rules');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ termo: 'Uber', categoria: 'Transporte' });
    pedido.flush({ id: 'r1', termo: 'uber', categoria: 'Transporte' });
    http.expectOne('/api/categories/rules').flush([{ id: 'r1', termo: 'uber', categoria: 'Transporte' }]);
  });

  it('compra em dolar grava o convertido e o original; preco acima da media avisa', () => {
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela);
    const t = tela.componentInstance as unknown as {
      novoItem: { set(v: string): void };
      novoValor: { set(v: number): void };
      trocarMoeda(c: string): void;
      conferirPreco(): void;
      lancar(): void;
    };
    t.novoItem.set('Café');
    t.novoValor.set(24);
    t.conferirPreco();
    http.expectOne((r) => r.url === '/api/products/price-check').flush({ media: 20, compras: 3, percentual: 20, acima: true });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('20% acima da média');

    t.novoValor.set(20);
    t.trocarMoeda('USD');
    http.expectOne((r) => r.url === '/api/exchange-rate' && r.params.get('de') === 'USD').flush({ taxa: 5.5 });
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body.itens[0]).toEqual(
      expect.objectContaining({ valor: 110, moedaOriginal: 'USD', valorOriginal: 20 }),
    );
    pedido.flush([]);
    http.expectOne((r) => r.url === '/api/expenses').flush([]);
    http.expectOne((r) => r.url === '/api/expenses/splits').flush([]);
  });

  function abrirCom(tela: ReturnType<typeof TestBed.createComponent<Expenses>>): void {
    tela.detectChanges();
    http.expectOne((pedido) => pedido.url === '/api/expenses').flush([]);
    http.expectOne('/api/categories').flush({ categorias: ['Mercado'] });
    http.expectOne((pedido) => pedido.url === '/api/expenses/splits').flush([]);
    http.expectOne('/api/family').flush({ id: 'f', nome: 'Casa', plano: 'GRATUITO', membros: [], convites: [] });
    http.expectOne('/api/categories/rules').flush([]);
    http.expectOne('/api/accounts').flush([]);
    http.expectOne('/api/receipts/usage').flush({ usadas: 0, limite: 10, restantes: 10 });
  }
});
