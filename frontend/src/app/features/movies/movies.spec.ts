import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Filme } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Movies } from './movies';

const CATALOGO = {
  tmdbId: '550',
  titulo: 'Clube da Luta',
  ano: '1999',
  poster: '',
  nota: 8.4,
  sinopse: '',
};

function filme(mudancas: Partial<Filme> = {}): Filme {
  return {
    ...CATALOGO,
    id: 'f1',
    assistido: false,
    avaliacoes: {},
    usuario: 'titular@piggu.test',
    ...mudancas,
  };
}

/** A fila de filmes do casal: sortear, buscar, assistir e dar nota. */
describe('Movies', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Movies],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: new AuthFalso() }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function abrir(filmes: Filme[] = []) {
    const tela = TestBed.createComponent(Movies);
    tela.detectChanges();
    http.expectOne('/api/movies').flush(filmes);
    tela.detectChanges();
    return tela;
  }

  it('sorteia no genero escolhido e adiciona o sorteado a fila', async () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    await tela.whenStable();

    digitar(pagina, '#genero', '18');
    clicar(pagina, 'Sortear filme');
    http
      .expectOne((r) => r.url === '/api/movies/random' && r.params.get('genero') === '18')
      .flush(CATALOGO);
    tela.detectChanges();
    expect(pagina.querySelector('.destaque h3')?.textContent).toContain('Clube da Luta');

    clicar(pagina, 'Adicionar à lista');
    http.expectOne((r) => r.method === 'POST' && r.url === '/api/movies').flush(filme());
    tela.detectChanges();
    expect(pagina.querySelector('.destaque')).toBeNull();
    expect(pagina.textContent).not.toContain('Nenhum filme na fila.');
  });

  it('busca curta e recusada; a busca valida mostra os resultados', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#buscaFilme', 'a');
    clicar(pagina, 'Buscar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('pelo menos duas letras');

    digitar(pagina, '#buscaFilme', 'clube');
    clicar(pagina, 'Buscar');
    http.expectOne((r) => r.url === '/api/movies/search').flush([CATALOGO]);
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')).toBeNull();
    expect(pagina.textContent).toContain('Clube da Luta');
  });

  it('marcar como assistido leva o filme para "Já assistidos"', () => {
    const tela = abrir([filme()]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Já assistimos');
    http
      .expectOne((r) => r.url === '/api/movies/f1/watched' && r.params.get('assistido') === 'true')
      .flush(filme({ assistido: true }));
    tela.detectChanges();

    expect(pagina.textContent).toContain('Nenhum filme na fila.');
    expect(pagina.textContent).toContain('Voltar para a fila');
  });

  it('cada pessoa da sua nota e ve a dos outros', () => {
    const tela = abrir([
      filme({ assistido: true, avaliacoes: { 'titular@piggu.test': 2, 'bia@piggu.test': 5 } }),
    ]);
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.querySelectorAll('.estrela.marcada').length).toBe(2);
    expect(pagina.textContent).toContain('bia@piggu.test: 5 ★');

    pagina.querySelector<HTMLButtonElement>('[aria-label="Dar nota 4"]')!.click();
    const pedido = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/movies/f1/rating');
    expect(pedido.request.body).toEqual({ nota: 4 });
    pedido.flush(
      filme({ assistido: true, avaliacoes: { 'titular@piggu.test': 4, 'bia@piggu.test': 5 } }),
    );
    tela.detectChanges();

    expect(pagina.querySelectorAll('.estrela.marcada').length).toBe(4);
  });

  it('remover da lista pede confirmacao', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = abrir([filme()]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Remover');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/movies/f1').flush(null);
    tela.detectChanges();

    expect(pagina.textContent).toContain('Nenhum filme na fila.');
  });

  it('erro do catalogo aparece e libera o sorteio', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Sortear filme');
    http
      .expectOne((r) => r.url === '/api/movies/random')
      .flush(
        { erro: 'Catálogo de filmes fora do ar.' },
        { status: 503, statusText: 'Indisponivel' },
      );
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Catálogo de filmes fora do ar.',
    );
    expect(
      [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes('Sortear'))!
        .disabled,
    ).toBe(false);
  });
});
