import { TestBed } from '@angular/core/testing';
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
  preferencias: { moeda: 'EUR', moedaConversao: 'BRL', mostrarCotacao: true },
};

const tokens: ParDeTokens = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresIn: 1800,
  usuario,
};

/**
 * Guarda de tokens.
 *
 * <p>A escolha entre lembrar e nao lembrar decide se a sessao sobrevive a fechar o
 * navegador. Trocar os dois de lugar deixaria alguem conectado em um computador
 * emprestado, entao vale travar o comportamento.</p>
 */
describe('TokenStorage', () => {
  let storage: TokenStorage;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    storage = TestBed.inject(TokenStorage);
  });

  it('guarda no localStorage quando a pessoa pede para ser lembrada', () => {
    storage.guardar(tokens, true);

    expect(localStorage.getItem('piggu_access_token')).toBe('access-1');
    expect(sessionStorage.getItem('piggu_access_token')).toBeNull();
  });

  it('guarda apenas na sessao quando a pessoa nao quer ser lembrada', () => {
    storage.guardar(tokens, false);

    expect(sessionStorage.getItem('piggu_refresh_token')).toBe('refresh-1');
    expect(localStorage.getItem('piggu_refresh_token')).toBeNull();
  });

  it('le de volta o que guardou', () => {
    storage.guardar(tokens, true);

    expect(storage.accessToken).toBe('access-1');
    expect(storage.refreshToken).toBe('refresh-1');
  });

  it('renovar preserva onde os tokens estavam guardados', () => {
    storage.guardar(tokens, false);
    storage.atualizar({ ...tokens, accessToken: 'access-2', refreshToken: 'refresh-2' });

    expect(sessionStorage.getItem('piggu_access_token')).toBe('access-2');
    expect(localStorage.getItem('piggu_access_token')).toBeNull();
  });

  it('limpar apaga os dois lugares', () => {
    storage.guardar(tokens, true);
    storage.limpar();

    expect(storage.accessToken).toBeNull();
    expect(localStorage.getItem('piggu_access_token')).toBeNull();
    expect(sessionStorage.getItem('piggu_access_token')).toBeNull();
  });

  it('sem nada guardado nao devolve token', () => {
    expect(storage.accessToken).toBeNull();
    expect(storage.refreshToken).toBeNull();
  });
});
