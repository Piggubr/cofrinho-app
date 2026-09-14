import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../config/app-config';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { TokenStorage } from './token-storage';
import { ParDeTokens, Usuario } from '../api/models';

const usuario: Usuario = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'beatriz@piggu.test',
  nome: 'Beatriz',
  primeiroNome: 'Beatriz',
  apelido: null,
  foto: null,
  role: 'BEATRIZ',
  ativo: true,
  permissoes: {},
};

function par(sufixo: string): ParDeTokens {
  return {
    accessToken: `access-${sufixo}`,
    refreshToken: `refresh-${sufixo}`,
    expiresIn: 1800,
    usuario,
  };
}

/**
 * Interceptor de autenticacao.
 *
 * <p>E o unico lugar do app que sabe que existem dois tokens. Se ele parar de
 * renovar, o usuario e expulso a cada trinta minutos; se anexar token nas rotas de
 * login, o backend recusa a propria tentativa de entrar.</p>
 */
describe('authInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  let storage: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'teste' } },
      ],
    });

    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
    storage = TestBed.inject(TokenStorage);
    TestBed.inject(AuthService);
  });

  afterEach(() => servidor.verify());

  it('anexa o token de acesso nas chamadas da API', () => {
    storage.guardar(par('1'), true);

    http.get('/api/expenses').subscribe();

    const pedido = servidor.expectOne('/api/expenses');
    expect(pedido.request.headers.get('Authorization')).toBe('Bearer access-1');
    pedido.flush([]);
  });

  it('nao anexa token nas rotas que servem para obter um', () => {
    storage.guardar(par('1'), true);

    http.post('/api/auth/google', { idToken: 'x' }).subscribe();

    const pedido = servidor.expectOne('/api/auth/google');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush(par('1'));
  });

  it('nao mexe em chamadas para fora da nossa API', () => {
    storage.guardar(par('1'), true);

    http.get('https://outro.servico.test/dados').subscribe();

    const pedido = servidor.expectOne('https://outro.servico.test/dados');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush({});
  });

  it('renova e repete a chamada quando o token venceu', () => {
    storage.guardar(par('1'), true);
    let resposta: unknown;

    http.get('/api/expenses').subscribe((dados) => (resposta = dados));

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });

    const renovacao = servidor.expectOne('/api/auth/refresh');
    expect(renovacao.request.body).toEqual({ refreshToken: 'refresh-1' });
    renovacao.flush(par('2'));

    const repetida = servidor.expectOne('/api/expenses');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer access-2');
    repetida.flush([{ id: 'gasto-1' }]);

    expect(resposta).toEqual([{ id: 'gasto-1' }]);
    expect(storage.accessToken).toBe('access-2');
  });

  it('uma renovacao so atende varias chamadas que venceram juntas', () => {
    storage.guardar(par('1'), true);

    http.get('/api/expenses').subscribe();
    http.get('/api/notes').subscribe();

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });
    servidor.expectOne('/api/notes').flush(null, { status: 401, statusText: 'Unauthorized' });

    // Uma unica renovacao: o backend rotaciona o refresh a cada uso, entao duas
    // chamadas em paralelo invalidariam uma a outra.
    const renovacao = servidor.expectOne('/api/auth/refresh');
    renovacao.flush(par('2'));

    servidor.expectOne('/api/expenses').flush([]);
    servidor.expectOne('/api/notes').flush([]);
  });

  it('erro que nao seja 401 passa direto, sem tentar renovar', () => {
    storage.guardar(par('1'), true);
    let status = 0;

    http.get('/api/expenses').subscribe({ error: (falha) => (status = falha.status) });

    servidor.expectOne('/api/expenses').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
  });

  it('renovacao recusada encerra a sessao guardada', async () => {
    storage.guardar(par('1'), true);
    let falhou = false;

    http.get('/api/expenses').subscribe({ error: () => (falhou = true) });

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });
    servidor
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    await Promise.resolve();

    expect(falhou).toBe(true);
    expect(storage.accessToken).toBeNull();
  });
});
