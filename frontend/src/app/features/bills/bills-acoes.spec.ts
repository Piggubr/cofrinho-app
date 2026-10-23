import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ContaFixa } from '../../core/api/models';
import { mesKey } from '../../core/ui/datas';
import { PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Bills } from './bills';

function conta(mudancas: Partial<ContaFixa>): ContaFixa {
  return {
    id: 'c1',
    descricao: 'Aluguel',
    categoria: 'Casa',
    valor: 1500,
    dia: 5,
    automatico: false,
    vencimento: '2026-10-05',
    situacao: 'PENDENTE',
    ...mudancas,
  };
}

const ALUGUEL = conta({});
const LUZ = conta({ id: 'c2', descricao: 'Luz', valor: 120, automatico: true });
const INTERNET = conta({ id: 'c3', descricao: 'Internet', valor: 100, situacao: 'PAGA' });

/** Contas do mes: criar, pagar varias de uma vez, apagar e trocar de mes. */
describe('Bills: acoes', () => {
  let http: HttpTestingController;
  const mes = mesKey(new Date());

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Bills], providers: PROVEDORES_DE_TELA });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function responder(contas: ContaFixa[], mesDaLista = mes) {
    http
      .expectOne(
        (r) => r.method === 'GET' && r.url === '/api/bills' && r.params.get('mes') === mesDaLista,
      )
      .flush(contas);
  }

  function abrir(contas: ContaFixa[] = [ALUGUEL, LUZ, INTERNET]) {
    const tela = TestBed.createComponent(Bills);
    tela.detectChanges();
    http.expectOne('/api/categories').flush({ categorias: ['Casa', 'Contas'] });
    responder(contas);
    tela.detectChanges();
    return tela;
  }

  function texto(tela: ComponentFixture<Bills>, seletor: string) {
    tela.detectChanges();
    return (tela.nativeElement as HTMLElement).querySelector(seletor)?.textContent?.trim();
  }

  it('cria a conta fixa e pede descricao, valor e dia validos', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Nenhuma conta fixa ainda');

    clicar(pagina, 'Criar conta fixa');
    expect(texto(tela, '[role=alert]')).toContain('Preencha descrição, valor');

    digitar(pagina, '#descricao-conta', ' Condomínio ');
    digitar(pagina, '#valor-conta', '450');
    digitar(pagina, '#dia-conta', '40');
    clicar(pagina, 'Criar conta fixa');
    expect(texto(tela, '[role=alert]')).toContain('entre 1 e 31');

    digitar(pagina, '#dia-conta', '8');
    digitar(pagina, '#categoria-conta', 'Contas');
    pagina.querySelector<HTMLInputElement>('.caixa input')!.click();
    clicar(pagina, 'Criar conta fixa');

    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/bills');
    expect(pedido.request.body).toEqual({
      descricao: 'Condomínio',
      categoria: 'Contas',
      valor: 450,
      dia: 8,
      automatico: true,
    });
    pedido.flush(null);
    responder([conta({ descricao: 'Condomínio', automatico: true })]);
    expect(texto(tela, '[role=status]')).toBe('Conta fixa criada.');
    expect(pagina.textContent).toContain('lança sozinha');
  });

  it('erro ao criar aparece e libera o botao', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#descricao-conta', 'Luz');
    digitar(pagina, '#valor-conta', '100');
    clicar(pagina, 'Criar conta fixa');
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ erro: 'Limite de contas.' }, { status: 400, statusText: 'x' });

    expect(texto(tela, '[role=alert]')).toBe('Limite de contas.');
    const botao = [...pagina.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Criar conta fixa'),
    )!;
    expect(botao.disabled).toBe(false);
  });

  it('marca todas as em aberto e paga uma por vez', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.querySelectorAll('.marcar-conta').length).toBe(2);
    expect(pagina.textContent).toContain('Paga');

    pagina.querySelector<HTMLInputElement>('.pagar-varias input')!.click();
    tela.detectChanges();
    expect(pagina.querySelector('.pagar-varias button')?.textContent).toContain('Pagar 2');

    clicar(pagina, 'Pagar 2');
    const primeira = http.expectOne((r) => r.url === '/api/bills/c1/pay');
    expect(primeira.request.params.get('mes')).toBe(mes);
    primeira.flush({});
    http.expectOne((r) => r.url === '/api/bills/c2/pay').flush({});
    responder([{ ...ALUGUEL, situacao: 'PAGA' }, { ...LUZ, situacao: 'PAGA' }, INTERNET]);

    expect(texto(tela, '[role=status]')).toBe('2 contas lançadas nos gastos.');
    expect(pagina.querySelector('.pagar-varias')).toBeNull();
  });

  it('marca e desmarca uma a uma; desmarcar todas zera', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const [aluguel, luz] = [...pagina.querySelectorAll<HTMLInputElement>('.marcar-conta')];

    luz.click();
    tela.detectChanges();
    expect(pagina.querySelector('.pagar-varias button')?.textContent).toContain('Pagar 1');
    aluguel.click();
    tela.detectChanges();
    expect(pagina.querySelector<HTMLInputElement>('.pagar-varias input')!.checked).toBe(true);
    luz.click();
    tela.detectChanges();
    expect(pagina.querySelector('.pagar-varias button')?.textContent).toContain('Pagar 1');

    pagina.querySelector<HTMLInputElement>('.pagar-varias input')!.click();
    pagina.querySelector<HTMLInputElement>('.pagar-varias input')!.click();
    tela.detectChanges();
    expect(pagina.querySelector<HTMLButtonElement>('.pagar-varias button')!.disabled).toBe(true);
  });

  it('se uma conta falha ao pagar, o erro aparece', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    pagina.querySelector<HTMLInputElement>('.pagar-varias input')!.click();
    tela.detectChanges();
    clicar(pagina, 'Pagar 2');
    http
      .expectOne((r) => r.url === '/api/bills/c1/pay')
      .flush({ erro: 'Conta já paga.' }, { status: 409, statusText: 'x' });

    expect(texto(tela, '[role=alert]')).toBe('Conta já paga.');
  });

  it('apaga a conta depois de confirmar; desistir nao chama a API', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = abrir([ALUGUEL]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    confirmar.mockReturnValue(true);
    clicar(pagina, 'Apagar');
    expect(confirmar.mock.calls[1][0]).toContain('Aluguel');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/bills/c1').flush(null);
    responder([]);
    expect(texto(tela, '[role=status]')).toBe('Conta fixa apagada.');
  });

  it('troca de mes e mostra o erro da carga', () => {
    const tela = abrir([ALUGUEL]);
    const pagina: HTMLElement = tela.nativeElement;
    const hoje = new Date();

    clicar(pagina, 'Próximo mês');
    const seguinte = mesKey(new Date(hoje.getFullYear(), hoje.getMonth() + 1, 1));
    http
      .expectOne((r) => r.url === '/api/bills' && r.params.get('mes') === seguinte)
      .flush({ erro: 'Fora do ar.' }, { status: 503, statusText: 'x' });
    expect(texto(tela, '[role=alert]')).toBe('Fora do ar.');

    clicar(pagina, 'Mês anterior');
    responder([ALUGUEL]);
    expect(pagina.textContent).toContain('A vencer');
  });
});
