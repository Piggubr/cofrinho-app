import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Aceite } from './aceite';

/** Termos novos: aceitar libera o app; quem nao concorda sai ou exclui a conta. */
describe('Aceite', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ termosAceitos: '2026-09' }));
    TestBed.configureTestingModule({
      imports: [Aceite],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir() {
    const tela = TestBed.createComponent(Aceite);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;
    const botao = (texto: string) =>
      [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!;
    return { tela, pagina, botao };
  }

  it('mostra o que mudou e, ao aceitar, grava a versao vigente e abre o painel', async () => {
    const { tela, pagina, botao } = abrir();
    const ir = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(pagina.querySelectorAll('.mudancas li').length).toBeGreaterThan(0);
    expect(pagina.querySelector('a[href="/termos"]')).not.toBeNull();

    botao('Aceitar e continuar').click();
    const pedido = http.expectOne('/api/me/termos');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ versao: VERSAO_DO_AVISO });
    pedido.flush(usuarioDeTeste({ termosAceitos: VERSAO_DO_AVISO }));
    await tela.whenStable();

    expect(auth.usuario()?.termosAceitos).toBe(VERSAO_DO_AVISO);
    expect(ir).toHaveBeenCalledWith('/painel');
  });

  it('se o servidor recusar, mostra o erro e deixa tentar de novo', async () => {
    const { tela, pagina, botao } = abrir();
    botao('Aceitar e continuar').click();
    http
      .expectOne('/api/me/termos')
      .flush({ erro: 'Os termos mudaram. Recarregue a pagina e leia de novo.' }, { status: 422, statusText: 'x' });
    await tela.whenStable();
    tela.detectChanges();

    expect(pagina.querySelector('[role="alert"]')?.textContent).toContain('Os termos mudaram');
    expect(botao('Aceitar e continuar').disabled).toBe(false);
  });

  it('"Sair" encerra a sessao', () => {
    const { botao } = abrir();
    const sair = vi.spyOn(auth, 'sair');
    botao('Sair').click();
    expect(sair).toHaveBeenCalled();
  });

  it('excluir a conta pede EXCLUIR; confirmado, apaga e encerra', () => {
    const { botao } = abrir();
    const perguntar = vi.spyOn(window, 'prompt').mockReturnValueOnce('nao').mockReturnValueOnce('excluir');

    botao('excluir minha conta').click();
    http.expectNone('/api/me');

    botao('excluir minha conta').click();
    const pedido = http.expectOne('/api/me');
    expect(pedido.request.method).toBe('DELETE');
    pedido.flush(null);

    expect(perguntar).toHaveBeenCalledTimes(2);
    expect(auth.usuario()).toBeNull();
    perguntar.mockRestore();
  });
});
