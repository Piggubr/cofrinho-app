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
    http.expectOne('/api/categories').flush({ categorias: ['Mercado', 'Lazer'] });
    http.expectOne('/api/incomes/categories').flush(['Salário', 'Extra']);
    http.expectOne('/api/accounts').flush([]);
    tela.detectChanges();
    // Os campos dentro de <form> se registram num microtask.
    await tela.whenStable();
    return tela;
  }

  function clicar(pagina: HTMLElement, texto: string): void {
    const botao = [...pagina.querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto,
    );
    if (!botao) throw new Error(`Botao nao encontrado: ${texto}`);
    botao.click();
  }

  it('lanca um gasto e avisa as telas abertas, sem fechar o painel', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const versao = TestBed.inject(Lancamentos).versao();

    digitar(pagina, '#valor-rapido', '23.9');
    digitar(pagina, '#descricao-rapida', 'Padaria');
    // Como no navegador, cada evento de digitacao passa pela deteccao de mudancas.
    tela.detectChanges();
    clicar(pagina, 'Lançar gasto');

    const pedido = http.expectOne((p) => p.method === 'POST' && p.url === '/api/expenses');
    expect(pedido.request.body.itens[0]).toEqual(
      expect.objectContaining({ item: 'Padaria', valor: 23.9, categoria: null }),
    );
    pedido.flush([]);
    tela.detectChanges();
    await tela.whenStable();

    expect(pagina.textContent).toContain('Gasto lançado.');
    expect(TestBed.inject(Lancamentos).versao()).toBe(versao + 1);
    expect((pagina.querySelector('#valor-rapido') as HTMLInputElement).value).toBe('');
  });

  it('na aba Receita lanca uma receita com a categoria escolhida', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Receita');
    tela.detectChanges();
    await tela.whenStable();
    digitar(pagina, '#valor-rapido', '5000');
    digitar(pagina, '#descricao-rapida', 'Salário de outubro');
    digitar(pagina, '#categoria-rapida', 'Salário');
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
    expect(pagina.textContent).toContain('Receita lançada.');
  });

  it('sem valor nao manda nada e diz o que falta', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#descricao-rapida', 'Padaria');
    clicar(pagina, 'Lançar gasto');
    tela.detectChanges();

    expect(pagina.textContent).toContain('Digite um valor válido.');
  });
});
