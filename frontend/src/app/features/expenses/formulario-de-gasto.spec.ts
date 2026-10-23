import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ContaOuCartao, MembroDaFamilia, ReciboLido } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FilaDeGastos } from '../../core/offline/fila-de-gastos';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import {
  ID_DA_BIA,
  ID_DA_TITULAR,
  PROVEDORES_DE_TELA,
  clicar,
  digitar,
  gastoDeTeste,
} from '../../testing/tela';
import { FormularioDeGasto, GastoLancado } from './formulario-de-gasto';

function conta(mudancas: Partial<ContaOuCartao>): ContaOuCartao {
  return {
    id: 'b1',
    nome: 'Conta',
    tipo: 'CONTA',
    fechamento: null,
    vencimento: null,
    faturaAberta: null,
    faturaAPagar: null,
    ...mudancas,
  };
}

const CONTAS = [
  conta({ id: 'c1', nome: 'Nubank', tipo: 'CARTAO', fechamento: 3, vencimento: 10 }),
  conta({ id: 'c2', nome: 'Itaú', tipo: 'CARTAO' }),
  conta({ id: 'b1', nome: 'Conta corrente' }),
  conta({ id: 'b2', nome: 'Poupança' }),
];

const MEMBROS: MembroDaFamilia[] = [
  {
    id: ID_DA_TITULAR,
    nome: 'Titular',
    email: 't@piggu.test',
    foto: null,
    papel: 'TITULAR',
    premium: false,
  },
  {
    id: ID_DA_BIA,
    nome: 'Bia',
    email: 'b@piggu.test',
    foto: null,
    papel: 'MEMBRO',
    premium: false,
  },
];

const RECIBO: ReciboLido = {
  reciboId: 'rec1',
  estabelecimento: 'Mercado Bom',
  data: '2026-10-05',
  itens: [
    { item: 'Leite', categoria: 'Mercado', valor: 5 },
    { item: 'Pão', categoria: 'Mercado', valor: 3 },
  ],
  origem: 'OCR',
  aviso: 'Confira o total.',
  leiturasRestantes: 2,
};

/** O formulario do gasto: o que falta, cartao e parcelas, outra moeda, orcamento e o recibo por foto. */
describe('FormularioDeGasto', () => {
  let http: HttpTestingController;
  let lancados: GastoLancado[];

  beforeEach(() => {
    localStorage.removeItem('piggu_conta_padrao');
    localStorage.removeItem('piggu_fila_de_gastos');
    const auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.configureTestingModule({
      imports: [FormularioDeGasto],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
    lancados = [];
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('piggu_conta_padrao');
  });

  function abrir(
    contas: ContaOuCartao[] = CONTAS,
    membros: MembroDaFamilia[] = MEMBROS,
    restantes = 3,
  ) {
    const tela = TestBed.createComponent(FormularioDeGasto);
    tela.componentInstance.lancado.subscribe((lancado) => lancados.push(lancado));
    tela.detectChanges();
    http.expectOne('/api/categories').flush({ categorias: ['Mercado', 'Lazer'] });
    http.expectOne('/api/accounts').flush(contas);
    http.expectOne('/api/family').flush({ membros });
    http.expectOne('/api/receipts/usage').flush({ restantes });
    tela.detectChanges();
    return tela;
  }

  function alerta(tela: ComponentFixture<FormularioDeGasto>): string | undefined {
    tela.detectChanges();
    return (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent?.trim();
  }

  function escolherArquivo(pagina: HTMLElement, arquivo: File) {
    const entrada = pagina.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(entrada, 'files', { value: [arquivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
  }

  /** O FileReader termina quando quer: espera o pedido de leitura aparecer. */
  async function esperarLeitura() {
    for (let tentativa = 0; tentativa < 100; tentativa++) {
      const pedidos = http.match((r) => r.url === '/api/receipts/parse');
      if (pedidos.length) return pedidos[0];
      await new Promise((pronto) => setTimeout(pronto, 10));
    }
    throw new Error('A leitura do recibo nao foi pedida.');
  }

  it('aponta o que falta: item, valor, conta, cartao e parcelas', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Digite o nome do item.');

    digitar(pagina, '#item', 'Mercado do mês');
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Digite um valor válido.');

    digitar(pagina, '#valor', '300');
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Escolha de qual conta saiu o dinheiro.');
    expect(pagina.querySelector('#conta-debito')!.getAttribute('aria-invalid')).toBe('true');

    clicar(pagina, 'Cartão de crédito');
    tela.detectChanges();
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Escolha em qual cartão foi a compra.');

    digitar(pagina, '#cartao', 'c1');
    clicar(pagina, 'Parcelado');
    tela.detectChanges();
    digitar(pagina, '#parcelas', '1');
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Parcelado: de 2 a 48 parcelas.');
    expect(pagina.textContent).toContain('Vai para a fatura de');
    expect(http.match((r) => r.method === 'POST')).toEqual([]);
  });

  it('parcelado no cartao vai com as parcelas e avisa que o orcamento estourou', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#item', 'Geladeira');
    digitar(pagina, '#valor', '300');
    clicar(pagina, 'Cartão de crédito');
    tela.detectChanges();
    digitar(pagina, '#cartao', 'c1');
    clicar(pagina, 'Parcelado');
    tela.detectChanges();
    digitar(pagina, '#parcelas', '3');
    tela.detectChanges();
    expect(pagina.textContent).toContain('3x de');
    clicar(pagina, 'Lançar');

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/expenses');
    expect(pedido.request.body).toMatchObject({
      contaId: 'c1',
      parcelas: 3,
      recorrente: null,
      origem: 'Manual',
    });
    expect(pedido.request.body.itens[0]).toMatchObject({
      item: 'Geladeira',
      valor: 300,
      tipo: 'Variavel',
    });
    pedido.flush([gastoDeTeste({ categoria: 'Casa' })]);
    http
      .expectOne((r) => r.url === '/api/budgets')
      .flush([
        {
          id: 'o1',
          categoria: 'Casa',
          limite: 200,
          gasto: 300,
          percentual: 150,
          alerta: 'ESTOUROU',
        },
      ]);

    expect(lancados).toEqual([
      { mensagem: 'Gasto lançado. Casa passou do limite do mês (150%).', offline: false },
    ]);
    expect(localStorage.getItem('piggu_conta_padrao')).toBe('c1');
  });

  it('o aviso do orcamento muda com o quanto ja foi usado', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const lancar = (categoria: string) => {
      digitar(pagina, '#item', 'Cinema');
      digitar(pagina, '#valor', '30');
      digitar(pagina, '#conta-debito', 'b1');
      clicar(pagina, 'Lançar');
      http
        .expectOne((r) => r.method === 'POST')
        .flush(categoria ? [gastoDeTeste({ categoria })] : []);
      tela.detectChanges();
    };

    lancar('Lazer');
    http
      .expectOne((r) => r.url === '/api/budgets')
      .flush([
        { id: 'o1', categoria: 'Lazer', limite: 100, gasto: 85, percentual: 85, alerta: 'ATENCAO' },
      ]);
    lancar('Lazer');
    http.expectOne((r) => r.url === '/api/budgets').flush([]);
    lancar('Lazer');
    http.expectOne((r) => r.url === '/api/budgets').flush(null, { status: 500, statusText: 'x' });
    lancar('');

    expect(lancados.map((l) => l.mensagem)).toEqual([
      'Gasto lançado. Lazer já usou 85% do limite do mês.',
      'Gasto lançado.',
      'Gasto lançado.',
      'Gasto lançado.',
    ]);
  });

  it('sem internet o gasto fica na fila; erro do servidor aparece', () => {
    const tela = abrir([conta({ id: 'b1' })], []);
    const pagina: HTMLElement = tela.nativeElement;
    const fila = TestBed.inject(FilaDeGastos);
    const guardar = vi.spyOn(fila, 'guardar').mockImplementation(() => undefined);

    digitar(pagina, '#item', 'Café');
    digitar(pagina, '#valor', '5');
    clicar(pagina, 'Lançar');
    http.expectOne((r) => r.method === 'POST').error(new ProgressEvent('error'), { status: 0 });
    expect(guardar).toHaveBeenCalledOnce();
    expect(lancados[0].offline).toBe(true);

    digitar(pagina, '#item', 'Café');
    digitar(pagina, '#valor', '5');
    clicar(pagina, 'Lançar');
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ erro: 'Categoria inexistente.' }, { status: 400, statusText: 'x' });
    expect(alerta(tela)).toBe('Categoria inexistente.');
  });

  it('recorrente vira gasto fixo; cartao sem cadastro pede para cadastrar', () => {
    const tela = abrir([conta({ id: 'c1', tipo: 'CARTAO' })], []);
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#item', 'Streaming');
    digitar(pagina, '#valor', '40');
    clicar(pagina, 'Cartão de crédito');
    tela.detectChanges();
    clicar(pagina, 'Recorrente');
    tela.detectChanges();
    expect(pagina.textContent).toContain('Lança sozinho todo dia');
    clicar(pagina, 'Lançar');
    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body).toMatchObject({ contaId: 'c1', recorrente: true, parcelas: null });
    expect(pedido.request.body.itens[0].tipo).toBe('Fixo');
    pedido.flush([]);

    tela.componentInstance['contas'].set([]);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Nenhum cartão cadastrado ainda.');
    digitar(pagina, '#item', 'Streaming');
    digitar(pagina, '#valor', '40');
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toContain('Cadastre o cartão');
  });

  it('compra em outra moeda espera a cotacao e grava convertida', () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#item', 'Jantar');
    digitar(pagina, '#valor', '100');
    digitar(pagina, '#moeda-compra', 'USD');
    const cotacao = http.expectOne((r) => r.url === '/api/exchange-rate');
    expect(cotacao.request.params.get('de')).toBe('USD');
    clicar(pagina, 'Lançar');
    expect(alerta(tela)).toBe('Aguarde a cotação da moeda da compra.');

    cotacao.flush({ de: 'USD', para: 'EUR', taxa: 0.9, data: '2026-10-05' });
    tela.detectChanges();
    expect(pagina.textContent).toContain('cotação de hoje');
    expect(pagina.querySelector('summary')?.textContent).toContain('USD');

    tela.componentInstance['conferirPreco']();
    expect(http.match((r) => r.url === '/api/products/price-check')).toEqual([]);

    clicar(pagina, 'Lançar');
    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body.itens[0]).toMatchObject({
      valor: 90,
      moedaOriginal: 'USD',
      valorOriginal: 100,
    });
    pedido.flush([]);

    digitar(pagina, '#moeda-compra', 'GBP');
    http
      .expectOne((r) => r.url === '/api/exchange-rate')
      .flush({ erro: 'Sem cotação.' }, { status: 502, statusText: 'x' });
    expect(alerta(tela)).toBe('Sem cotação.');
    digitar(pagina, '#moeda-compra', 'EUR');
    expect(http.match((r) => r.url === '/api/exchange-rate')).toEqual([]);
  });

  it('dividir e compartilhar aparecem no resumo e vao no pedido', () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;
    const [dividirTitular, dividirBia, compartilhar] = [
      ...pagina.querySelectorAll<HTMLInputElement>('input[type=checkbox]'),
    ];

    dividirBia.click();
    dividirTitular.click();
    dividirTitular.click();
    compartilhar.click();
    tela.detectChanges();
    expect(pagina.querySelector('summary')?.textContent).toContain('dividido · compartilhado');

    digitar(pagina, '#item', 'Pizza');
    digitar(pagina, '#valor', '60');
    clicar(pagina, 'Lançar');
    const pedido = http.expectOne((r) => r.method === 'POST');
    expect(pedido.request.body).toMatchObject({ dividirCom: [ID_DA_BIA], compartilhado: true });
    pedido.flush([]);
  });

  it('avisa quando o preco passou da media do item', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const valor = pagina.querySelector<HTMLInputElement>('#valor')!;

    digitar(pagina, '#valor', '12');
    valor.dispatchEvent(new Event('blur'));
    expect(http.match((r) => r.url === '/api/products/price-check')).toEqual([]);

    digitar(pagina, '#item', 'Leite');
    valor.dispatchEvent(new Event('blur'));
    http
      .expectOne((r) => r.url === '/api/products/price-check')
      .flush({ acima: true, percentual: 30, media: 9.2 });
    tela.detectChanges();
    expect(pagina.querySelector('.alerta-preco')?.textContent).toContain('30% acima da média');

    valor.dispatchEvent(new Event('blur'));
    http
      .expectOne((r) => r.url === '/api/products/price-check')
      .flush(null, { status: 500, statusText: 'x' });
    tela.detectChanges();
    expect(pagina.querySelector('.alerta-preco')).toBeNull();
  });

  it('o atalho preenche o valor e poe o cursor no campo', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    tela.componentInstance.preencherValor(12.5);
    tela.componentInstance.preencherValor(0);
    tela.detectChanges();

    expect(tela.componentInstance['novoValor']()).toBe(12.5);
    expect(document.activeElement).toBe(pagina.querySelector('#valor'));
  });

  it('le o recibo, deixa conferir e lanca os itens', async () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;

    escolherArquivo(
      pagina,
      new File([new Uint8Array([1, 2, 3])], 'nota.jpg', { type: 'image/jpeg' }),
    );
    const leitura = await esperarLeitura();
    expect(leitura.request.body).toMatchObject({ imageBase64: 'AQID', mimeType: 'image/jpeg' });
    leitura.flush(RECIBO);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Mercado Bom');
    expect(pagina.textContent).toContain('lido pelo Piggu');
    expect(pagina.textContent).toContain('Confira o total.');

    digitar(pagina, '#confItem0', 'Leite integral');
    digitar(pagina, '#confCat0', 'Lazer');
    digitar(pagina, '#confValor0', '6');
    clicar(pagina, 'Remover', 1);
    tela.detectChanges();
    clicar(pagina, 'Lançar 1 item(ns)');

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/expenses');
    expect(pedido.request.body).toMatchObject({
      reciboId: 'rec1',
      origem: 'Foto',
      contaId: 'b1',
      itens: [{ item: 'Leite integral', categoria: 'Lazer', valor: 6 }],
    });
    pedido.flush([]);
    expect(lancados).toEqual([{ mensagem: 'Recibo lançado.', offline: false }]);
    tela.detectChanges();
    expect(pagina.textContent).not.toContain('Conferir recibo');
  });

  it('recibo sem item valido nao vai; erro ao lancar aparece; descartar limpa', async () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;

    escolherArquivo(pagina, new File([new Uint8Array([1])], 'nota.png', { type: 'image/png' }));
    (await esperarLeitura()).flush({
      ...RECIBO,
      origem: 'GEMINI',
      aviso: null,
      estabelecimento: '',
    });
    tela.detectChanges();
    expect(pagina.textContent).toContain('lido pelo Gemini');

    clicar(pagina, 'Lançar 2 item(ns)');
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ erro: 'Recibo repetido.' }, { status: 409, statusText: 'x' });
    expect(alerta(tela)).toBe('Recibo repetido.');

    digitar(pagina, '#confItem0', ' ');
    digitar(pagina, '#confItem1', '');
    clicar(pagina, 'Lançar 2 item(ns)');
    expect(alerta(tela)).toBe('Adicione pelo menos um item válido.');

    clicar(pagina, 'Descartar');
    tela.detectChanges();
    expect(pagina.textContent).not.toContain('Conferir recibo');
  });

  it('foto que so a IA le pede autorizacao antes de ir ao Google', async () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;
    const consentimento = { erro: 'Esta foto precisa da IA.', codigo: 'CONSENTIMENTO_NECESSARIO' };

    escolherArquivo(
      pagina,
      new File([new Uint8Array([1, 2, 3])], 'nota.jpg', { type: 'image/jpeg' }),
    );
    (await esperarLeitura()).flush(consentimento, { status: 409, statusText: 'x' });
    tela.detectChanges();
    expect(pagina.querySelector('[role=alertdialog]')?.textContent).toContain(
      'Esta foto precisa da IA.',
    );

    clicar(pagina, 'Autorizar e ler');
    const autorizada = http.expectOne((r) => r.url === '/api/receipts/parse');
    expect(autorizada.request.body.imageBase64).toBe('AQID');
    expect(autorizada.request.body.versaoDoAviso).toBeTruthy();
    autorizada.flush(consentimento, { status: 409, statusText: 'x' });
    tela.detectChanges();

    clicar(pagina, 'Agora não');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alertdialog]')).toBeNull();
    tela.componentInstance['autorizarIa']();
    expect(http.match((r) => r.url === '/api/receipts/parse')).toEqual([]);
  });

  it('erro na leitura e foto grande demais aparecem; sem leituras o botao leva ao Premium', async () => {
    const tela = abrir([conta({ id: 'b1' })]);
    const pagina: HTMLElement = tela.nativeElement;

    escolherArquivo(pagina, new File([new Uint8Array([1])], 'nota.jpg', { type: 'image/jpeg' }));
    (await esperarLeitura()).flush({ erro: 'Não li nada.' }, { status: 422, statusText: 'x' });
    expect(alerta(tela)).toBe('Não li nada.');

    const grande = new File([new Uint8Array(1)], 'g.jpg', { type: 'image/jpeg' });
    Object.defineProperty(grande, 'size', { value: 6 * 1024 * 1024 });
    escolherArquivo(pagina, grande);
    expect(alerta(tela)).toContain('O limite é de 5 MB.');

    tela.componentInstance['leiturasRestantes'].set(0);
    tela.detectChanges();
    expect(pagina.textContent).toContain('As leituras grátis deste mês acabaram.');
  });

  it('sem o contador de leituras o botao segue funcionando', () => {
    const tela = TestBed.createComponent(FormularioDeGasto);
    tela.detectChanges();
    http.expectOne('/api/categories').flush(null);
    http.expectOne('/api/accounts').flush(null);
    http.expectOne('/api/family').flush(null);
    http.expectOne('/api/receipts/usage').flush(null, { status: 500, statusText: 'x' });
    tela.detectChanges();

    expect((tela.nativeElement as HTMLElement).textContent).not.toContain('leituras');
    expect((tela.nativeElement as HTMLElement).querySelector('input[type=file]')).not.toBeNull();
  });
});
