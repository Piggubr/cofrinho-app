import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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

  it('salva as moedas escolhidas e atualiza a sessao com a resposta', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([
      { codigo: 'BRL', nome: 'Real brasileiro' },
      { codigo: 'EUR', nome: 'Euro' },
      { codigo: 'USD', nome: 'Dolar americano' },
    ]);
    tela.detectChanges();
    await tela.whenStable();

    const pagina: HTMLElement = tela.nativeElement;
    const moeda = pagina.querySelector<HTMLSelectElement>('#moeda')!;
    moeda.value = 'USD';
    moeda.dispatchEvent(new Event('change'));
    const cotacao = pagina.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    cotacao.click();
    tela.detectChanges();

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Salvar preferências'))!
      .click();

    const pedido = http.expectOne('/api/auth/me/preferences');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({
      moeda: 'USD',
      moedaConversao: 'BRL',
      mostrarCotacao: false,
    });

    const salvo = usuarioDeTeste({
      preferencias: { moeda: 'USD', moedaConversao: 'BRL', mostrarCotacao: false },
    });
    pedido.flush(salvo);
    tela.detectChanges();

    expect(auth.usuario()?.preferencias.moeda).toBe('USD');
    expect(pagina.textContent).toContain('Preferências salvas.');
  });

  it('erro do backend aparece para a pessoa e nada muda na sessao', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([{ codigo: 'EUR', nome: 'Euro' }]);
    tela.detectChanges();

    [...tela.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Salvar preferências'))!
      .click();
    http
      .expectOne('/api/auth/me/preferences')
      .flush(
        { erro: 'Moeda desconhecida: XYZ.', codigo: 'BusinessException', campos: [], momento: '' },
        { status: 422, statusText: 'Unprocessable' },
      );
    tela.detectChanges();

    expect(tela.nativeElement.textContent).toContain('Moeda desconhecida');
    expect(auth.usuario()?.preferencias.moeda).toBe('EUR');
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
      'Rosa Piggu',
      'Menta',
      'Lilás',
      'Azul',
    ]);
    expect(opcoes[0].getAttribute('aria-checked')).toBe('true');

    opcoes[1].click();
    tela.detectChanges();

    expect(opcoes[1].getAttribute('aria-checked')).toBe('true');
    expect(opcoes[0].getAttribute('aria-checked')).toBe('false');
    expect(document.documentElement.dataset['tema']).toBe('menta');
    expect(localStorage.getItem('piggu_tema')).toBe('menta');

    opcoes[0].click();
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(localStorage.getItem('piggu_tema')).toBe('rosa');
    localStorage.removeItem('piggu_tema');
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
});
