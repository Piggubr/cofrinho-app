import { TestBed } from '@angular/core/testing';
import { TokenStorage } from './token-storage';
import { ParDeTokens } from '../api/models';
import { usuarioDeTeste } from '../../testing/auth-falso';

const tokens: ParDeTokens = { accessToken: 'access-1', expiresIn: 1800, usuario: usuarioDeTeste() };

/**
 * O access token fica so em memoria; o refresh nem passa pelo JavaScript (cookie
 * HttpOnly). Nada de sessao pode sobrar no armazenamento do navegador.
 */
describe('TokenStorage', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('guarda e le o access token sem tocar no armazenamento do navegador', () => {
    const storage = TestBed.inject(TokenStorage);
    storage.guardar(tokens);

    expect(storage.accessToken).toBe('access-1');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('limpar esquece o token', () => {
    const storage = TestBed.inject(TokenStorage);
    storage.guardar(tokens);
    storage.limpar();

    expect(storage.accessToken).toBeNull();
  });

  it('apaga tokens que versoes antigas deixaram no navegador', () => {
    localStorage.setItem('piggu_refresh_token', 'antigo');
    sessionStorage.setItem('piggu_access_token', 'antigo');

    TestBed.inject(TokenStorage);

    expect(localStorage.getItem('piggu_refresh_token')).toBeNull();
    expect(sessionStorage.getItem('piggu_access_token')).toBeNull();
  });
});
