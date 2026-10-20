import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Profile } from './profile';

/** Preferencias de moeda: gravadas no backend e refletidas na sessao na hora. */
describe('Profile', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrirComMoedas() {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([
      { codigo: 'BRL', nome: 'Real brasileiro' },
      { codigo: 'EUR', nome: 'Euro' },
      { codigo: 'USD', nome: 'Dolar americano' },
    ]);
    tela.detectChanges();
    await tela.whenStable();
    return tela;
  }

  function escolher(pagina: HTMLElement, id: string, valor: string): void {
    const campo = pagina.querySelector<HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
  }

  it('trocar a moeda do app ja salva, sem botao, e nao mexe no par da cotacao', async () => {
    const tela = await abrirComMoedas();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).not.toContain('Salvar preferências');

    escolher(pagina, 'moeda', 'USD');
    tela.detectChanges();

    const pedido = http.expectOne('/api/auth/me/preferences');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({
      moeda: 'USD',
      moedaCotacao: 'EUR',
      moedaConversao: 'BRL',
      mostrarCotacao: true,
    });
    pedido.flush(
      usuarioDeTeste({
        preferencias: {
          moeda: 'USD',
          moedaCotacao: 'EUR',
          moedaConversao: 'BRL',
          mostrarCotacao: true,
        },
      }),
    );
    tela.detectChanges();

    expect(auth.usuario()?.preferencias.moeda).toBe('USD');
    expect(auth.usuario()?.preferencias.moedaCotacao).toBe('EUR');
  });

  it('a chave da cotacao salva no toque e mostra ou esconde a escolha do par', async () => {
    const tela = await abrirComMoedas();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.querySelector('#moedaCotacao')).not.toBeNull();

    const chave = [...pagina.querySelectorAll('label.caixa')]
      .find((l) => l.textContent?.includes('Mostrar a cotação'))!
      .querySelector('input')!;
    chave.click();
    tela.detectChanges();
    await tela.whenStable();

    const pedido = http.expectOne('/api/auth/me/preferences');
    expect(pedido.request.body.mostrarCotacao).toBe(false);
    pedido.flush(
      usuarioDeTeste({
        preferencias: { moeda: 'EUR', moedaConversao: 'BRL', mostrarCotacao: false },
      }),
    );
    tela.detectChanges();

    expect(auth.usuario()?.preferencias.mostrarCotacao).toBe(false);
    expect(pagina.querySelector('#moedaCotacao')).toBeNull();
    expect(pagina.querySelector('#moedaConversao')).toBeNull();
  });

  it('erro do backend aparece para a pessoa e o campo volta ao que esta gravado', async () => {
    const tela = await abrirComMoedas();
    const pagina: HTMLElement = tela.nativeElement;

    escolher(pagina, 'moedaConversao', 'USD');
    tela.detectChanges();
    http
      .expectOne('/api/auth/me/preferences')
      .flush(
        { erro: 'Moeda desconhecida: XYZ.', codigo: 'BusinessException', campos: [], momento: '' },
        { status: 422, statusText: 'Unprocessable' },
      );
    tela.detectChanges();
    await tela.whenStable();

    expect(pagina.textContent).toContain('Moeda desconhecida');
    expect(auth.usuario()?.preferencias.moedaConversao).toBe('BRL');
    expect(pagina.querySelector<HTMLSelectElement>('#moedaConversao')!.value).toBe('BRL');
  });

  describe('Organizar o Painel', () => {
    afterEach(() => localStorage.clear());

    function linhas(pagina: HTMLElement): HTMLElement[] {
      return [...pagina.querySelectorAll<HTMLElement>('.ordem-do-painel li')];
    }

    function ordem(pagina: HTMLElement): (string | undefined)[] {
      return linhas(pagina).map((li) => li.dataset['bloco']);
    }

    /** O jsdom nao desenha: cada linha ganha 40px de altura, uma embaixo da outra. */
    function desenharLinhas(pagina: HTMLElement): void {
      linhas(pagina).forEach((li) => {
        li.getBoundingClientRect = () => {
          const top = linhas(pagina).indexOf(li) * 40;
          return { top, height: 40, bottom: top + 40 } as DOMRect;
        };
      });
    }

    function ponteiro(tipo: string, clientY: number, pointerType: string): Event {
      const evento = new MouseEvent(tipo, { bubbles: true, cancelable: true, clientY, button: 0 });
      Object.defineProperty(evento, 'pointerType', { value: pointerType });
      return evento;
    }

    function abrir() {
      const tela = TestBed.createComponent(Profile);
      tela.detectChanges();
      http.expectOne('/api/exchange-rate/currencies').flush([]);
      tela.detectChanges();
      return tela;
    }

    it('nao tem mais as setas de subir e descer', () => {
      const pagina: HTMLElement = abrir().nativeElement;
      expect(pagina.querySelector('[aria-label^="Subir"]')).toBeNull();
      expect(pagina.querySelector('[aria-label^="Descer"]')).toBeNull();
      expect(linhas(pagina)[0].querySelector('.alca')).not.toBeNull();
    });

    it('arrastar com o dedo pelo pontilhado leva o bloco para onde soltar', () => {
      const tela = abrir();
      const pagina: HTMLElement = tela.nativeElement;
      desenharLinhas(pagina);
      const alca = linhas(pagina)[0].querySelector('.alca')!;

      alca.dispatchEvent(ponteiro('pointerdown', 20, 'touch'));
      tela.detectChanges();
      expect(linhas(pagina)[0].classList).toContain('arrastando');
      // Passa do meio da terceira linha (100px).
      window.dispatchEvent(ponteiro('pointermove', 105, 'touch'));
      window.dispatchEvent(ponteiro('pointerup', 105, 'touch'));
      tela.detectChanges();

      expect(ordem(pagina).slice(0, 3)).toEqual(['avisos', 'mes', 'resumo']);
      expect(pagina.querySelector('.arrastando')).toBeNull();
    });

    it('o dedo fora do pontilhado nao arrasta, para a tela poder rolar', () => {
      const tela = abrir();
      const pagina: HTMLElement = tela.nativeElement;
      desenharLinhas(pagina);
      const antes = ordem(pagina);

      linhas(pagina)[0]
        .querySelector('.nome-do-bloco')!
        .dispatchEvent(ponteiro('pointerdown', 20, 'touch'));
      window.dispatchEvent(ponteiro('pointermove', 105, 'touch'));
      window.dispatchEvent(ponteiro('pointerup', 105, 'touch'));
      tela.detectChanges();

      expect(ordem(pagina)).toEqual(antes);
    });

    it('com o mouse a linha toda arrasta, para cima tambem', () => {
      const tela = abrir();
      const pagina: HTMLElement = tela.nativeElement;
      desenharLinhas(pagina);

      linhas(pagina)[3]
        .querySelector('.nome-do-bloco')!
        .dispatchEvent(ponteiro('pointerdown', 140, 'mouse'));
      window.dispatchEvent(ponteiro('pointermove', 10, 'mouse'));
      window.dispatchEvent(ponteiro('pointerup', 10, 'mouse'));
      tela.detectChanges();

      expect(ordem(pagina)[0]).toBe('cofrinho');
    });

    it('no teclado, as setas na alca mudam a ordem', () => {
      const tela = abrir();
      const pagina: HTMLElement = tela.nativeElement;
      linhas(pagina)[1]
        .querySelector('.alca')!
        .dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true }));
      tela.detectChanges();

      expect(ordem(pagina).slice(0, 2)).toEqual(['avisos', 'resumo']);
    });
  });

  it('avisos: liga o e-mail e grava; no navegador nao oferece a notificacao do iPhone', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).not.toContain('Notificação neste iPhone');

    const caixa = [...pagina.querySelectorAll('label.caixa')].find((l) =>
      l.textContent?.includes('Por e-mail'),
    )!;
    expect(caixa.textContent).toContain('titular@piggu.test');
    // A chave salva no toque, sem botao.
    expect(pagina.textContent).not.toContain('Salvar avisos');
    caixa.querySelector('input')!.click();
    tela.detectChanges();
    await tela.whenStable();

    const pedido = http.expectOne('/api/auth/me/alerts');
    expect(pedido.request.body).toEqual({ email: true, push: false });
    pedido.flush(usuarioDeTeste({ avisos: { email: true, push: false } }));
    tela.detectChanges();
    expect(auth.usuario()?.avisos?.email).toBe(true);
    expect(pagina.textContent).toContain('Avisos salvos.');
  });

  it('cor do Piggu: escolher Menta pinta o app e fica guardada no aparelho', () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    const opcoes = [...pagina.querySelectorAll<HTMLButtonElement>('.temas [role="radio"]')];
    expect(opcoes.map((b) => b.querySelector('strong')?.textContent)).toEqual([
      'Chiclete',
      'Rosa Piggu',
      'Menta',
      'Azul',
      'Lilás',
      'Coral',
      'Mel',
      'Vinho',
      'Grafite',
      'Tangerina',
      'Elétrico',
      'Turquesa',
      'Cobalto',
    ]);
    expect(opcoes[0].getAttribute('aria-checked')).toBe('true');

    opcoes[2].click();
    tela.detectChanges();

    expect(opcoes[2].getAttribute('aria-checked')).toBe('true');
    expect(opcoes[0].getAttribute('aria-checked')).toBe('false');
    expect(document.documentElement.dataset['tema']).toBe('menta');
    expect(localStorage.getItem('piggu_tema')).toBe('menta');

    opcoes[0].click();
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(localStorage.getItem('piggu_tema')).toBe('chiclete');
    localStorage.removeItem('piggu_tema');
  });

  it('aparencia: escolher Escuro fixa o modo no aparelho e Automatico volta a seguir o sistema', () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    const modos = [...pagina.querySelectorAll<HTMLButtonElement>('.modos [role="radio"]')];
    expect(modos.map((b) => b.textContent?.trim())).toEqual(['Automático', 'Claro', 'Escuro']);
    expect(modos[0].getAttribute('aria-checked')).toBe('true');

    modos[2].click();
    tela.detectChanges();

    expect(modos[2].getAttribute('aria-checked')).toBe('true');
    expect(modos[2].classList).toContain('ativo');
    expect(document.documentElement.dataset['modo']).toBe('escuro');
    expect(localStorage.getItem('piggu_modo')).toBe('escuro');

    modos[0].click();
    expect(document.documentElement.dataset['modo']).toBeUndefined();
    expect(localStorage.getItem('piggu_modo')).toBe('automatico');
    localStorage.removeItem('piggu_modo');
  });

  it('cor do Premium: sem o plano, o titular vai para os planos e a cor nao muda', () => {
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Mais cores no Premium');
    const coral = [...pagina.querySelectorAll<HTMLButtonElement>('.temas [role="radio"]')].find(
      (b) => b.querySelector('strong')?.textContent === 'Coral',
    )!;
    expect(coral.classList).toContain('travado');

    coral.click();
    tela.detectChanges();

    expect(navegar).toHaveBeenCalledWith(['/plano']);
    expect(coral.getAttribute('aria-checked')).toBe('false');
    expect(localStorage.getItem('piggu_tema')).toBeNull();
  });

  it('cor do Premium: quem nao e titular recebe o recado em vez dos planos', () => {
    auth.atualizarUsuario(
      usuarioDeTeste({ role: 'PARCEIRO', plano: 'GRATUITO', premiumAte: null }),
    );
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();

    const pagina: HTMLElement = tela.nativeElement;
    [...pagina.querySelectorAll<HTMLButtonElement>('.temas [role="radio"]')]
      .find((b) => b.querySelector('strong')?.textContent === 'Vinho')!
      .click();
    tela.detectChanges();

    expect(pagina.textContent).toContain('Quem cuida da assinatura é o titular da família.');
  });

  describe('foto de perfil', () => {
    function abrir() {
      const tela = TestBed.createComponent(Profile);
      tela.detectChanges();
      http.expectOne('/api/exchange-rate/currencies').flush([]);
      tela.detectChanges();
      return tela;
    }

    function botao(pagina: HTMLElement, texto: string): HTMLButtonElement | undefined {
      return [...pagina.querySelectorAll<HTMLButtonElement>('.acoes-da-foto button')].find((b) =>
        b.textContent?.includes(texto),
      );
    }

    it('sem foto: mostra a inicial e oferece colocar uma, sem botao de remover', () => {
      const pagina: HTMLElement = abrir().nativeElement;

      expect(pagina.querySelector('.foto-do-perfil .avatar-inicial')?.textContent).toBe('T');
      expect(pagina.querySelector('.acoes-da-foto')?.textContent).toContain('Colocar foto');
      expect(botao(pagina, 'Remover foto')).toBeUndefined();
      expect(pagina.querySelectorAll('input[type="file"][accept="image/*"]').length).toBe(1);
    });

    it('foto do Google: remover apaga e a sessao fica com as iniciais', () => {
      auth.usuario.set(
        usuarioDeTeste({
          foto: 'https://lh3.googleusercontent.com/t',
          temFotoDoGoogle: true,
          fotoPropria: false,
        }),
      );
      const tela = abrir();
      const pagina: HTMLElement = tela.nativeElement;
      expect(pagina.querySelector<HTMLImageElement>('.foto-do-perfil img')?.src).toContain(
        'googleusercontent',
      );
      expect(botao(pagina, 'Usar foto do Google')).toBeUndefined();

      botao(pagina, 'Remover foto')!.click();
      const pedido = http.expectOne('/api/me/foto');
      expect(pedido.request.method).toBe('DELETE');
      pedido.flush(usuarioDeTeste({ foto: null, temFotoDoGoogle: true, fotoPropria: false }));
      tela.detectChanges();

      expect(auth.usuario()?.foto).toBeNull();
      expect(pagina.textContent).toContain('Foto removida.');
      expect(pagina.querySelector('.foto-do-perfil .avatar-inicial')).not.toBeNull();
      expect(botao(pagina, 'Usar foto do Google')).toBeDefined();
    });

    it('foto propria: oferece voltar para a do Google', () => {
      auth.usuario.set(
        usuarioDeTeste({
          foto: 'data:image/jpeg;base64,AAAA',
          temFotoDoGoogle: true,
          fotoPropria: true,
        }),
      );
      const tela = abrir();

      botao(tela.nativeElement, 'Usar foto do Google')!.click();
      const pedido = http.expectOne('/api/me/foto/google');
      expect(pedido.request.method).toBe('PUT');
      pedido.flush(
        usuarioDeTeste({
          foto: 'https://lh3.googleusercontent.com/t',
          temFotoDoGoogle: true,
          fotoPropria: false,
        }),
      );
      tela.detectChanges();

      expect(auth.usuario()?.foto).toContain('googleusercontent');
      expect(tela.nativeElement.textContent).toContain('Voltamos para a foto do Google.');
    });

    it('arquivo que nao e imagem: avisa e nao envia nada', async () => {
      const tela = abrir();
      const campo = tela.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
      Object.defineProperty(campo, 'files', {
        value: [new File(['oi'], 'nota.txt', { type: 'text/plain' })],
      });

      campo.dispatchEvent(new Event('change'));
      await tela.whenStable();
      tela.detectChanges();

      http.expectNone('/api/me/foto');
      expect(tela.nativeElement.textContent).toContain('Não consegui ler essa imagem.');
    });
  });

  it('conta do Google cria a senha pelo Perfil; depois disso pede a atual para trocar', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([]);
    tela.detectChanges();
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;
    const preencher = (id: string, valor: string) => {
      const campo = pagina.querySelector<HTMLInputElement>(`#${id}`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    };
    const salvar = async () => {
      [...pagina.querySelectorAll('button')]
        .find((b) => /Criar senha|Trocar senha/.test(b.textContent ?? ''))!
        .click();
      await new Promise((pronto) => setTimeout(pronto));
      tela.detectChanges();
    };

    expect(pagina.querySelector('#senha-atual')).toBeNull();
    preencher('nova-senha', 'cofrinho123');
    preencher('repita-senha', 'cofrinho999');
    await salvar();
    expect(pagina.textContent).toContain('nao sao iguais');
    expect(auth.senhasDefinidas).toEqual([]);

    preencher('repita-senha', 'cofrinho123');
    await salvar();
    expect(auth.senhasDefinidas).toEqual([['cofrinho123', undefined]]);
    expect(pagina.textContent).toContain('Senha criada.');
    expect(pagina.querySelector('#senha-atual')).not.toBeNull();
  });
});
