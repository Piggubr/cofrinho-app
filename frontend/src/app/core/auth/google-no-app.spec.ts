import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { ATALHOS_NATIVOS } from '../nativo/atalhos';
import { GoogleNoApp, desafioDe } from './google-no-app';

/** Login com Google no app: o Google abre no navegador com o desafio e a volta troca o codigo. */
describe('GoogleNoApp', () => {
  let abertos: string[];

  function preparar(nativo: boolean, hostname = 'piggu.com.br') {
    abertos = [];
    const documento = {
      location: {
        hostname,
        href: `https://${hostname}/entrar`,
        assign: (url: string) => abertos.push(url),
      },
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'cliente-web' } },
        { provide: ATALHOS_NATIVOS, useValue: { disponivel: nativo, plugin: {} } },
        { provide: DOCUMENT, useValue: documento },
      ],
    });
    return TestBed.inject(GoogleNoApp);
  }

  afterEach(() => localStorage.clear());

  it('so vale no app instalado que abre o site no ar, nao no site nem na demonstracao', () => {
    expect(preparar(true).disponivel).toBe(true);
    TestBed.resetTestingModule();
    expect(preparar(false).disponivel).toBe(false);
    TestBed.resetTestingModule();
    expect(preparar(true, 'localhost').disponivel).toBe(false);
  });

  it('o desafio e o S256 do PKCE (exemplo da RFC 7636)', async () => {
    expect(await desafioDe('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    );
  });

  it('abre o Google com o desafio e a volta no backend; o verificador fica so no aparelho', async () => {
    const google = preparar(true);

    await google.abrir(false);

    const url = new URL(abertos[0]);
    expect(url.origin + url.pathname).toBe('https://accounts.google.com/o/oauth2/v2/auth');
    const p = url.searchParams;
    expect(p.get('client_id')).toBe('cliente-web');
    expect(p.get('redirect_uri')).toBe('https://piggu.com.br/api/auth/google/app');
    expect(p.get('response_type')).toBe('id_token');
    expect(p.get('response_mode')).toBe('form_post');
    const { verificador } = JSON.parse(localStorage.getItem('piggu.google-no-app')!);
    expect(p.get('nonce')).toBe(await desafioDe(verificador));
    expect(p.get('state')).toBe(p.get('nonce'));
    expect(abertos[0]).not.toContain(verificador);
  });

  it('a volta troca o codigo com o verificador, uma vez so', async () => {
    const google = preparar(true);
    const http = TestBed.inject(HttpTestingController);
    await google.abrir(false);
    const { verificador } = JSON.parse(localStorage.getItem('piggu.google-no-app')!);

    const volta = google.concluir('codigo-1');
    const troca = http.expectOne('/api/auth/google/app/troca');
    expect(troca.request.body).toEqual({ codigo: 'codigo-1', verificador });
    troca.flush({ idToken: 'id-token' });

    expect(await volta).toEqual({ idToken: 'id-token', lembrar: false });
    await expect(google.concluir('codigo-1')).rejects.toThrow('venceu');
    http.verify();
  });
});
