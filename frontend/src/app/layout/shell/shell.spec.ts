import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AbaNativa, AbasNativas } from '../../core/nativo/abas-nativas';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Shell } from './shell';

@Component({ template: '' })
class TelaVazia {}

/** A cotacao do topo segue as preferencias de moeda da pessoa. */
describe('Shell', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  const CHAVE_DO_TOUR = `piggu_tutorial_${usuarioDeTeste().id}`;

  beforeEach(() => {
    // O tour do primeiro acesso tem testes proprios no fim; nos outros ele ja foi visto.
    localStorage.setItem(CHAVE_DO_TOUR, '1');
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CHAVE_DO_TOUR);
  });

  function abrir() {
    http = TestBed.inject(HttpTestingController);
    const tela = TestBed.createComponent(Shell);
    tela.detectChanges();
    return tela;
  }

  it('consulta o par escolhido e mostra 1 da moeda mais forte na mais fraca', () => {
    auth.preferir({ moeda: 'USD', moedaConversao: 'BRL' });
    const tela = abrir();

    const pedido = http.expectOne((req) => req.url === '/api/exchange-rate');
    expect(pedido.request.params.get('de')).toBe('USD');
    expect(pedido.request.params.get('para')).toBe('BRL');
    pedido.flush({
      de: 'USD',
      para: 'BRL',
      taxa: 5.4,
      data: '',
      fonte: '',
      estimativa: true,
      desatualizada: false,
    });
    tela.detectChanges();

    const texto = tela.nativeElement.querySelector('.cotacao').textContent;
    expect(texto).toContain('1 USD =');
    expect(texto).toContain('R$');
    expect(texto).toContain('5,40');
  });

  it('segue a ordem escolhida, com 4 casas abaixo de 1: 1 BRL = US$ 0,1786', () => {
    auth.preferir({ moeda: 'BRL', moedaCotacao: 'BRL', moedaConversao: 'USD' });
    const tela = abrir();

    http.expectOne((req) => req.url === '/api/exchange-rate').flush({
      de: 'BRL',
      para: 'USD',
      taxa: 0.178563,
      data: '',
      fonte: '',
      estimativa: true,
      desatualizada: false,
    });
    tela.detectChanges();

    const texto: string = tela.nativeElement.querySelector('.cotacao').textContent;
    expect(texto).toContain('1 BRL =');
    expect(texto).toContain('US$');
    expect(texto).toContain('0,1786');
  });

  it('o par da cotacao nao depende da moeda do app', () => {
    auth.preferir({ moeda: 'JPY', moedaCotacao: 'EUR', moedaConversao: 'BRL' });
    abrir();

    const pedido = http.expectOne((req) => req.url === '/api/exchange-rate');
    expect(pedido.request.params.get('de')).toBe('EUR');
    expect(pedido.request.params.get('para')).toBe('BRL');
  });

  it('com a cotacao desligada nao pede nada e nao mostra nada', () => {
    auth.preferir({ mostrarCotacao: false });
    const tela = abrir();

    http.expectNone((req) => req.url === '/api/exchange-rate');
    expect(tela.nativeElement.querySelector('.cotacao')).toBeNull();
  });

  it('trocar a moeda no perfil refaz a consulta', () => {
    const tela = abrir();
    http
      .expectOne((req) => req.url === '/api/exchange-rate')
      .flush({
        de: 'EUR',
        para: 'BRL',
        taxa: 6.15,
        data: '',
        fonte: '',
        estimativa: true,
        desatualizada: false,
      });

    auth.preferir({ moedaConversao: 'USD' });
    tela.detectChanges();

    const novo = http.expectOne((req) => req.url === '/api/exchange-rate');
    expect(novo.request.params.get('para')).toBe('USD');
    novo.flush({
      de: 'EUR',
      para: 'USD',
      taxa: 1.1,
      data: '',
      fonte: '',
      estimativa: true,
      desatualizada: false,
    });
  });

  it('cinco abas embaixo, o "+" flutuando e o "Mais" abre o menu em grupos', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;

    const abas = [...pagina.querySelectorAll('nav.abas .aba')].map((a) => a.textContent?.trim());
    expect(abas).toEqual(['Painel', 'Gastos', 'Receitas', 'Relatórios', 'Mais']);
    expect(pagina.querySelector('button.lancar-flutuante')).not.toBeNull();
    expect(pagina.querySelector('nav.menu')!.classList).not.toContain('aberto');

    (pagina.querySelector('nav.abas button') as HTMLButtonElement).click();
    tela.detectChanges();
    expect(pagina.querySelector('nav.menu')!.classList).toContain('aberto');
    expect(pagina.querySelectorAll('nav.menu .menu-itens a').length).toBe(18);
    expect([...pagina.querySelectorAll('.menu-grupo-titulo')].map((t) => t.textContent?.trim())).toEqual([
      'Dia a dia',
      'Planejar',
      'Cartões e datas',
      'Estilo de vida',
      'Família e plano',
    ]);
  });

  it('no "Mais" do celular, as telas que ja tem aba ficam de fora e o resto segue a ordem de uso', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const itens = [...pagina.querySelectorAll<HTMLElement>('nav.menu .menu-itens a')];

    const naBarra = itens.filter((a) => a.classList.contains('na-barra')).map((a) => a.textContent?.trim());
    expect(naBarra).toEqual(['Painel', 'Gastos', 'Receitas', 'Relatórios']);

    const noMais = itens
      .filter((a) => !a.classList.contains('na-barra'))
      .sort((a, b) => +a.style.getPropertyValue('--ordem-no-mais') - +b.style.getPropertyValue('--ordem-no-mais'))
      .map((a) => a.textContent?.trim());
    expect(noMais).toEqual([
      'Planejar',
      'Contas do mês',
      'Orçamento',
      'Minhas metas',
      'Cartões e bancos',
      'Calendário',
      'Compras',
      'Girls Math',
      'Lugares',
      'Filmes',
      'Fotos',
      'Prêmios',
      'Família',
      'Premium',
    ]);
  });

  it('todas as telas de estilo de vida aparecem, mesmo com modulos desligados numa conta antiga', () => {
    const usuario = usuarioDeTeste();
    auth.usuario.set({ ...usuario, preferencias: { ...usuario.preferencias, modulos: [] } });
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const itens = [...pagina.querySelectorAll('nav.menu .menu-itens a')].map((a) => a.textContent?.trim());

    for (const nome of ['Compras', 'Lugares', 'Filmes', 'Fotos', 'Prêmios', 'Girls Math']) {
      expect(itens).toContain(nome);
    }
  });

  it('tela de dentro mostra o voltar e o nome; a nova entra animada e o voltar retorna', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([
          { path: 'painel', component: TelaVazia },
          { path: 'perfil', component: TelaVazia },
          { path: 'calendario', component: TelaVazia },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/painel');
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const conteudo = pagina.querySelector('main.conteudo') as HTMLElement;
    const animacoes: Keyframe[][] = [];
    conteudo.animate = ((quadros: Keyframe[]) => {
      animacoes.push(quadros);
      return {} as Animation;
    }) as typeof conteudo.animate;

    expect(pagina.querySelector('.titulo-da-tela')).toBeNull();

    // Sem troca nenhuma dentro do app, o voltar leva ao Painel.
    await router.navigateByUrl('/perfil');
    tela.detectChanges();
    expect(pagina.querySelector('.titulo-da-tela')?.textContent?.trim()).toBe('Perfil');
    expect(pagina.querySelector('.usuario-link')!.classList).toContain('ativo');
    expect(animacoes.at(-1)![0]['transform']).toBe('translateX(36px)');

    await router.navigateByUrl('/calendario');
    tela.detectChanges();
    expect(pagina.querySelector('.titulo-da-tela')?.textContent?.trim()).toBe('Calendário');

    await router.navigateByUrl('/painel');
    tela.detectChanges();
    expect(pagina.querySelector('.titulo-da-tela')).toBeNull();
    expect(animacoes.at(-1)![0]['transform']).toBe('translateX(-36px)');
    expect(animacoes.length).toBe(3);
  });

  it('o voltar do cabecalho vai ao Painel quando a pessoa abriu direto na tela', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([
          { path: 'painel', component: TelaVazia },
          { path: 'perfil', component: TelaVazia },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/perfil');
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;

    (pagina.querySelector('button.voltar') as HTMLButtonElement).click();
    await tela.whenStable();
    expect(router.url).toBe('/painel');
  });

  it('o membro tambem tem o "+" de lancar', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    expect((tela.nativeElement as HTMLElement).querySelector('.lancar-flutuante')).not.toBeNull();
  });

  it('o "+" vira um balao com gasto, receita e foto; o gasto abre o painel', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const mais = pagina.querySelector('button.lancar-flutuante') as HTMLButtonElement;

    mais.click();
    tela.detectChanges();
    const opcoes = [...pagina.querySelectorAll('.opcoes-do-lancar .opcao')].map((o) =>
      o.textContent?.trim(),
    );
    expect(opcoes).toEqual(['Lançar gasto', 'Lançar receita', 'Foto do recibo']);
    expect(pagina.querySelector('app-lancamento-rapido')).toBeNull();
    expect(mais.getAttribute('aria-expanded')).toBe('true');

    (pagina.querySelector('.opcoes-do-lancar .opcao') as HTMLButtonElement).click();
    tela.detectChanges();
    http.match(() => true).forEach((pedido) => pedido.flush(null));

    expect(pagina.querySelector('.opcoes-do-lancar')).toBeNull();
    expect(pagina.querySelector('app-lancamento-rapido')).not.toBeNull();

    // O mesmo botao, agora um "x", fecha o painel.
    mais.click();
    tela.detectChanges();
    expect(pagina.querySelector('app-lancamento-rapido')).toBeNull();
    expect(mais.getAttribute('aria-expanded')).toBe('false');
  });

  it('o "+" se recolhe ao rolar para baixo, fica com o balao aberto e volta no topo', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const mais = pagina.querySelector('button.lancar-flutuante') as HTMLButtonElement;
    const rolar = (y: number) => {
      Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
      window.dispatchEvent(new Event('scroll'));
      tela.detectChanges();
    };

    try {
      rolar(40);
      expect(mais.classList).not.toContain('recolhido');
      rolar(300);
      expect(mais.classList).toContain('recolhido');

      // A pontinha traz o botao de volta e abre o balao; rolar com ele aberto nao recolhe.
      mais.click();
      tela.detectChanges();
      expect(mais.classList).not.toContain('recolhido');
      rolar(600);
      expect(mais.classList).not.toContain('recolhido');

      mais.click();
      rolar(900);
      expect(mais.classList).toContain('recolhido');
      rolar(0);
      expect(mais.classList).not.toContain('recolhido');
    } finally {
      Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });
    }
  });

  it('a folha do "Mais" no celular: puxar para cima volta ao lugar, para baixo fecha', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    try {
      const tela = abrir();
      http.match(() => true).forEach((pedido) => pedido.flush(null));
      const pagina: HTMLElement = tela.nativeElement;
      const menu = pagina.querySelector('nav.menu') as HTMLElement;
      const topo = pagina.querySelector('.menu-topo') as HTMLElement;
      const dedo = (tipo: string, y: number, alvo: Element = topo) => {
        const evento = new MouseEvent(tipo, { clientY: y, bubbles: true });
        alvo.dispatchEvent(evento);
        tela.detectChanges();
      };

      // Fechada, a alca nao arrasta nada.
      dedo('pointerdown', 100);
      dedo('pointermove', 200);
      expect(menu.style.transform).toBe('');

      (pagina.querySelector('.aba[data-tour="mais"]') as HTMLButtonElement).click();
      tela.detectChanges();
      expect(menu.classList).toContain('aberto');

      // Para cima: cede so um pouco e volta ao soltar.
      dedo('pointerdown', 300);
      dedo('pointermove', 100);
      const subiu = Number(/-?[\d.]+/.exec(menu.style.transform)![0]);
      expect(subiu).toBeLessThan(0);
      expect(subiu).toBeGreaterThan(-24);
      expect((pagina.querySelector('.menu-fundo') as HTMLElement).style.opacity).toBe('');
      dedo('pointerup', 100);
      expect(menu.classList).toContain('aberto');
      expect(menu.style.transform).toBe('');

      // Para baixo: o fundo clareia e, passado um terco, fecha.
      dedo('pointerdown', 100);
      dedo('pointermove', 300);
      expect(Number((pagina.querySelector('.menu-fundo') as HTMLElement).style.opacity)).toBeLessThan(1);
      dedo('pointerup', 600);
      expect(menu.classList).not.toContain('aberto');
      // Soltar sem ter comecado nao faz nada.
      dedo('pointerup', 600);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('tocar fora do balao ou apertar Esc fecha as opcoes', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    const mais = pagina.querySelector('button.lancar-flutuante') as HTMLButtonElement;

    mais.click();
    tela.detectChanges();
    (pagina.querySelector('.opcoes-fundo') as HTMLButtonElement).click();
    tela.detectChanges();
    expect(pagina.querySelector('.opcoes-do-lancar')).toBeNull();

    mais.click();
    tela.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    tela.detectChanges();
    expect(pagina.querySelector('.opcoes-do-lancar')).toBeNull();
  });

  it('"Foto do recibo" abre o painel ja lendo a foto escolhida', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;
    (pagina.querySelector('button.lancar-flutuante') as HTMLButtonElement).click();
    tela.detectChanges();

    const campo = pagina.querySelector('.opcoes-do-lancar input[type=file]') as HTMLInputElement;
    const foto = new File(['x'], 'nota.jpg', { type: 'image/jpeg' });
    Object.defineProperty(campo, 'files', { value: [foto] });
    campo.dispatchEvent(new Event('change'));
    tela.detectChanges();

    expect(pagina.querySelector('.opcoes-do-lancar')).toBeNull();
    expect(pagina.querySelector('app-lancamento-rapido')).not.toBeNull();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
  });

  it('o parceiro ve as telas de dinheiro, mas nao o Premium, que e do titular', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'PARCEIRO' }));
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const itens = [...(tela.nativeElement as HTMLElement).querySelectorAll('nav.menu .menu-itens a')].map((a) =>
      a.textContent?.trim(),
    );

    expect(itens).toContain('Gastos');
    expect(itens).toContain('Receitas');
    expect(itens).not.toContain('Premium');
    expect(itens.length).toBe(17);
  });

  it('no app iOS com o plugin, troca a barra HTML pelas abas nativas', () => {
    let tocar: (id: string) => void = () => {};
    const configuradas: AbaNativa[][] = [];
    const selecionadas: (string | null)[] = [];
    TestBed.overrideProvider(AbasNativas, {
      useValue: {
        disponivel: true,
        configurar: (abas: AbaNativa[]) => configuradas.push(abas),
        selecionar: (id: string | null) => selecionadas.push(id),
        mostrarSoEmTelaEstreita: () => () => {},
        ouvir: (aoTocar: (id: string) => void) => {
          tocar = aoTocar;
          return () => {};
        },
      },
    });
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.classList).toContain('abas-nativas');
    expect(configuradas.at(-1)!.map((aba) => aba.id)).toEqual([
      '/painel',
      '/gastos',
      '/receitas',
      '/relatorios',
      'mais',
    ]);

    tocar('mais');
    tela.detectChanges();
    expect(pagina.querySelector('nav.menu')!.classList).toContain('aberto');
    expect(selecionadas.at(-1)).toBe('mais');
  });

  describe('tour do primeiro acesso', () => {
    beforeEach(() => localStorage.removeItem(CHAVE_DO_TOUR));

    function primeiroAcesso() {
      const tela = abrir();
      http.match(() => true).forEach((pedido) => pedido.flush(null));
      return { tela, pagina: tela.nativeElement as HTMLElement };
    }

    const titulo = (pagina: HTMLElement) => pagina.querySelector('#titulo-tour')?.textContent?.trim();
    const botao = (pagina: HTMLElement, texto: string) =>
      [...pagina.querySelectorAll<HTMLButtonElement>('.balao button')].find(
        (b) => b.textContent?.trim() === texto,
      )!;

    it('abre na primeira vez, destaca o "+" e nao volta depois de pular', () => {
      const { tela, pagina } = primeiroAcesso();
      expect(titulo(pagina)).toBe('Bem-vindo ao Piggu');

      botao(pagina, 'Próximo').click();
      tela.detectChanges();
      expect(titulo(pagina)).toBe('Lance pelo +');
      expect(pagina.querySelector('.lancar-flutuante')!.classList).toContain('destaque');

      botao(pagina, 'Pular').click();
      tela.detectChanges();
      expect(pagina.querySelector('app-tutorial')).toBeNull();
      expect(localStorage.getItem(CHAVE_DO_TOUR)).toBe('1');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [Shell],
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
          { provide: AuthService, useValue: auth },
        ],
      });
      const { pagina: deNovo } = primeiroAcesso();
      expect(deNovo.querySelector('app-tutorial')).toBeNull();
    });

    it('no fim, "Lançar meu primeiro gasto" fecha o tour e abre o painel do "+"', () => {
      const { tela, pagina } = primeiroAcesso();
      for (let i = 0; i < 7; i++) {
        botao(pagina, 'Próximo').click();
        tela.detectChanges();
      }
      expect(titulo(pagina)).toBe('Assistente');

      botao(pagina, 'Lançar meu primeiro gasto').click();
      tela.detectChanges();
      http.match(() => true).forEach((pedido) => pedido.flush(null));

      expect(pagina.querySelector('app-tutorial')).toBeNull();
      expect(pagina.querySelector('app-lancamento-rapido')).not.toBeNull();
    });

    it('o membro ve o mesmo tour, com o "+"', () => {
      auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
      const { tela, pagina } = primeiroAcesso();
      expect(pagina.querySelector('.balao .contagem')?.textContent?.trim()).toBe('1/8');

      botao(pagina, 'Próximo').click();
      tela.detectChanges();
      expect(titulo(pagina)).toBe('Lance pelo +');
    });
  });
});
