import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { Lancamentos } from '../../core/ui/lancamentos';
import { AuthFalso } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, digitar } from '../../testing/tela';
import { LancamentoRapido } from './lancamento-rapido';

/** O painel do "+": lanca gasto ou receita sem trocar de tela e avisa as telas abertas. */
describe('LancamentoRapido', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: new AuthFalso() }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrir() {
    const tela = TestBed.createComponent(LancamentoRapido);
    tela.detectChanges();
    responder();
    tela.detectChanges();
    // Os campos dentro de <form> se registram num microtask.
    await tela.whenStable();
    return tela;
  }

  /** Os formularios pedem as listas ao abrir; responde todas pela URL. */
  function responder(): void {
    const corpos: Record<string, unknown> = {
      '/api/categories': { categorias: ['Mercado', 'Lazer'] },
      '/api/incomes/categories': ['Salário', 'Extra'],
      '/api/accounts': [],
      '/api/family': { id: 'f', nome: 'Casa', plano: 'GRATUITO', membros: [], convites: [] },
      '/api/receipts/usage': { usadas: 0, limite: 10, restantes: 10 },
    };
    // Responder umas listas pede outras (o acerto vem depois dos gastos).
    for (let pedidos = http.match(() => true); pedidos.length; pedidos = http.match(() => true)) {
      for (const pedido of pedidos) {
        expect(corpos).toHaveProperty([pedido.request.url]);
        pedido.flush(corpos[pedido.request.url] as object);
      }
    }
  }

  function clicar(pagina: HTMLElement, texto: string): void {
    const botao = [...pagina.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto,
    );
    if (!botao) throw new Error(`Botao nao encontrado: ${texto}`);
    botao.click();
  }

  it('lanca um gasto, avisa as telas abertas e entrega a mensagem para fechar', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const versao = TestBed.inject(Lancamentos).versao();
    const lancados: string[] = [];
    tela.componentInstance.lancado.subscribe((mensagem) => lancados.push(mensagem));

    digitar(pagina, '#rapido-valor', '23.9');
    digitar(pagina, '#rapido-item', 'Padaria');
    // Como no navegador, cada evento de digitacao passa pela deteccao de mudancas.
    tela.detectChanges();
    clicar(pagina, 'Lançar');

    const pedido = http.expectOne((p) => p.method === 'POST' && p.url === '/api/expenses');
    expect(pedido.request.body.itens[0]).toEqual(
      expect.objectContaining({ item: 'Padaria', valor: 23.9, categoria: null }),
    );
    pedido.flush([]);
    tela.detectChanges();
    await tela.whenStable();

    expect(lancados).toEqual(['Gasto lançado.']);
    expect(TestBed.inject(Lancamentos).versao()).toBe(versao + 1);
    expect((pagina.querySelector('#rapido-valor') as HTMLInputElement).value).toBe('');
  });

  it('na aba Receita lanca uma receita com a categoria escolhida', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const lancados: string[] = [];
    tela.componentInstance.lancado.subscribe((mensagem) => lancados.push(mensagem));

    clicar(pagina, 'Receita');
    tela.detectChanges();
    responder();
    tela.detectChanges();
    await tela.whenStable();
    digitar(pagina, '#rapido-valor-receita', '5000');
    digitar(pagina, '#rapido-descricao-receita', 'Salário de outubro');
    digitar(pagina, '#rapido-categoria-receita', 'Salário');
    clicar(pagina, 'Lançar receita');

    const pedido = http.expectOne((p) => p.method === 'POST' && p.url === '/api/incomes');
    expect(pedido.request.body).toEqual(
      expect.objectContaining({
        descricao: 'Salário de outubro',
        valor: 5000,
        categoria: 'Salário',
      }),
    );
    pedido.flush({});
    tela.detectChanges();
    expect(lancados).toEqual(['Receita lançada.']);
  });

  it('abre direto na aba Receita quando pedido pelo balao', async () => {
    const tela = TestBed.createComponent(LancamentoRapido);
    tela.componentRef.setInput('tipoInicial', 'receita');
    tela.detectChanges();
    http.match(() => true).forEach((pedido) => pedido.flush([]));
    tela.detectChanges();

    const ativa = (tela.nativeElement as HTMLElement).querySelector('.alternador .ativo');
    expect(ativa?.textContent?.trim()).toBe('Receita');
  });

  it('o gasto tem todos os campos da tela de Gastos: cartão, parcelas e mais opções', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Cartão de crédito');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Como foi a compra');
    expect(pagina.querySelector('details.mais-opcoes')).not.toBeNull();
    expect(pagina.querySelector('#rapido-moeda-compra')).not.toBeNull();
  });

  it('sem valor nao manda nada e diz o que falta', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#rapido-item', 'Padaria');
    clicar(pagina, 'Lançar');
    tela.detectChanges();

    expect(pagina.textContent).toContain('Digite um valor válido.');
    expect(http.match((p) => p.method === 'POST')).toEqual([]);
  });
});
