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
  email: 'titular@piggu.test',
  nome: 'Titular',
  primeiroNome: 'Titular',
  apelido: null,
  foto: null,
  role: 'TITULAR',
  ativo: true,
  permissoes: {},
  preferencias: { moeda: 'EUR', moedaConversao: 'BRL', mostrarCotacao: true },
  plano: 'GRATUITO',
  premiumAte: null,
  familia: '22222222-2222-2222-2222-222222222222',
};

function par(sufixo: string): ParDeTokens {
  return {
    accessToken: `access-${sufixo}`,
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
    storage.guardar(par('1'));

    http.get('/api/expenses').subscribe();

    const pedido = servidor.expectOne('/api/expenses');
    expect(pedido.request.headers.get('Authorization')).toBe('Bearer access-1');
    pedido.flush([]);
  });

  it('nao anexa token nas rotas que servem para obter um', () => {
    storage.guardar(par('1'));

    http.post('/api/auth/google', { idToken: 'x' }).subscribe();

    const pedido = servidor.expectOne('/api/auth/google');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush(par('1'));
  });

  it('nao mexe em chamadas para fora da nossa API', () => {
    storage.guardar(par('1'));

    http.get('https://outro.servico.test/dados').subscribe();

    const pedido = servidor.expectOne('https://outro.servico.test/dados');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    pedido.flush({});
  });

  it('renova e repete a chamada quando o token venceu', () => {
    storage.guardar(par('1'));
    let resposta: unknown;

    http.get('/api/expenses').subscribe((dados) => (resposta = dados));

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });

    const renovacao = servidor.expectOne('/api/auth/refresh');
    // O refresh vai no cookie, que o navegador manda sozinho: o corpo segue vazio.
    expect(renovacao.request.body).toEqual({});
    expect(renovacao.request.withCredentials).toBe(true);
    renovacao.flush(par('2'));

    const repetida = servidor.expectOne('/api/expenses');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer access-2');
    repetida.flush([{ id: 'gasto-1' }]);

    expect(resposta).toEqual([{ id: 'gasto-1' }]);
    expect(storage.accessToken).toBe('access-2');
  });

  it('uma renovacao so atende varias chamadas que venceram juntas', () => {
    storage.guardar(par('1'));

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
    expect(storage.accessToken).toBe('access-2');
  });

  it('erro que nao seja 401 passa direto, sem tentar renovar', () => {
    storage.guardar(par('1'));
    let status = 0;

    http.get('/api/expenses').subscribe({ error: (falha) => (status = falha.status) });

    servidor.expectOne('/api/expenses').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
  });

  it('renovacao recusada encerra a sessao guardada', async () => {
    storage.guardar(par('1'));
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

  it('renovou, mas a chamada repetida falhou: o erro chega a quem chamou e a sessao fica', () => {
    storage.guardar(par('1'));
    const auth = TestBed.inject(AuthService);
    const encerrar = vi.spyOn(auth, 'encerrarLocalmente');
    let status = 0;

    http.post('/api/expenses', {}).subscribe({ error: (falha) => (status = falha.status) });

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });
    servidor.expectOne('/api/auth/refresh').flush(par('2'));
    servidor
      .expectOne('/api/expenses')
      .flush(null, { status: 422, statusText: 'Unprocessable Entity' });

    expect(status).toBe(422);
    expect(encerrar).not.toHaveBeenCalled();
    expect(storage.accessToken).toBe('access-2');
  });

  it('renovacao sem sinal nao encerra a sessao', () => {
    storage.guardar(par('1'));
    const encerrar = vi.spyOn(TestBed.inject(AuthService), 'encerrarLocalmente');
    let status = -1;

    http.get('/api/expenses').subscribe({ error: (falha) => (status = falha.status) });

    servidor.expectOne('/api/expenses').flush(null, { status: 401, statusText: 'Unauthorized' });
    servidor.expectOne('/api/auth/refresh').error(new ProgressEvent('error'), { status: 0 });

    expect(status).toBe(0);
    expect(encerrar).not.toHaveBeenCalled();
  });
});
