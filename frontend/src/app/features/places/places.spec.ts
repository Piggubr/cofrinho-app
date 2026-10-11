import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Lugar } from '../../core/api/models';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Places } from './places';

function lugar(mudancas: Partial<Lugar> = {}): Lugar {
  return {
    id: 'l1',
    nome: 'Cantina',
    categoria: 'Restaurante',
    localizacao: 'Lisboa',
    nota: 4,
    comentario: 'Massa boa',
    data: '2026-09-20',
    marcacoes: ['Jantar'],
    fotoAssetId: 'a1',
    temFoto: true,
    valor: 80,
    usuario: 'titular@piggu.test',
    ...mudancas,
  };
}

/** Lugares visitados: nota, marcadores e foto opcional. */
describe('Places', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Places], providers: PROVEDORES_DE_TELA });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function abrir(lugares: Lugar[] = [lugar()]) {
    const tela = TestBed.createComponent(Places);
    tela.detectChanges();
    http.expectOne('/api/places').flush(lugares);
    http.expectOne('/api/places/tags').flush(['Jantar', 'Viagem']);
    tela.detectChanges();
    return tela;
  }

  it('mostra o lugar com nota, marcador e a foto pelo endereco do arquivo', () => {
    const pagina: HTMLElement = abrir().nativeElement;

    expect(pagina.querySelector('.nota')?.textContent).toBe('★★★★');
    expect(pagina.querySelector('.etiqueta')?.textContent).toBe('Jantar');
    expect(pagina.querySelector<HTMLImageElement>('img.foto')!.src).toContain(
      '/api/assets/a1/content',
    );
  });

  it('sem nome, nao salva', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Novo lugar');
    tela.detectChanges();

    clicar(pagina, 'Salvar');
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite o nome do lugar.');
  });

  it('novo lugar vai com nota, marcadores e sem foto', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Novo lugar');
    tela.detectChanges();

    digitar(pagina, '#nomeLugar', ' Praia do Meco ');
    digitar(pagina, '#dataVisita', '2026-08-15');
    pagina.querySelector<HTMLButtonElement>('[aria-label="Nota 3"]')!.click();
    clicar(pagina, 'Viagem');
    clicar(pagina, 'Salvar');

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/places');
    expect(pedido.request.body).toMatchObject({
      nome: 'Praia do Meco',
      categoria: null,
      nota: 3,
      data: '2026-08-15',
      marcacoes: ['Viagem'],
      valor: 0,
      imageBase64: null,
    });
    pedido.flush(lugar({ id: 'l2', nome: 'Praia do Meco', temFoto: false }));
    tela.detectChanges();

    expect(pagina.querySelectorAll('article.lugar').length).toBe(1);
    expect(pagina.querySelector('#nomeLugar')).toBeNull();
  });

  it('editar abre com os dados e salva no mesmo lugar', async () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Editar');
    tela.detectChanges();
    await tela.whenStable();
    expect(pagina.querySelector<HTMLInputElement>('#nomeLugar')!.value).toBe('Cantina');

    digitar(pagina, '#nomeLugar', 'Cantina Nova');
    clicar(pagina, 'Salvar');
    const pedido = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/places/l1');
    expect(pedido.request.body).toMatchObject({ nome: 'Cantina Nova', marcacoes: ['Jantar'] });
    pedido.flush(lugar({ nome: 'Cantina Nova' }));
    tela.detectChanges();

    expect(pagina.querySelector('article.lugar h3')?.textContent).toBe('Cantina Nova');
  });

  it('novo marcador curto e recusado; valido entra na lista', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Novo lugar');
    tela.detectChanges();

    digitar(pagina, '#novoMarcador', 'x');
    clicar(pagina, 'Adicionar marcador');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite um nome válido.');

    digitar(pagina, '#novoMarcador', 'Bar');
    clicar(pagina, 'Adicionar marcador');
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/places/tags')
      .flush(['Bar', 'Jantar', 'Viagem']);
    tela.detectChanges();
    expect(pagina.querySelectorAll('button.marcador').length).toBe(3);
  });

  it('apagar pede confirmacao', () => {
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    http.expectNone((r) => r.method === 'DELETE');

    clicar(pagina, 'Apagar');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/places/l1').flush(null);
    tela.detectChanges();
    expect(pagina.querySelectorAll('article.lugar').length).toBe(0);
  });
});
