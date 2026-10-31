import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA } from '../../testing/tela';
import { FilaDeGastos } from './fila-de-gastos';

const pedido = (item: string) => ({
  data: '2026-10-11',
  origem: 'Manual',
  itens: [{ item, categoria: null, valor: 10, tipo: 'Variavel' }],
});

/** Gastos sem sinal: guardados por pessoa e enviados na ordem quando a rede volta. */
describe('FilaDeGastos', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    localStorage.removeItem('piggu_fila_de_gastos');
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('piggu_fila_de_gastos');
  });

  it('envia na ordem; sem rede para ali e guarda o resto', async () => {
    const fila = TestBed.inject(FilaDeGastos);
    fila.guardar(pedido('Pao'));
    fila.guardar(pedido('Leite'));
    fila.guardar(pedido('Cafe'));
    expect(JSON.parse(localStorage.getItem('piggu_fila_de_gastos')!)).toHaveLength(3);

    const envio = fila.enviar();
    const primeiro = http.expectOne('/api/expenses');
    expect(primeiro.request.body.itens[0].item).toBe('Pao');
    primeiro.flush([]);
    await Promise.resolve();
    await new Promise((pronto) => setTimeout(pronto));
    http.expectOne('/api/expenses').error(new ProgressEvent('error'), { status: 0 });
    await envio;

    expect(fila.pendentes().map((g) => g.pedido.itens[0].item)).toEqual(['Leite', 'Cafe']);
    expect(fila.enviados()).toBe(1);
  });

  it('recusado pelo servidor sai da fila e e contado; de outra pessoa nao e enviado', async () => {
    const fila = TestBed.inject(FilaDeGastos);
    fila.guardar(pedido('Invalido'));
    auth.usuario.set(usuarioDeTeste({ id: 'outra-pessoa' }));
    fila.guardar(pedido('Da outra'));
    auth.usuario.set(usuarioDeTeste());

    const envio = fila.enviar();
    http.expectOne('/api/expenses').flush({}, { status: 400, statusText: 'Bad Request' });
    await envio;

    expect(fila.recusados()).toBe(1);
    expect(fila.pendentes()).toEqual([]);
    expect(JSON.parse(localStorage.getItem('piggu_fila_de_gastos')!)).toHaveLength(1);
  });

  it('sessao encerrada (401) guarda o gasto; 422 tira da fila e conta como recusado', async () => {
    const fila = TestBed.inject(FilaDeGastos);
    fila.guardar(pedido('Pao'));

    let envio = fila.enviar();
    http.expectOne('/api/expenses').flush({}, { status: 401, statusText: 'Unauthorized' });
    await envio;
    expect(fila.pendentes()).toHaveLength(1);
    expect(fila.recusados()).toBe(0);
    expect(JSON.parse(localStorage.getItem('piggu_fila_de_gastos')!)).toHaveLength(1);

    envio = fila.enviar();
    http.expectOne('/api/expenses').flush({}, { status: 422, statusText: 'Unprocessable' });
    await envio;
    expect(fila.pendentes()).toEqual([]);
    expect(fila.recusados()).toBe(1);
  });

  it('cartao apagado (404 do Piggu) tira da fila; 404 sem o codigo, de proxy, espera', async () => {
    const fila = TestBed.inject(FilaDeGastos);
    fila.guardar(pedido('Pao'));

    let envio = fila.enviar();
    http.expectOne('/api/expenses').flush('<html>', { status: 404, statusText: 'Not Found' });
    await envio;
    expect(fila.pendentes()).toHaveLength(1);

    envio = fila.enviar();
    http
      .expectOne('/api/expenses')
      .flush({ erro: 'Conta ou cartao nao encontrado.', codigo: 'NotFoundException' }, { status: 404, statusText: 'Not Found' });
    await envio;
    expect(fila.pendentes()).toEqual([]);
    expect(fila.recusados()).toBe(1);
  });

  it('outra aba guardou um gasto: le de novo e nao grava por cima', () => {
    const fila = TestBed.inject(FilaDeGastos);
    fila.guardar(pedido('Pao'));
    const daOutraAba = [
      ...JSON.parse(localStorage.getItem('piggu_fila_de_gastos')!),
      { usuario: usuarioDeTeste().id, pedido: pedido('Leite'), guardadoEm: '2026-10-11T10:00:00Z' },
    ];
    localStorage.setItem('piggu_fila_de_gastos', JSON.stringify(daOutraAba));
    window.dispatchEvent(new StorageEvent('storage', { key: 'piggu_fila_de_gastos' }));

    fila.guardar(pedido('Cafe'));

    expect(fila.pendentes().map((g) => g.pedido.itens[0].item)).toEqual(['Pao', 'Leite', 'Cafe']);
    expect(JSON.parse(localStorage.getItem('piggu_fila_de_gastos')!)).toHaveLength(3);
  });
});
