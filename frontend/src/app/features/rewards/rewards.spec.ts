import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Premio, SaldoDeMoedas } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Rewards } from './rewards';

const PIZZA: Premio = { id: 'p1', nome: 'Pizza', descricao: 'Sexta', preco: 50, ativo: true };
const CINEMA: Premio = { id: 'p2', nome: 'Cinema', descricao: '', preco: 200, ativo: true };

function saldo(valor: number): SaldoDeMoedas {
  return {
    saldo: valor,
    historico: [{ id: 'm1', data: '2026-10-01', valor: 30, motivo: 'Louça', usuario: 'bia@x' }],
  };
}

/** Fofocoins: quem cuida do dinheiro ajusta e cria premios; todos resgatam se tiver saldo. */
describe('Rewards', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Rewards],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function responder(moedas = 100, todos = 'true') {
    http.expectOne('/api/coins').flush(saldo(moedas));
    http
      .expectOne((r) => r.url === '/api/prizes' && r.params.get('todos') === todos)
      .flush([PIZZA, CINEMA]);
    http.expectOne('/api/prizes/redemptions').flush([]);
  }

  function abrir(moedas = 100, todos = 'true') {
    const tela = TestBed.createComponent(Rewards);
    tela.detectChanges();
    responder(moedas, todos);
    tela.detectChanges();
    return tela;
  }

  const botaoResgatar = (pagina: HTMLElement) =>
    [...pagina.querySelectorAll<HTMLButtonElement>('button')].filter((b) =>
      b.textContent?.includes('Resgatar'),
    );

  it('so libera o resgate do que o saldo paga', () => {
    const pagina: HTMLElement = abrir(100).nativeElement;
    const [pizza, cinema] = botaoResgatar(pagina);

    expect(pizza.disabled).toBe(false);
    expect(cinema.disabled).toBe(true);
  });

  it('resgatar confirma, avisa o saldo novo e recarrega', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    botaoResgatar(pagina)[0].click();
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/prizes/p1/redemptions')
      .flush({
        id: 'r1',
        premio: 'Pizza',
        preco: 50,
        usuario: 'titular',
        status: 'PENDENTE',
        data: '',
        saldo: 50,
      });
    responder(50);
    tela.detectChanges();

    expect(pagina.textContent).toContain('Resgatado. Saldo agora: 50 Fofocoins.');
  });

  it('ajuste sem motivo e recusado; com motivo, atualiza o saldo', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#ajusteValor', '20');
    clicar(pagina, 'Ajustar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Explique o motivo do ajuste.',
    );

    digitar(pagina, '#ajusteMotivo', 'Arrumou a cozinha');
    clicar(pagina, 'Ajustar');
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/coins/adjustments');
    expect(pedido.request.body).toEqual({ valor: 20, motivo: 'Arrumou a cozinha' });
    pedido.flush(saldo(120));
    tela.detectChanges();

    expect(pagina.textContent).toContain('Saldo atualizado.');
  });

  it('criar premio exige nome e preco, e recarrega a lista', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Criar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite o nome do prêmio.');

    digitar(pagina, '#premioNome', 'Massagem');
    clicar(pagina, 'Criar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite um preço válido.');

    digitar(pagina, '#premioPreco', '80');
    clicar(pagina, 'Criar');
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/prizes');
    expect(pedido.request.body).toEqual({
      nome: 'Massagem',
      descricao: '',
      preco: 80,
      ativo: true,
    });
    pedido.flush({});
    http.expectOne((r) => r.url === '/api/prizes' && r.method === 'GET').flush([PIZZA]);
  });

  it('o historico de moedas abre e fecha', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Ver histórico');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Louça');

    clicar(pagina, 'Esconder histórico');
    tela.detectChanges();
    expect(pagina.textContent).not.toContain('Louça');
  });

  it('o membro so ve os premios ativos e nao ajusta nem cria', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    const pagina: HTMLElement = abrir(100, 'false').nativeElement;

    expect(pagina.querySelector('#ajusteValor')).toBeNull();
    expect(pagina.querySelector('#premioNome')).toBeNull();
    expect(pagina.querySelector('[aria-label=Remover]')).toBeNull();
  });
});
