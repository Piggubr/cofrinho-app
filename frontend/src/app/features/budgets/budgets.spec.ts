import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Budgets } from './budgets';

/** O alerta aparece na barra; no gratuito, o formulario vira convite ao Premium. */
describe('Budgets', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Budgets],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(): HTMLElement {
    return abrirTela().nativeElement;
  }

  function abrirTela() {
    const tela = TestBed.createComponent(Budgets);
    tela.detectChanges();
    // O limite do mes (no topo) tem o proprio teste em goals.spec.ts.
    http.expectOne('/api/monthly-goals').flush({});
    http.expectOne((r) => r.url === '/api/expenses').flush([]);
    http.expectOne('/api/categories').flush({ categorias: ['Lazer', 'Mercado'] });
    http
      .expectOne((r) => r.url === '/api/budgets')
      .flush([
        {
          id: 'o1',
          categoria: 'Mercado',
          limite: 100,
          gasto: 120,
          percentual: 120,
          alerta: 'ESTOUROU',
        },
      ]);
    tela.detectChanges();
    return tela;
  }

  function clicar(pagina: HTMLElement, texto: string) {
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!.click();
  }

  it('categoria estourada aparece com a barra marcada', () => {
    const pagina = abrir();
    expect(pagina.querySelector('.progresso.estourado')).not.toBeNull();
    expect(pagina.textContent).toContain('120%');
  });

  it('no gratuito nao ha formulario, so o convite', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const pagina = abrir();
    expect(pagina.querySelector('#limite-orcamento')).toBeNull();
    expect(pagina.textContent).toContain('Piggu Premium');
  });

  it('todas as categorias aparecem para escolher, inclusive a que ja tem limite', async () => {
    const tela = abrirTela();
    await tela.whenStable();
    const opcoes = [
      ...(tela.nativeElement as HTMLElement).querySelectorAll('#categoria-orcamento option'),
    ].map((o) => o.textContent?.trim());
    expect(opcoes).toEqual(['Escolha', 'Lazer', 'Mercado']);
  });

  it('sem categoria ou com limite zero, nada vai para o backend', () => {
    const tela = abrirTela();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Salvar categoria');
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('Escolha a categoria');
    http.expectNone((r) => r.method === 'PUT');
  });

  it('editar leva a categoria e o limite ao formulario, e salvar recarrega a lista', async () => {
    const tela = abrirTela();
    const pagina: HTMLElement = tela.nativeElement;

    pagina.querySelector<HTMLButtonElement>('button[aria-label=Mudar]')!.click();
    tela.detectChanges();
    await tela.whenStable();
    expect(pagina.querySelector<HTMLInputElement>('#limite-orcamento')!.value).toBe('100');

    const limite = pagina.querySelector<HTMLInputElement>('#limite-orcamento')!;
    limite.value = '150';
    limite.dispatchEvent(new Event('input'));
    clicar(pagina, 'Salvar categoria');
    const pedido = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/budgets');
    expect(pedido.request.body).toEqual({ categoria: 'Mercado', limite: 150 });
    pedido.flush(null);
    http
      .expectOne((r) => r.url === '/api/budgets' && r.method === 'GET')
      .flush([
        {
          id: 'o1',
          categoria: 'Mercado',
          limite: 150,
          gasto: 120,
          percentual: 80,
          alerta: 'ATENCAO',
        },
      ]);
    tela.detectChanges();

    expect(pagina.querySelector('.progresso.atencao')).not.toBeNull();
    expect(pagina.querySelector('[role=alert]')).toBeNull();
  });

  it('erro ao salvar aparece e libera o botao', async () => {
    const tela = abrirTela();
    const pagina: HTMLElement = tela.nativeElement;
    pagina.querySelector<HTMLButtonElement>('button[aria-label=Mudar]')!.click();
    tela.detectChanges();
    await tela.whenStable();

    clicar(pagina, 'Salvar categoria');
    http
      .expectOne((r) => r.method === 'PUT')
      .flush(
        { erro: 'Recurso do Piggu Premium.' },
        { status: 402, statusText: 'Payment Required' },
      );
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Recurso do Piggu Premium.',
    );
    expect(
      [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes('Salvar categoria'))!
        .disabled,
    ).toBe(false);
  });

  it('apagar segue livre no gratuito e recarrega a lista', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const tela = abrirTela();
    const pagina: HTMLElement = tela.nativeElement;

    pagina.querySelector<HTMLButtonElement>('button[aria-label=Apagar]')!.click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/budgets/o1').flush(null);
    http.expectOne((r) => r.url === '/api/budgets' && r.method === 'GET').flush([]);
    tela.detectChanges();

    expect(pagina.textContent).toContain('Nenhuma categoria com limite ainda.');
  });
});
