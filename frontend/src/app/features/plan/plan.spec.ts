import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { InfoDoPlano } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Plan } from './plan';

const GRATUITO: InfoDoPlano = {
  plano: 'GRATUITO',
  premiumAte: null,
  origem: null,
  reembolsoAte: null,
  assinaturaDisponivel: true,
  site: { mensal: '19,90', anual: '199,00' },
  app: { mensal: '22,89', anual: '228,85' },
  gratuito: ['Gastos'],
  premium: ['Open Finance'],
};

/** Assinar leva para a pagina do provedor; voltar de la renova o token antes de ler o plano. */
describe('Plan', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  function montar(assinatura: string | null = null) {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.configureTestingModule({
      imports: [Plan],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(assinatura ? { assinatura } : {}) },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(Plan);
  }

  afterEach(() => http.verify());

  it('mostra o preco do site e manda para o pagamento do periodo escolhido', async () => {
    const tela = montar();
    const irPara = vi
      .spyOn(tela.componentInstance as unknown as { irPara: (url: string) => void }, 'irPara')
      .mockImplementation(() => undefined);
    tela.detectChanges();
    http.expectOne('/api/billing/plan').flush(GRATUITO);
    await tela.whenStable();
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('R$ 19,90');
    expect(pagina.textContent).not.toContain('22,89');

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Assinar anual'))!
      .click();
    const pedido = http.expectOne('/api/billing/checkout');
    expect(pedido.request.body).toEqual({ periodo: 'ANUAL' });
    pedido.flush({ url: 'https://checkout.stripe.test/s' });
    await tela.whenStable();

    expect(irPara).toHaveBeenCalledWith('https://checkout.stripe.test/s');
  });

  it('ao voltar do pagamento renova o token antes de consultar o plano', async () => {
    const tela = montar('ok');
    tela.detectChanges();
    await tela.whenStable();
    http.expectOne('/api/billing/plan').flush(GRATUITO);

    expect(auth.renovacoes()).toBe(1);
  });

  it('nos 7 dias do arrependimento mostra o reembolso e, confirmado, cancela', async () => {
    const tela = montar();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    tela.detectChanges();
    const premium: InfoDoPlano = {
      ...GRATUITO,
      plano: 'PREMIUM',
      premiumAte: '2026-11-08T00:00:00Z',
      origem: 'WEB',
      reembolsoAte: '2026-10-15T00:00:00Z',
    };
    http.expectOne('/api/billing/plan').flush(premium);
    await tela.whenStable();
    tela.detectChanges();

    const botao = [...(tela.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Cancelar e pedir reembolso'),
    )!;
    botao.click();
    http.expectOne('/api/billing/refund').flush(null);
    await vi.waitFor(() => http.expectOne('/api/billing/plan').flush({ ...premium, plano: 'GRATUITO', reembolsoAte: null }));
    await tela.whenStable();
    tela.detectChanges();

    expect(auth.renovacoes()).toBe(1);
    expect(tela.nativeElement.textContent).toContain('reembolso aparece');
  });
});
