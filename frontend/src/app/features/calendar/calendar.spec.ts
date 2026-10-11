import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Nota } from '../../core/api/models';
import { dataIso, mesKey } from '../../core/ui/datas';
import { PROVEDORES_DE_TELA, clicar, digitar, gastoDeTeste } from '../../testing/tela';
import { Calendar } from './calendar';

function nota(mudancas: Partial<Nota> = {}): Nota {
  return {
    id: 'n1',
    titulo: 'Dentista',
    texto: '',
    data: null,
    valor: 0,
    categoria: '',
    gastoId: null,
    usuario: 'titular@piggu.test',
    criadoEm: '2026-10-01T10:00:00Z',
    ...mudancas,
  };
}

/** Lembretes no mes, e a nota com valor que vira gasto. */
describe('Calendar', () => {
  let http: HttpTestingController;
  const hoje = new Date();
  const mes = mesKey(hoje);
  const dia3 = dataIso(new Date(hoje.getFullYear(), hoje.getMonth(), 3));

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Calendar], providers: PROVEDORES_DE_TELA });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function responder(notas: Nota[], mesDosGastos = mes) {
    http.expectOne('/api/notes').flush(notas);
    http
      .expectOne((r) => r.url === '/api/expenses' && r.params.get('mes') === mesDosGastos)
      .flush([
        gastoDeTeste({ data: dia3, item: 'Pão', valor: 12 }),
        gastoDeTeste({ id: 'g2', data: dia3, valor: 8 }),
      ]);
    http.expectOne('/api/categories').flush({ categorias: ['Saúde', 'Lazer'] });
  }

  async function abrir(notas: Nota[] = []) {
    const tela = TestBed.createComponent(Calendar);
    tela.detectChanges();
    responder(notas);
    tela.detectChanges();
    await tela.whenStable();
    return tela;
  }

  it('o dia com gasto mostra o total e, aberto, os gastos e lembretes dele', async () => {
    const tela = await abrir([nota({ id: 'n2', data: dia3, titulo: 'Feira' })]);
    const pagina: HTMLElement = tela.nativeElement;
    const celula = [...pagina.querySelectorAll<HTMLButtonElement>('button.celula')].find(
      (c) => c.querySelector('.numero-do-dia')?.textContent === '3',
    )!;

    expect(celula.classList).toContain('com-gasto');
    expect(celula.querySelector('.marca-gasto')?.textContent).toContain('20');
    expect(celula.querySelector('.marca-nota')).not.toBeNull();

    celula.click();
    tela.detectChanges();
    expect(pagina.textContent).toContain('Pão');
    expect(pagina.textContent).toContain('Feira');
    await tela.whenStable();
    expect(pagina.querySelector<HTMLInputElement>('#dataNota')!.value).toBe(dia3);
  });

  it('nota solta aparece em "Sem data"', async () => {
    const tela = await abrir([nota()]);
    expect((tela.nativeElement as HTMLElement).textContent).toContain('Dentista');
  });

  it('sem titulo, a nota nao vai para o backend', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Criar nota');
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite o título');
  });

  it('nota com valor avisa que vira gasto e vai com a categoria', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#tituloNota', 'Consulta');
    digitar(pagina, '#valorNota', '150');
    tela.detectChanges();
    expect(pagina.textContent).toContain('esta nota também lança um gasto');
    clicar(pagina, 'Criar nota');

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/notes');
    expect(pedido.request.body).toMatchObject({
      titulo: 'Consulta',
      valor: 150,
      categoria: 'Saúde',
    });
    pedido.flush({});
    responder([]);
  });

  it('apagar nota com gasto pede confirmacao dizendo que o gasto vai junto', async () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = await abrir([nota({ gastoId: 'g9' })]);

    clicar(tela.nativeElement, 'Apagar');

    expect(confirmar.mock.calls[0][0]).toContain('O gasto lançado junto também será apagado.');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/notes/n1').flush(null);
    responder([]);
  });

  it('desistir de apagar nao chama o backend', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = await abrir([nota()]);

    clicar(tela.nativeElement, 'Apagar');

    http.expectNone((r) => r.method === 'DELETE');
  });

  it('trocar de mes busca os gastos do mes novo', async () => {
    const tela = await abrir();
    const anterior = mesKey(new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1));

    clicar(tela.nativeElement, 'Mês anterior');

    responder([], anterior);
  });
});
