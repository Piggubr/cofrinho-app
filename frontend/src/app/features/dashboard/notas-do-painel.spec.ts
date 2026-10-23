import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Nota } from '../../core/api/models';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { NotasDoPainel } from './notas-do-painel';

function nota(mudancas: Partial<Nota> = {}): Nota {
  return {
    id: 'n1',
    titulo: 'Pagar a luz',
    texto: '',
    data: null,
    valor: 0,
    categoria: '',
    gastoId: null,
    usuario: null,
    criadoEm: '2026-10-01T10:00:00Z',
    ...mudancas,
  };
}

/** Suas notas no Painel: as cinco mais novas, anotar rapido e apagar. */
describe('NotasDoPainel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NotasDoPainel], providers: PROVEDORES_DE_TELA });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function abrir(notas: Nota[] | null = []) {
    const tela = TestBed.createComponent(NotasDoPainel);
    const pedido = http.expectOne('/api/notes');
    if (notas) {
      pedido.flush(notas);
    } else {
      pedido.flush({ erro: 'Fora do ar.' }, { status: 500, statusText: 'Erro' });
    }
    tela.detectChanges();
    await tela.whenStable();
    return tela;
  }

  it('mostra so as cinco mais novas, com data e texto', async () => {
    const notas = Array.from({ length: 7 }, (_, i) =>
      nota({ id: `n${i}`, titulo: `Nota ${i}`, criadoEm: `2026-10-0${i + 1}T10:00:00Z` }),
    );
    notas[6] = { ...notas[6], data: '2026-10-20', texto: 'até sexta' };
    const pagina: HTMLElement = (await abrir(notas)).nativeElement;
    const titulos = [...pagina.querySelectorAll('.linha-titulo')].map((p) => p.textContent);
    expect(titulos).toEqual(['Nota 6', 'Nota 5', 'Nota 4', 'Nota 3', 'Nota 2']);
    expect(pagina.querySelector('.linha-detalhe')?.textContent).toContain('20/10/2026');
    expect(pagina.querySelector('.linha-detalhe')?.textContent).toContain('até sexta');
  });

  it('sem notas mostra o vazio; com erro mostra o aviso', async () => {
    expect((await abrir([])).nativeElement.textContent).toContain('Nenhuma nota ainda.');
    const comErro: HTMLElement = (await abrir(null)).nativeElement;
    expect(comErro.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('cria nota com titulo e texto e fecha o formulario', async () => {
    const tela = await abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, '+ Criar outra');
    tela.detectChanges();

    clicar(pagina, 'Salvar nota');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Digite o título da nota.');

    digitar(pagina, '#tituloNotaPainel', ' Mercado ');
    digitar(pagina, '#textoNotaPainel', ' leite ');
    clicar(pagina, 'Salvar nota');
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/notes');
    expect(pedido.request.body).toEqual(
      expect.objectContaining({ titulo: 'Mercado', texto: 'leite' }),
    );
    pedido.flush(nota({ id: 'n9', titulo: 'Mercado', texto: 'leite' }));
    tela.detectChanges();

    expect(pagina.querySelector('#tituloNotaPainel')).toBeNull();
    expect(pagina.textContent).toContain('Mercado');
  });

  it('erro ao salvar mantem o formulario; cancelar fecha', async () => {
    const tela = await abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, '+ Criar outra');
    tela.detectChanges();
    digitar(pagina, '#tituloNotaPainel', 'Mercado');
    clicar(pagina, 'Salvar nota');
    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body.texto).toBeNull();
    pedido.flush({ erro: 'Tente de novo.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(pagina.querySelector('[role="alert"]')).not.toBeNull();
    expect(pagina.querySelector('#tituloNotaPainel')).not.toBeNull();

    clicar(pagina, 'Cancelar');
    tela.detectChanges();
    expect(pagina.querySelector('#tituloNotaPainel')).toBeNull();
  });

  it('apagar pede confirmacao e tira a nota da lista', async () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValue(true);
    const tela = await abrir([nota()]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    expect(confirmar).toHaveBeenCalledWith('Apagar a nota "Pagar a luz"?');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    clicar(pagina, 'Apagar');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/notes/n1').flush(null);
    tela.detectChanges();
    expect(pagina.textContent).not.toContain('Pagar a luz');
  });

  it('erro ao apagar mostra o aviso e mantem a nota', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = await abrir([nota()]);
    clicar(tela.nativeElement, 'Apagar');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Nao deu.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(tela.nativeElement.textContent).toContain('Pagar a luz');
  });
});
