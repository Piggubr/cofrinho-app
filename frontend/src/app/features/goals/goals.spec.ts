import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Gasto } from '../../core/api/models';
import { APP_CONFIG } from '../../core/config/app-config';
import { mesKey } from '../../core/ui/datas';
import { Goals } from './goals';

function gasto(valor: number): Gasto {
  return {
    id: `g${valor}`,
    data: '2026-10-01',
    reciboId: '',
    estabelecimento: 'Mercado',
    item: 'Compras',
    categoria: 'Mercado',
    valor,
    tipo: 'DEBITO',
    origem: 'MANUAL',
    usuario: '11111111-1111-1111-1111-111111111111',
    registradoEm: '2026-10-01T10:00:00Z',
  };
}

/** A meta do mes em curso mostra quanto dela os gastos ja consumiram. */
describe('Goals', () => {
  let http: HttpTestingController;
  const mesAtual = mesKey(new Date());

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Goals],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(metas: Record<string, number>, gastos: Gasto[]) {
    const tela = TestBed.createComponent(Goals);
    tela.detectChanges();
    http.expectOne('/api/monthly-goals').flush(metas);
    const pedido = http.expectOne((r) => r.url === '/api/expenses');
    expect(pedido.request.params.get('mes')).toBe(mesAtual);
    pedido.flush(gastos);
    tela.detectChanges();
    return tela;
  }

  function digitarLimite(pagina: HTMLElement, valor: string) {
    const campo = pagina.querySelector<HTMLInputElement>('#limite')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  function clicar(pagina: HTMLElement, texto: string) {
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!.click();
  }

  it('mostra o quanto da meta do mes ja foi usado', async () => {
    const tela = abrir({ [mesAtual]: 1000 }, [gasto(250), gasto(150)]);
    const pagina: HTMLElement = tela.nativeElement;
    await tela.whenStable();

    expect(pagina.textContent).toContain('40% usado');
    expect(pagina.textContent).not.toContain('limite ultrapassado');
    expect(pagina.querySelector<HTMLInputElement>('#limite')!.value).toBe('1000');
  });

  it('gasto acima da meta marca a meta como ultrapassada e a barra para em 100%', () => {
    const tela = abrir({ [mesAtual]: 300 }, [gasto(450)]);
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.textContent).toContain('100% usado');
    expect(pagina.textContent).toContain('limite ultrapassado');
    expect(pagina.querySelector('.progresso')!.classList).toContain('estourado');
  });

  it('sem meta no mes, convida a definir uma', () => {
    const tela = abrir({}, [gasto(80)]);
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.textContent).toContain('Nenhum limite definido para este mês ainda.');
    expect(pagina.textContent).toContain('Nenhum limite cadastrado.');
  });

  it('valor vazio ou zero nao vai para o backend', async () => {
    const tela = abrir({}, []);
    const pagina: HTMLElement = tela.nativeElement;
    await tela.whenStable();

    digitarLimite(pagina, '0');
    clicar(pagina, 'Salvar limite');
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Digite um valor válido');
    http.expectNone('/api/monthly-goals');
  });

  it('salva a meta do mes escolhido e atualiza o historico', async () => {
    const tela = abrir({}, [gasto(100)]);
    const pagina: HTMLElement = tela.nativeElement;
    await tela.whenStable();

    digitarLimite(pagina, '500');
    clicar(pagina, 'Salvar limite');
    const pedido = http.expectOne((r) => r.url === '/api/monthly-goals' && r.method === 'PUT');
    expect(pedido.request.body).toEqual({ mes: mesAtual, limite: 500 });
    pedido.flush({ [mesAtual]: 500 });
    tela.detectChanges();

    expect(pagina.textContent).toContain('20% usado');
    expect(pagina.querySelectorAll('.linha').length).toBe(1);
  });

  it('erro do backend ao salvar aparece e libera o botao', async () => {
    const tela = abrir({}, []);
    const pagina: HTMLElement = tela.nativeElement;
    await tela.whenStable();

    digitarLimite(pagina, '500');
    clicar(pagina, 'Salvar limite');
    http
      .expectOne((r) => r.method === 'PUT')
      .flush({ erro: 'Limite fora do permitido.' }, { status: 400, statusText: 'Bad Request' });
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Limite fora do permitido.',
    );
    const botao = [...pagina.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Salvar limite'),
    )!;
    expect(botao.disabled).toBe(false);
  });

  it('editar uma meta do historico leva o mes e o valor para o formulario', async () => {
    const tela = abrir({ '2026-01': 800, '2026-02': 900 }, []);
    const pagina: HTMLElement = tela.nativeElement;

    pagina.querySelectorAll<HTMLButtonElement>('.linha button')[1].click();
    tela.detectChanges();
    await tela.whenStable();

    expect(pagina.querySelector<HTMLInputElement>('#mes')!.value).toBe('2026-01');
    expect(pagina.querySelector<HTMLInputElement>('#limite')!.value).toBe('800');
  });

  it('falha ao carregar mostra o erro e para de carregar', () => {
    const tela = TestBed.createComponent(Goals);
    tela.detectChanges();
    http.expectOne('/api/monthly-goals').flush(null, { status: 500, statusText: 'Erro' });
    http.expectOne((r) => r.url === '/api/expenses');
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.querySelector('[role=alert]')).not.toBeNull();
    expect(pagina.textContent).not.toContain('Carregando...');
  });
});
