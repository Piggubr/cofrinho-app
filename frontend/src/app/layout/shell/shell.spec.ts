import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AbaNativa, AbasNativas } from '../../core/nativo/abas-nativas';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Shell } from './shell';

/** A cotacao do topo segue as preferencias de moeda da pessoa. */
describe('Shell', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
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

  afterEach(() => http.verify());

  function abrir() {
    http = TestBed.inject(HttpTestingController);
    const tela = TestBed.createComponent(Shell);
    tela.detectChanges();
    return tela;
  }

  it('consulta o par escolhido e mostra a taxa na moeda de conversao', () => {
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
    expect(texto).toContain('USD →');
    expect(texto).toContain('R$');
    expect(texto).toContain('5,40');
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

  it('a barra de baixo tem os atalhos e o "Mais" abre o menu com todas as telas', () => {
    const tela = abrir();
    http.match(() => true).forEach((pedido) => pedido.flush(null));
    const pagina: HTMLElement = tela.nativeElement;

    const abas = [...pagina.querySelectorAll('nav.abas .aba')].map((a) => a.textContent?.trim());
    expect(abas).toEqual(['Painel', 'Gastos', '', 'Relatórios', 'Mais']);
    expect(pagina.querySelector('nav.menu')!.classList).not.toContain('aberto');

    (pagina.querySelector('nav.abas button') as HTMLButtonElement).click();
    tela.detectChanges();
    expect(pagina.querySelector('nav.menu')!.classList).toContain('aberto');
    expect(pagina.querySelectorAll('nav.menu .menu-itens a').length).toBe(16);
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
    expect(itens.length).toBe(15);
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
      'lancar',
      '/relatorios',
      'mais',
    ]);

    tocar('mais');
    tela.detectChanges();
    expect(pagina.querySelector('nav.menu')!.classList).toContain('aberto');
    expect(selecionadas.at(-1)).toBe('mais');
  });
});
