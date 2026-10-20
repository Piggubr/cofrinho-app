import { ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { APP_CONFIG } from '../config/app-config';
import { ErrosDoApp, semDadosPessoais, telaAtual } from './erros-do-app';

describe('erros do app', () => {
  let enviados: { url: string; corpo: Record<string, string>; init: RequestInit }[];
  let tratador: ErrorHandler;

  beforeEach(() => {
    enviados = [];
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn((url: string, init: RequestInit) => {
      enviados.push({ url, corpo: JSON.parse(init.body as string), init });
      return Promise.resolve(new Response(null, { status: 204 }));
    }));
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: ErrorHandler, useClass: ErrosDoApp },
      ],
    });
    tratador = TestBed.inject(ErrorHandler);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('manda o erro sem token, sem cookie e sem dado pessoal', () => {
    tratador.handleError(new TypeError('falhou para ana@piggu.test, CPF 123.456.789-09'));

    expect(enviados).toHaveLength(1);
    expect(enviados[0].url).toBe('/api/app/erros');
    expect(enviados[0].init.credentials).toBe('omit');
    expect(new Headers(enviados[0].init.headers).has('Authorization')).toBe(false);
    expect(enviados[0].corpo['mensagem']).toBe('falhou para [email], CPF [cpf]');
  });

  it('nao repete o mesmo erro, para no quinto e ignora erro de chamada a API', () => {
    tratador.handleError(new Error('igual'));
    tratador.handleError(new Error('igual'));
    tratador.handleError(new HttpErrorResponse({ status: 500 }));
    for (let i = 0; i < 10; i++) tratador.handleError(new Error(`erro ${'x'.repeat(i)}`));

    expect(enviados).toHaveLength(5);
  });

  it('promessa rejeitada manda o motivo; falha ao enviar nao vira outro erro', () => {
    vi.stubGlobal('fetch', vi.fn(() => { throw new Error('sem rede'); }));
    expect(() => tratador.handleError({ rejection: new Error('motivo') })).not.toThrow();
  });

  it('a tela troca ids e numeros por :id', () => {
    expect(telaAtual('/gastos/3f2a9c1e-0000-4000-8000-000000000000/editar')).toBe('/gastos/:id/editar');
    expect(telaAtual('/metas/2026-10')).toBe('/metas/:id');
    expect(telaAtual('/painel')).toBe('/painel');
  });

  it('limpa telefone, token, busca e numero comprido', () => {
    expect(semDadosPessoais('(11) 98765-4321 eyJa.eyJb.c /x?q=mercado 4111111111111111'))
      .toBe('[telefone] [token] /x?[...] [numero]');
  });
});
