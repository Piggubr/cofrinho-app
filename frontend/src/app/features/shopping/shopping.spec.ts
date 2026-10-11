import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ItemDeCompra } from '../../core/api/models';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Shopping } from './shopping';

function item(mudancas: Partial<ItemDeCompra> = {}): ItemDeCompra {
  return {
    id: 'i1',
    item: 'Ovos',
    quantidade: '12',
    lista: 'Compras',
    comprado: false,
    marca: '',
    imagem: '',
    codigo: '',
    usuario: '11111111-1111-1111-1111-111111111111',
    ...mudancas,
  };
}

/** Lista de compras: riscar no mercado sem esperar o servidor, e desfazer se ele recusar. */
describe('Shopping', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Shopping], providers: PROVEDORES_DE_TELA });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(itens: ItemDeCompra[]) {
    const tela = TestBed.createComponent(Shopping);
    tela.detectChanges();
    http.expectOne('/api/shopping/items').flush(itens);
    tela.detectChanges();
    return tela;
  }

  const titulos = (pagina: HTMLElement, seletor: string) =>
    [...pagina.querySelectorAll(`${seletor} .linha-titulo`)].map((t) => t.textContent?.trim());

  it('separa pendentes e comprados, e cada lista mostra so os seus', () => {
    const tela = abrir([
      item(),
      item({ id: 'i2', item: 'Pão', comprado: true }),
      item({ id: 'i3', item: 'Fone', lista: 'Desejos' }),
    ]);
    const pagina: HTMLElement = tela.nativeElement;

    expect(titulos(pagina, 'label.item:not(.riscado)')).toEqual(['Ovos']);
    expect(titulos(pagina, 'label.riscado')).toEqual(['Pão']);

    clicar(pagina, 'Desejos');
    tela.detectChanges();
    expect(titulos(pagina, 'label.item')).toEqual(['Fone']);
  });

  it('adiciona na lista aberta e limpa o campo', async () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    clicar(pagina, 'Desejos');
    digitar(pagina, '#novoItem', ' Tênis ');
    digitar(pagina, '#quantidade', '1 par');
    clicar(pagina, 'Adicionar');

    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body).toEqual({ item: 'Tênis', quantidade: '1 par', lista: 'Desejos' });
    pedido.flush(item({ id: 'i9', item: 'Tênis', lista: 'Desejos' }));
    tela.detectChanges();
    await tela.whenStable();

    expect(titulos(pagina, 'label.item')).toEqual(['Tênis']);
    expect(pagina.querySelector<HTMLInputElement>('#novoItem')!.value).toBe('');
  });

  it('item vazio ou busca curta nao chamam o backend', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Adicionar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Digite o que deseja adicionar.',
    );

    digitar(pagina, '#busca', 'a');
    clicar(pagina, 'Buscar');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('pelo menos duas letras');
  });

  it('busca no catalogo e adiciona com marca e codigo', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    digitar(pagina, '#busca', 'leite');
    clicar(pagina, 'Buscar');
    http
      .expectOne((r) => r.url === '/api/shopping/catalog' && r.params.get('busca') === 'leite')
      .flush([
        { codigo: '789', nome: 'Leite integral', marca: 'Boa', quantidade: '1 L', imagem: '' },
      ]);
    tela.detectChanges();

    pagina.querySelector<HTMLButtonElement>('button.resultado')!.click();
    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body).toMatchObject({
      item: 'Leite integral',
      marca: 'Boa',
      codigo: '789',
      lista: 'Compras',
    });
    pedido.flush(item({ id: 'i5', item: 'Leite integral' }));
    tela.detectChanges();

    expect(pagina.querySelector('button.resultado')).toBeNull();
    expect(titulos(pagina, 'label.item')).toContain('Leite integral');
  });

  it('riscar o item vale na hora; se o servidor recusar, volta e avisa', () => {
    const tela = abrir([item()]);
    const pagina: HTMLElement = tela.nativeElement;

    pagina.querySelector<HTMLInputElement>('label.item input')!.dispatchEvent(new Event('change'));
    tela.detectChanges();
    expect(titulos(pagina, 'label.riscado')).toEqual(['Ovos']);

    http
      .expectOne(
        (r) => r.url === '/api/shopping/items/i1/purchased' && r.params.get('comprado') === 'true',
      )
      .flush({ erro: 'Sem conexão com a lista.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(titulos(pagina, 'label.riscado')).toEqual([]);
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Sem conexão com a lista.');
  });

  it('apagar tira o item da lista', () => {
    const tela = abrir([item()]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/shopping/items/i1').flush(null);
    tela.detectChanges();

    expect(titulos(pagina, 'label.item')).toEqual([]);
  });
});
