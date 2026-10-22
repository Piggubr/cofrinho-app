import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Expenses } from './expenses';
import { FormularioDeGasto } from './formulario-de-gasto';

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
    abrirCom(tela, [], [], [], [], restantes);
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
      id: 'g1',
      data: '2026-09-10',
      reciboId: 'r1',
      estabelecimento: '',
      item: 'Uber',
      categoria: 'Outros',
      valor: 20,
      tipo: 'Variavel',
      origem: 'Manual',
      usuario: 'a',
      registradoEm: '',
    };
    responder({ gastos: [gasto], categorias: ['Outros', 'Transporte'] });

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
    http
      .expectOne('/api/categories/rules')
      .flush([{ id: 'r1', termo: 'uber', categoria: 'Transporte' }]);
  });

  it('compra em dolar grava o convertido e o original; preco acima da media avisa', () => {
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela);
    const t = formulario(tela) as unknown as {
      novoItem: { set(v: string): void };
      novoValor: { set(v: number): void };
      trocarMoeda(c: string): void;
      conferirPreco(): void;
      lancar(): void;
    };
    t.novoItem.set('Café');
    t.novoValor.set(24);
    t.conferirPreco();
    http
      .expectOne((r) => r.url === '/api/products/price-check')
      .flush({ media: 20, compras: 3, percentual: 20, acima: true });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('20% acima da média');

    t.novoValor.set(20);
    t.trocarMoeda('USD');
    http
      .expectOne((r) => r.url === '/api/exchange-rate' && r.params.get('de') === 'USD')
      .flush({ taxa: 5.5 });
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body.itens[0]).toEqual(
      expect.objectContaining({ valor: 110, moedaOriginal: 'USD', valorOriginal: 20 }),
    );
    pedido.flush([]);
    http.expectOne((r) => r.url === '/api/expenses').flush([]);
    http.expectOne((r) => r.url === '/api/expenses/splits').flush([]);
  });

  it('divide pelo id de cada pessoa e mostra o acerto com o nome; conta excluida vira ex-membro', () => {
    const tela = TestBed.createComponent(Expenses);
    const bia = {
      id: 'u-bia',
      nome: 'Bia',
      email: 'bia@piggu.test',
      foto: null,
      papel: 'PARCEIRO',
    };
    const eu = {
      id: usuarioDeTeste().id,
      nome: 'Titular',
      email: 'titular@piggu.test',
      foto: null,
      papel: 'TITULAR',
    };
    abrirCom(
      tela,
      [eu, bia],
      [
        { pessoa: eu.id, pagou: 100, parte: 50, saldo: 50 },
        { pessoa: 'u-bia', pagou: 0, parte: 30, saldo: -30 },
        { pessoa: null, pagou: 0, parte: 20, saldo: -20 },
      ],
      [],
      [
        { de: 'u-bia', para: eu.id, valor: 30 },
        { de: null, para: eu.id, valor: 20 },
      ],
    );
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    const nomes = [...pagina.querySelectorAll('.linha-titulo')].map((l) => l.textContent?.trim());
    expect(nomes).toEqual(expect.arrayContaining(['Titular', 'Bia', 'Ex-membro']));
    expect(pagina.textContent).not.toContain('@piggu.test');
    const acerto = pagina.querySelector('.acerto-resumo')?.textContent?.replace(/\s+/g, ' ');
    expect(acerto).toContain('Bia paga');
    expect(acerto).toContain('para Titular');

    const caixas = [...pagina.querySelectorAll<HTMLInputElement>('.dividir input[type=checkbox]')];
    caixas.forEach((caixa) => caixa.dispatchEvent(new Event('change')));
    const t = formulario(tela) as unknown as {
      novoItem: { set(v: string): void };
      novoValor: { set(v: number): void };
      lancar(): void;
    };
    t.novoItem.set('Jantar');
    t.novoValor.set(100);
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body.dividirCom).toEqual([eu.id, 'u-bia']);
    pedido.flush([]);
    http.expectOne((r) => r.url === '/api/expenses').flush([]);
    http.expectOne((r) => r.url === '/api/expenses/splits').flush([]);
  });

  it('a última conta vira a padrão: cartão já vem escolhido e mostra a fatura da compra', () => {
    localStorage.setItem('piggu_conta_padrao', 'c2');
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela, [], [], CONTAS);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    const opcoes = pagina.querySelector<HTMLDetailsElement>('details.mais-opcoes')!;
    expect(opcoes.open).toBe(false);
    expect(opcoes.querySelector('summary')?.textContent).toContain('Hoje');
    expect(pagina.querySelector('.alternador .ativo')?.textContent).toContain('Cartão de crédito');
    expect(pagina.textContent).toContain('Vai para a fatura de');

    const t = formulario(tela) as unknown as Tela;
    t.trocarForma('DEBITO');
    t.novoItem.set('Mercado');
    t.novoValor.set(90);
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body.contaId).toBe('c1');
    pedido.flush([]);
    http.expectOne((r) => r.url === '/api/expenses').flush([]);
    http.expectOne((r) => r.url === '/api/expenses/splits').flush([]);
    expect(localStorage.getItem('piggu_conta_padrao')).toBe('c1');
    localStorage.removeItem('piggu_conta_padrao');
  });

  it('cartão sem escolher não é lançado; parcelado pede de 2 a 48; recorrente vai marcado', () => {
    localStorage.removeItem('piggu_conta_padrao');
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela, [], [], [...CONTAS, { ...CONTAS[1], id: 'c3', nome: 'Inter' }]);
    const t = formulario(tela) as unknown as Tela;
    t.novoItem.set('TV');
    t.novoValor.set(900);
    t.trocarForma('CARTAO');
    t.lancar();
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Escolha em qual cartão');
    expect(tela.nativeElement.querySelector('#cartao').getAttribute('aria-invalid')).toBe('true');

    t.escolherConta('c3');
    t.trocarModo('PARCELADO');
    t.novasParcelas.set(1);
    t.lancar();
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('de 2 a 48 parcelas');

    t.novasParcelas.set(10);
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('10x de');
    t.trocarModo('RECORRENTE');
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body).toEqual(
      expect.objectContaining({ contaId: 'c3', parcelas: null, recorrente: true }),
    );
    expect(pedido.request.body.itens[0].tipo).toBe('Fixo');
  });

  it('débito/Pix é sempre à vista: sem À vista/Parcelado/Recorrente e sem o campo Tipo', () => {
    localStorage.removeItem('piggu_conta_padrao');
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela, [], [], CONTAS);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.querySelector('#tipo')).toBeNull();
    expect(pagina.textContent).not.toContain('Como foi a compra');

    const t = formulario(tela) as unknown as Tela;
    t.trocarForma('CARTAO');
    t.trocarModo('PARCELADO');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Como foi a compra');

    // Voltar para o débito desfaz o parcelado: não sai um Pix em 2x.
    t.trocarForma('DEBITO');
    t.novoItem.set('Feira');
    t.novoValor.set(50);
    t.lancar();
    const pedido = http.expectOne((r) => r.url === '/api/expenses' && r.method === 'POST');
    expect(pedido.request.body).toEqual(
      expect.objectContaining({ contaId: 'c1', parcelas: null, recorrente: null }),
    );
    expect(pedido.request.body.itens[0].tipo).toBe('Variavel');
  });

  it('sem internet o gasto fica na fila deste aparelho e o formulario limpa', () => {
    localStorage.removeItem('piggu_fila_de_gastos');
    const tela = TestBed.createComponent(Expenses);
    abrirCom(tela);
    const t = formulario(tela) as unknown as {
      novoItem: { set(v: string): void; (): string };
      novoValor: { set(v: number): void };
      lancar(): void;
    };
    t.novoItem.set('Pao');
    t.novoValor.set(8);
    t.lancar();
    http
      .expectOne((r) => r.url === '/api/expenses' && r.method === 'POST')
      .error(new ProgressEvent('error'), { status: 0 });
    tela.detectChanges();

    expect(t.novoItem()).toBe('');
    expect(tela.nativeElement.textContent).toContain('ficou guardado neste aparelho');
    expect(tela.nativeElement.textContent).toContain('1 gasto esperando conexão');
    localStorage.removeItem('piggu_fila_de_gastos');
  });

  interface Tela {
    novoItem: { set(v: string): void };
    novoValor: { set(v: number): void };
    novasParcelas: { set(v: number): void };
    trocarForma(forma: 'CARTAO' | 'DEBITO'): void;
    trocarModo(modo: 'VISTA' | 'PARCELADO' | 'RECORRENTE'): void;
    escolherConta(id: string): void;
    lancar(): void;
  }

  const CONTAS = [
    { id: 'c1', nome: 'Conta', tipo: 'CONTA', fechamento: null, vencimento: null, faturaAberta: null },
    { id: 'c2', nome: 'Nubank', tipo: 'CARTAO', fechamento: 3, vencimento: 10, faturaAberta: null },
  ];

  function formulario(tela: ComponentFixture<Expenses>): FormularioDeGasto {
    return tela.debugElement.query(By.directive(FormularioDeGasto)).componentInstance;
  }

  /** A tela e o formulario pedem as listas ao abrir; responde todas pela URL. */
  function responder({
    gastos = [] as unknown[],
    categorias = ['Mercado'],
    membros = [] as unknown[],
    acerto = [] as unknown[],
    contas = [] as unknown[],
    transferencias = [] as unknown[],
    restantes = 10,
  } = {}): void {
    const corpos: Record<string, unknown> = {
      '/api/expenses': gastos,
      '/api/categories': { categorias },
      '/api/expenses/splits': acerto,
      '/api/family': { id: 'f', nome: 'Casa', plano: 'GRATUITO', membros, convites: [] },
      '/api/categories/rules': [],
      '/api/accounts': contas,
      '/api/expenses/splits/transfers': transferencias,
      '/api/receipts/usage': { usadas: 10 - restantes, limite: 10, restantes },
    };
    // Responder umas listas pede outras (o acerto vem depois dos gastos).
    for (let pedidos = http.match(() => true); pedidos.length; pedidos = http.match(() => true)) {
      for (const pedido of pedidos) {
        expect(corpos).toHaveProperty([pedido.request.url]);
        pedido.flush(corpos[pedido.request.url] as object);
      }
    }
  }

  function abrirCom(
    tela: ComponentFixture<Expenses>,
    membros: unknown[] = [],
    acerto: unknown[] = [],
    contas: unknown[] = [],
    transferencias: unknown[] = [],
    restantes = 10,
  ): void {
    tela.detectChanges();
    responder({ membros, acerto, contas, transferencias, restantes });
  }
});
