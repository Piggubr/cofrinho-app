import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { InfoDoPlano } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Plan, ehDaStripe } from './plan';

const GRATUITO: InfoDoPlano = {
  plano: 'GRATUITO',
  premiumAte: null,
  origem: null,
  reembolsoAte: null,
  diasDeTeste: 14,
  assinaturaDisponivel: true,
  site: { mensal: '19,90', anual: '199,00' },
  app: { mensal: '22,89', anual: '228,85' },
  gratuito: ['Gastos'],
  premium: ['Open Finance'],
  planoFamilia: false,
  voceAssinou: false,
  familiaDisponivel: true,
  siteFamilia: { mensal: '39,99', anual: '399,90' },
  appFamilia: { mensal: '45,99', anual: '459,89' },
  pessoasNoFamilia: 5,
  casaTemAssinatura: false,
};

/** Assinar leva para a pagina do provedor; voltar de la renova o token antes de ler o plano. */
describe('Plan', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  function montar(assinatura: string | null = null, promo: string | null = null) {
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
            snapshot: {
              queryParamMap: convertToParamMap({
                ...(assinatura ? { assinatura } : {}),
                ...(promo ? { promo } : {}),
              }),
            },
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
    expect(pagina.textContent).toContain('14 dias grátis');
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

  it('plano familia: mostra o preco e assina o periodo do familia', async () => {
    const tela = montar();
    vi.spyOn(
      tela.componentInstance as unknown as { irPara: (url: string) => void },
      'irPara',
    ).mockImplementation(() => undefined);
    tela.detectChanges();
    http.expectOne('/api/billing/plan').flush(GRATUITO);
    await tela.whenStable();
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('R$ 39,99');
    expect(pagina.textContent).toContain('até 5 pessoas');
    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Assinar família anual'))!
      .click();
    const pedido = http.expectOne('/api/billing/checkout');
    expect(pedido.request.body).toEqual({ periodo: 'FAMILIA_ANUAL' });
    pedido.flush({ url: 'https://checkout.stripe.test/s' });
    await tela.whenStable();
  });

  it('com o individual de outra pessoa da casa explica e nao oferece outra assinatura', async () => {
    const tela = montar();
    tela.detectChanges();
    http.expectOne('/api/billing/plan').flush({ ...GRATUITO, casaTemAssinatura: true });
    await tela.whenStable();
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('vale só para essa pessoa');
    expect(
      [...pagina.querySelectorAll('button')].some((b) => b.textContent?.includes('Assinar')),
    ).toBe(false);
  });

  async function abrir(info: InfoDoPlano): Promise<HTMLElement> {
    const tela = montar();
    tela.detectChanges();
    http.expectOne('/api/billing/plan').flush(info);
    await tela.whenStable();
    tela.detectChanges();
    return tela.nativeElement;
  }

  function cartaoAtual(pagina: HTMLElement): string {
    const atuais = pagina.querySelectorAll('.cartao.atual');
    expect(atuais).toHaveLength(1);
    return atuais[0].querySelector('h2')!.textContent!.trim();
  }

  it('marca o gratuito como o plano de quem nao assinou', async () => {
    const pagina = await abrir(GRATUITO);

    expect(cartaoAtual(pagina)).toBe('Gratuito');
    expect(pagina.querySelector('.cartao.atual .seu-plano')?.textContent).toContain('Seu plano');
  });

  it('quem esta no familia ve o nome do plano em cima e o cartao do familia marcado', async () => {
    const pagina = await abrir({
      ...GRATUITO,
      plano: 'PREMIUM',
      premiumAte: '2026-11-08T00:00:00Z',
      origem: 'WEB',
      planoFamilia: true,
      voceAssinou: false,
      casaTemAssinatura: true,
    });

    expect(pagina.textContent).toContain('Você tem o Premium família');
    expect(cartaoAtual(pagina)).toBe('Premium família');
  });

  it('casal e familia sem preco na Stripe aparecem como em breve, sem botao de assinar', async () => {
    const pagina = await abrir({
      ...GRATUITO,
      familiaDisponivel: false,
      casalDisponivel: false,
      siteCasal: { mensal: '29,90', anual: '299,00' },
    });

    const titulos = [...pagina.querySelectorAll('.planos h2')].map((h) => h.textContent!.trim());
    expect(titulos).toEqual(['Gratuito', 'Premium', 'Premium casal', 'Premium família']);
    expect(pagina.textContent?.match(/Em breve\./g)).toHaveLength(2);
    const botoes = [...pagina.querySelectorAll('button')].map((b) => b.textContent!.trim());
    expect(botoes.some((b) => b.includes('casal') || b.includes('família'))).toBe(false);
    expect(botoes).toContain('Assinar mensal');
  });

  it('ao voltar do pagamento renova o token antes de consultar o plano', async () => {
    const tela = montar('ok');
    tela.detectChanges();
    await tela.whenStable();
    http.expectOne('/api/billing/plan').flush(GRATUITO);

    expect(auth.renovacoes()).toBe(1);
  });

  it('link de campanha: mostra os dias da campanha e leva o codigo ao pagamento', async () => {
    const tela = montar(null, 'VOLTA30');
    tela.detectChanges();
    http.expectOne('/api/billing/plan?campanha=VOLTA30').flush({ ...GRATUITO, diasDeTeste: 30 });
    await tela.whenStable();
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('30 dias grátis');

    const assinar = [...(tela.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Assinar mensal'),
    )!;
    vi.spyOn(
      tela.componentInstance as unknown as { irPara: (url: string) => void },
      'irPara',
    ).mockImplementation(() => undefined);
    assinar.click();
    const pedido = http.expectOne('/api/billing/checkout');
    expect(pedido.request.body).toEqual({ periodo: 'MENSAL', campanha: 'VOLTA30' });
    pedido.flush({ url: 'https://checkout.stripe.com/c' });
  });

  it.each([
    [true, 'reembolso aparece'],
    [false, 'nada foi cobrado'],
  ])(
    'nos 7 dias do arrependimento mostra o reembolso e, confirmado, cancela (reembolsado: %s)',
    async (reembolsado, mensagem) => {
      const tela = montar();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      tela.detectChanges();
      const premium: InfoDoPlano = {
        ...GRATUITO,
        plano: 'PREMIUM',
        premiumAte: '2026-11-08T00:00:00Z',
        origem: 'WEB',
        reembolsoAte: '2026-10-15T00:00:00Z',
        voceAssinou: true,
        casaTemAssinatura: true,
      };
      http.expectOne('/api/billing/plan').flush(premium);
      await tela.whenStable();
      tela.detectChanges();

      const botao = [...(tela.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
        b.textContent?.includes('Cancelar e pedir reembolso'),
      )!;
      botao.click();
      http.expectOne('/api/billing/refund').flush({ reembolsado });
      await vi.waitFor(() =>
        http
          .expectOne('/api/billing/plan')
          .flush({ ...premium, plano: 'GRATUITO', reembolsoAte: null }),
      );
      await tela.whenStable();
      tela.detectChanges();

      expect(auth.renovacoes()).toBe(1);
      expect(tela.nativeElement.textContent).toContain(mensagem);
    },
  );
});

describe('ehDaStripe', () => {
  it('so aceita https na stripe.com', () => {
    expect(ehDaStripe('https://checkout.stripe.com/c/pay/cs_1')).toBe(true);
    expect(ehDaStripe('https://billing.stripe.com/p/session/x')).toBe(true);
    expect(ehDaStripe('http://checkout.stripe.com/c')).toBe(false);
    expect(ehDaStripe('https://stripe.com.golpe.io/c')).toBe(false);
    expect(ehDaStripe('javascript:alert(1)')).toBe(false);
  });
});
