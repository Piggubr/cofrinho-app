import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AReceber, ContaFixa, Receita } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { Lancamentos } from '../../core/ui/lancamentos';
import { dataIso, mesKey } from '../../core/ui/datas';
import { AuthFalso } from '../../testing/auth-falso';
import { ID_DA_BIA, ID_DA_TITULAR, PROVEDORES_DE_TELA, clicar, digitar } from '../../testing/tela';
import { Incomes } from './incomes';

const receita: Receita = {
  id: 'r1',
  data: '2026-10-05',
  descricao: 'Salário',
  categoria: 'Salário',
  valor: 3000,
  usuario: ID_DA_TITULAR,
};

const salarioFixo: ContaFixa = {
  id: 'f1',
  descricao: 'Salário',
  categoria: 'Salário',
  valor: 3000,
  dia: 5,
  automatico: true,
  vencimento: '2026-10-05',
  situacao: 'PENDENTE',
  tipo: 'RECEITA',
};

const pizza: AReceber = {
  id: 'a1',
  quem: 'João',
  descricao: 'Metade da pizza',
  valor: 25,
  data: '2020-01-10',
};

/** Receitas do mes, as que entram todo mes e o dinheiro a receber. */
describe('Incomes: acoes', () => {
  let http: HttpTestingController;
  const mes = mesKey(new Date());

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Incomes],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: new AuthFalso() }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function responderLista(
    receitas: Receita[],
    recorrentes: ContaFixa[],
    aReceber: AReceber[],
    mesDaLista = mes,
  ) {
    http
      .expectOne((r) => r.url === '/api/incomes' && r.params.get('mes') === mesDaLista)
      .flush(receitas);
    http
      .expectOne((r) => r.url === '/api/bills' && r.params.get('tipo') === 'RECEITA')
      .flush(recorrentes);
    responderAReceber(aReceber);
  }

  function responderAReceber(aReceber: AReceber[]) {
    http.expectOne('/api/incomes/receivables').flush(aReceber);
  }

  function responderCards() {
    for (const pedido of http.match(
      (r) => r.url === '/api/incomes/categories' || r.url === '/api/reports/month',
    )) {
      pedido.flush(pedido.request.url.includes('reports') ? null : ['Salário']);
    }
  }

  function abrir(
    receitas: Receita[] = [receita],
    recorrentes: ContaFixa[] = [],
    aReceber: AReceber[] = [],
  ) {
    const tela = TestBed.createComponent(Incomes);
    responderLista(receitas, recorrentes, aReceber);
    tela.detectChanges();
    responderCards();
    tela.detectChanges();
    return tela;
  }

  it('apaga a receita depois de confirmar e recarrega o mes', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = abrir([
      receita,
      { ...receita, id: 'r2', usuario: ID_DA_BIA, compartilhado: true },
    ]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('compartilhada');

    clicar(pagina, 'Apagar');

    expect(confirmar.mock.calls[0][0]).toContain('Salário');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/incomes/r1').flush(null);
    responderLista([], [], []);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Nenhuma receita neste mês');
  });

  it('desistir de apagar nao chama a API; erro ao apagar aparece', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    confirmar.mockReturnValue(true);
    clicar(pagina, 'Apagar');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Receita de outra pessoa.' }, { status: 403, statusText: 'Proibido' });
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Receita de outra pessoa.');
  });

  it('salario em duas parcelas: confere o dia e o valor da 2ª e mostra as duas na lista', () => {
    const tela = abrir(
      [],
      [{ ...salarioFixo, dia: 20, valor: 2000, segundoDia: 5, segundoValor: 3000 }],
    );
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Dia 20:');
    expect(pagina.textContent).toContain('dia 5:');
    expect(pagina.textContent).toContain('5.000,00');

    digitar(pagina, '#valor-receita', '2000');
    digitar(pagina, '#descricao-receita', 'Salário');
    pagina.querySelector<HTMLInputElement>('label.repetir input[type=checkbox]')!.click();
    tela.detectChanges();
    [...pagina.querySelectorAll<HTMLLabelElement>('label.repetir')]
      .find((l) => l.textContent?.includes('Em duas parcelas'))!
      .querySelector('input')!
      .click();
    tela.detectChanges();

    clicar(pagina, 'Lançar receita');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Informe o dia da 2ª parcela',
    );

    digitar(pagina, '#segundo-dia', '5');
    clicar(pagina, 'Lançar receita');
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Digite o valor da 2ª parcela',
    );

    digitar(pagina, '#segundo-valor', '3000');
    clicar(pagina, 'Lançar receita');
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/incomes');
    expect(pedido.request.body).toMatchObject({
      valor: 2000,
      recorrente: true,
      segundoDia: 5,
      segundoValor: 3000,
    });
  });

  it('para de repetir a receita fixa, e o erro aparece', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = abrir([], [salarioFixo]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Todo dia 5');

    clicar(pagina, 'Parar de repetir');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    confirmar.mockReturnValue(true);
    clicar(pagina, 'Parar de repetir');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/bills/f1').flush(null);
    responderLista([], [], []);

    clicar(pagina, 'Parar de repetir');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Falhou.' }, { status: 500, statusText: 'Erro' });
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Falhou.');
  });

  it('a receber pede quem, valor e data antes de guardar', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const alerta = () => {
      tela.detectChanges();
      return pagina.querySelector('[role=alert]')?.textContent;
    };

    clicar(pagina, 'Guardar a receber');
    expect(alerta()).toContain('Diga quem está te devendo.');
    expect(pagina.querySelector('#quem-deve')!.getAttribute('aria-invalid')).toBe('true');

    digitar(pagina, '#quem-deve', 'João');
    clicar(pagina, 'Guardar a receber');
    expect(alerta()).toContain('Digite quanto vão te pagar.');

    digitar(pagina, '#valor-a-receber', '25');
    digitar(pagina, '#data-a-receber', '');
    clicar(pagina, 'Guardar a receber');
    expect(alerta()).toContain('Escolha a data combinada');
  });

  it('guarda o a receber e mostra a conta atrasada', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#quem-deve', ' João ');
    digitar(pagina, '#motivo-a-receber', 'Metade da pizza');
    digitar(pagina, '#valor-a-receber', '25');
    digitar(pagina, '#data-a-receber', '2020-01-10');
    clicar(pagina, 'Guardar a receber');

    const pedido = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/incomes/receivables',
    );
    expect(pedido.request.body).toEqual({
      quem: 'João',
      descricao: 'Metade da pizza',
      valor: 25,
      data: '2020-01-10',
    });
    pedido.flush(pizza);
    responderAReceber([pizza]);
    tela.detectChanges();

    expect(pagina.querySelector('[role=status]')?.textContent).toContain('Guardado.');
    expect(pagina.querySelector('.atrasada')?.textContent).toContain('Está te devendo desde');
    expect(tela.componentInstance['quem']()).toBe('');
  });

  it('erro ao guardar o a receber aparece e libera o botao', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#quem-deve', 'João');
    digitar(pagina, '#valor-a-receber', '25');
    clicar(pagina, 'Guardar a receber');
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ erro: 'Valor alto demais.' }, { status: 400, statusText: 'Ruim' });
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Valor alto demais.');
    const botao = [...pagina.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Guardar a receber'),
    )!;
    expect(botao.disabled).toBe(false);
  });

  it('"Recebi" vira receita de hoje; o erro aparece', () => {
    const futuro = dataIso(new Date(new Date().getFullYear() + 1, 0, 1));
    const tela = abrir([], [], [{ ...pizza, data: futuro, descricao: '' }]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Combinado para');

    clicar(pagina, 'Recebi');
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/incomes/receivables/a1/receive')
      .flush(receita);
    responderLista([receita], [], []);
    tela.detectChanges();
    expect(pagina.querySelector('[role=status]')?.textContent).toContain('João pagou.');

    tela.componentInstance['aReceber'].set([pizza]);
    tela.detectChanges();
    clicar(pagina, 'Recebi');
    http
      .expectOne((r) => r.url.endsWith('/receive'))
      .flush({ erro: 'Já recebido.' }, { status: 409, statusText: 'x' });
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Já recebido.');
  });

  it('apaga o a receber depois de confirmar; desistir nao chama a API', () => {
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const tela = abrir([], [], [pizza]);
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    confirmar.mockReturnValue(true);
    clicar(pagina, 'Apagar');
    expect(confirmar.mock.calls[1][0]).toContain('João');
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/incomes/receivables/a1')
      .flush(null);
    responderAReceber([]);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Ninguém te deve nada agora.');

    tela.componentInstance['aReceber'].set([pizza]);
    tela.detectChanges();
    clicar(pagina, 'Apagar');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Não deu.' }, { status: 500, statusText: 'x' });
    tela.detectChanges();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Não deu.');
  });

  it('troca de mes, recarrega quando algo e lancado pelo "+" e aguenta falhas na carga', () => {
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;
    const anterior = mesKey(new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1));

    clicar(pagina, 'Mês anterior');
    http
      .expectOne((r) => r.url === '/api/incomes' && r.params.get('mes') === anterior)
      .flush({ erro: 'Fora do ar.' }, { status: 503, statusText: 'x' });
    http.expectOne((r) => r.url === '/api/bills').flush(null, { status: 503, statusText: 'x' });
    http.expectOne('/api/incomes/receivables').flush(null, { status: 503, statusText: 'x' });
    tela.detectChanges();
    responderCards();
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Fora do ar.');

    clicar(pagina, 'Próximo mês');
    responderLista([], [], []);
    tela.detectChanges();
    responderCards();

    TestBed.inject(Lancamentos).avisar();
    tela.detectChanges();
    responderLista([receita], [], []);
    tela.detectChanges();
    expect(pagina.textContent).toContain('Total:');
  });
});
