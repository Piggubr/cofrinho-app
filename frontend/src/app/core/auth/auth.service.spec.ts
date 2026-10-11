import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ParDeTokens, Usuario } from '../api/models';
import { APP_CONFIG } from '../config/app-config';
import { usuarioDeTeste } from '../../testing/auth-falso';
import { AuthService } from './auth.service';
import { GoogleIdentityService } from './google-identity.service';
import { TokenStorage } from './token-storage';

function tokens(usuario: Usuario = usuarioDeTeste()): ParDeTokens {
  return { accessToken: 'acesso', usuario } as ParDeTokens;
}

/** Entrada, renovacao, perfis e saida, sem Google de verdade. */
describe('AuthService', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let storage: TokenStorage;
  let contasEsquecidas: number;

  beforeEach(() => {
    contasEsquecidas = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        {
          provide: GoogleIdentityService,
          useValue: { esquecerConta: async () => void contasEsquecidas++ },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    storage = TestBed.inject(TokenStorage);
  });

  afterEach(() => http.verify());

  it('entrar com o Google guarda o acesso, o usuario e manda o aceite dos termos', async () => {
    const entrada = auth.entrarComGoogle('id-do-google', true, '2026-09');
    const pedido = http.expectOne('/api/auth/google');
    expect(pedido.request.body).toEqual({
      idToken: 'id-do-google',
      versaoDosTermos: '2026-09',
      lembrar: true,
    });
    expect(pedido.request.withCredentials).toBe(true);
    pedido.flush(tokens());
    await entrada;

    expect(auth.autenticado()).toBe(true);
    expect(storage.accessToken).toBe('acesso');
    expect(auth.ehTitular()).toBe(true);
    expect(auth.podeLancar()).toBe(true);
  });

  it('cada papel ve o que lhe cabe', () => {
    const casos: [Usuario['role'], boolean, boolean, boolean][] = [
      ['ADMIN', true, true, false],
      ['TITULAR', true, true, false],
      ['PARCEIRO', false, true, false],
      ['MEMBRO', false, false, true],
    ];
    for (const [role, titular, lanca, membro] of casos) {
      auth.atualizarUsuario(usuarioDeTeste({ role }));
      expect([auth.ehTitular(), auth.podeLancar(), auth.ehMembro()]).toEqual([
        titular,
        lanca,
        membro,
      ]);
      expect(auth.temPerfil(role)).toBe(true);
    }
  });

  it('Premium vem do plano, e o ADMIN sempre tem', () => {
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO' }));
    expect(auth.ehPremium()).toBe(false);
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', role: 'ADMIN' }));
    expect(auth.ehPremium()).toBe(true);
  });

  it('sem usuario, nenhum perfil vale', () => {
    expect(auth.temPerfil('TITULAR', 'MEMBRO')).toBe(false);
    expect(auth.autenticado()).toBe(false);
  });

  it('restaurar sessao renova pelo cookie', async () => {
    const restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').flush(tokens());

    expect(await restaurada).toBe(true);
    expect(auth.usuario()?.email).toBe('titular@piggu.test');
  });

  it('cookie vencido: nao restaura e limpa o acesso guardado', async () => {
    storage.guardar(tokens());
    const restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await restaurada).toBe(false);
    expect(storage.accessToken).toBeNull();
  });

  it('varias renovacoes ao mesmo tempo viram um so pedido', () => {
    const recebidos: string[] = [];
    auth.renovar().subscribe((t) => recebidos.push(t.accessToken));
    auth.renovar().subscribe((t) => recebidos.push(t.accessToken));
    http.expectOne('/api/auth/refresh').flush(tokens());

    expect(recebidos).toEqual(['acesso', 'acesso']);
    auth.renovar().subscribe();
    http.expectOne('/api/auth/refresh').flush(tokens());
  });

  it('sair avisa o servidor, limpa a sessao, esquece a conta do Google e vai para o login', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.atualizarUsuario(usuarioDeTeste());
    storage.guardar(tokens());

    const saida = auth.sair();
    http.expectOne('/api/auth/logout').flush({});
    await saida;

    expect(auth.autenticado()).toBe(false);
    expect(storage.accessToken).toBeNull();
    expect(contasEsquecidas).toBe(1);
    expect(navegar).toHaveBeenCalledWith(['/entrar']);
  });

  it('servidor fora do ar nao impede de sair desta maquina', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.atualizarUsuario(usuarioDeTeste());

    const saida = auth.sair();
    http.expectOne('/api/auth/logout').flush(null, { status: 503, statusText: 'Indisponivel' });
    await saida;

    expect(auth.autenticado()).toBe(false);
  });
});
