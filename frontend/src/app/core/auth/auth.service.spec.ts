import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ParDeTokens, Usuario } from '../api/models';
import { APP_CONFIG } from '../config/app-config';
import { VERSAO_DO_AVISO } from '../privacidade/aviso';
import { usuarioDeTeste } from '../../testing/auth-falso';
import { AuthService } from './auth.service';
import { GoogleIdentityService } from './google-identity.service';
import { AvisosNoCelular, PUSH_NATIVO } from '../nativo/avisos-no-celular';
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
        // iPhone de mentira: o sistema da o token abc123 quando o app registra.
        {
          provide: PUSH_NATIVO,
          useValue: {
            disponivel: true,
            plugin: {
              checkPermissions: async () => ({ receive: 'granted' }),
              register: async () => undefined,
              addListener: async (_evento: string, ouvir: (token: { value: string }) => void) =>
                ouvir({ value: 'abc123' }),
            },
          },
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
    expect(auth.cuidaDosPremios()).toBe(true);
  });

  it('conta nova do Google com cadastro incompleto fica pendente; o demo, sem o campo, nao', async () => {
    const entrada = auth.entrarComGoogle('id-token', true, VERSAO_DO_AVISO);
    http.expectOne('/api/auth/google').flush(tokens(usuarioDeTeste({ cadastroCompleto: false })));
    await entrada;
    expect(auth.sobreVocePendente()).toBe(true);

    auth.atualizarUsuario(usuarioDeTeste());
    expect(auth.sobreVocePendente()).toBe(false);
  });

  it('entrar com e-mail e senha usa a mesma sessao do Google', async () => {
    const entrada = auth.entrarComSenha('ana@piggu.test', 'cofrinho123', false);
    const pedido = http.expectOne('/api/auth/entrar');
    expect(pedido.request.body).toEqual({
      email: 'ana@piggu.test',
      senha: 'cofrinho123',
      lembrar: false,
    });
    expect(pedido.request.withCredentials).toBe(true);
    pedido.flush(tokens());
    await entrada;

    expect(auth.autenticado()).toBe(true);
    expect(storage.accessToken).toBe('acesso');
  });

  it('cadastro manda os dados e o aceite da versao vigente dos termos, sem abrir sessao', async () => {
    const cadastro = auth.cadastrar({
      nome: 'Ana',
      email: 'ana@piggu.test',
      senha: 'cofrinho123',
      pais: 'BR',
      documento: '52998224725',
      telefone: '11999998888',
      nascimento: '1990-05-20',
    });
    const pedido = http.expectOne('/api/auth/cadastro');
    expect(pedido.request.body).toEqual({
      nome: 'Ana',
      email: 'ana@piggu.test',
      senha: 'cofrinho123',
      pais: 'BR',
      documento: '52998224725',
      telefone: '11999998888',
      nascimento: '1990-05-20',
      versaoDosTermos: VERSAO_DO_AVISO,
    });
    pedido.flush(null, { status: 202, statusText: 'Accepted' });
    await cadastro;

    expect(auth.autenticado()).toBe(false);
  });

  it('criar ou trocar a senha atualiza o perfil e leva o cookie da sessao', async () => {
    const definir = auth.definirSenha('novaSenha456', 'cofrinho123');
    const pedido = http.expectOne('/api/auth/me/senha');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ senhaAtual: 'cofrinho123', novaSenha: 'novaSenha456' });
    expect(pedido.request.withCredentials).toBe(true);
    pedido.flush(usuarioDeTeste({ temSenha: true }));
    await definir;

    expect(auth.usuario()?.temSenha).toBe(true);
  });

  it('cada papel ve o que lhe cabe', () => {
    const casos: [Usuario['role'], boolean, boolean][] = [
      ['ADMIN', true, true],
      ['TITULAR', true, true],
      ['PARCEIRO', false, true],
      ['MEMBRO', false, false],
    ];
    for (const [role, titular, premios] of casos) {
      auth.atualizarUsuario(usuarioDeTeste({ role }));
      expect([auth.ehTitular(), auth.cuidaDosPremios()]).toEqual([titular, premios]);
      expect(auth.temPerfil(role)).toBe(true);
    }
  });

  it('Premium vem do plano, e o ADMIN sempre tem', () => {
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO' }));
    expect(auth.ehPremium()).toBe(false);
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', role: 'ADMIN' }));
    expect(auth.ehPremium()).toBe(true);
  });

  it('termos de versao antiga ou nunca aceitos ficam pendentes; ausente (demo) conta como aceito', () => {
    auth.atualizarUsuario(usuarioDeTeste());
    expect(auth.termosPendentes()).toBe(false);
    auth.atualizarUsuario(usuarioDeTeste({ termosAceitos: null }));
    expect(auth.termosPendentes()).toBe(true);
    auth.atualizarUsuario(usuarioDeTeste({ termosAceitos: '2026-09' }));
    expect(auth.termosPendentes()).toBe(true);
    auth.atualizarUsuario(usuarioDeTeste({ termosAceitos: VERSAO_DO_AVISO }));
    expect(auth.termosPendentes()).toBe(false);
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

  it('sessao vencida esquece o perfil guardado; sem sinal ele fica', async () => {
    localStorage.setItem('piggu_perfil_offline', JSON.stringify(tokens().usuario));
    let restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'), { status: 0 });
    expect(await restaurada).toBe(true);
    expect(localStorage.getItem('piggu_perfil_offline')).not.toBeNull();

    restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await restaurada).toBe(false);
    expect(localStorage.getItem('piggu_perfil_offline')).toBeNull();
  });

  it('sem sinal e com "manter conectado": abre com o perfil guardado', async () => {
    localStorage.setItem('piggu_perfil_offline', JSON.stringify(tokens().usuario));
    const restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'), { status: 0 });

    expect(await restaurada).toBe(true);
    expect(auth.usuario()?.email).toBe('titular@piggu.test');

    // Entrar sem "manter conectado" esquece o perfil guardado.
    const entrada = auth.entrarComGoogle('id', false);
    http.expectOne('/api/auth/google').flush(tokens());
    await entrada;
    expect(localStorage.getItem('piggu_perfil_offline')).toBeNull();
  });

  it('sem sinal e sem perfil guardado volta para o login', async () => {
    localStorage.removeItem('piggu_perfil_offline');
    const restaurada = auth.restaurarSessao();
    http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'), { status: 0 });

    expect(await restaurada).toBe(false);
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

  it('entre abas, a renovacao espera a trava do navegador', async () => {
    let liberar: () => void = () => {};
    const request = vi.fn((_nome: string, fazer: () => Promise<unknown>) =>
      // A trava de outra aba: so roda quando ela soltar.
      new Promise<void>((pronto) => (liberar = pronto)).then(fazer),
    );
    Object.defineProperty(navigator, 'locks', { value: { request }, configurable: true });
    try {
      const renovado = firstValueFrom(auth.renovar());
      expect(request).toHaveBeenCalledWith('piggu-renovar', expect.any(Function));
      http.expectNone('/api/auth/refresh');

      liberar();
      await new Promise((pronto) => setTimeout(pronto));
      http.expectOne('/api/auth/refresh').flush(tokens());

      expect((await renovado).accessToken).toBe('acesso');
    } finally {
      delete (navigator as { locks?: unknown }).locks;
    }
  });

  it('sair avisa o servidor, limpa a sessao, esquece a conta do Google e vai para o login', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.atualizarUsuario(usuarioDeTeste());
    storage.guardar(tokens());
    localStorage.setItem('piggu_conversa_do_assistente', '{"usuario":"x","falas":[]}');

    const saida = auth.sair();
    await new Promise((pronto) => setTimeout(pronto));
    http.expectOne('/api/auth/logout').flush({});
    await saida;

    expect(auth.autenticado()).toBe(false);
    expect(storage.accessToken).toBeNull();
    expect(contasEsquecidas).toBe(1);
    // A conversa com o assistente traz numeros da familia: nao fica para quem vier depois.
    expect(localStorage.getItem('piggu_conversa_do_assistente')).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/entrar']);
  });

  it('servidor fora do ar nao impede de sair desta maquina', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    auth.atualizarUsuario(usuarioDeTeste());

    const saida = auth.sair();
    await new Promise((pronto) => setTimeout(pronto));
    http.expectOne('/api/auth/logout').flush(null, { status: 503, statusText: 'Indisponivel' });
    await saida;

    expect(auth.autenticado()).toBe(false);
  });

  it('sair descadastra o celular dos avisos, e sai mesmo se isso falhar', async () => {
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await TestBed.inject(AvisosNoCelular).renovar();
    http.expectOne('/api/auth/me/devices').flush(null);
    auth.atualizarUsuario(usuarioDeTeste());

    const saida = auth.sair();
    const remocao = http.expectOne('/api/auth/me/devices/abc123');
    expect(remocao.request.method).toBe('DELETE');
    remocao.flush(null, { status: 500, statusText: 'Erro' });
    await new Promise((pronto) => setTimeout(pronto));
    http.expectOne('/api/auth/logout').flush({});
    await saida;

    expect(auth.autenticado()).toBe(false);
  });
});
