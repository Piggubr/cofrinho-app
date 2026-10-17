import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from '../auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PessoasDaFamilia, SISTEMA } from './pessoas-da-familia';

/** A API devolve o id de quem lancou; a tela mostra o nome. */
describe('PessoasDaFamilia', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;
  let pessoas: PessoasDaFamilia;

  const familia = (membros: { id: string; nome: string }[]) => ({
    id: 'f',
    nome: 'Casa',
    plano: 'PREMIUM',
    convites: [],
    membros: membros.map((m) => ({
      ...m,
      email: `${m.id}@piggu.test`,
      foto: null,
      papel: 'MEMBRO',
    })),
  });

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    pessoas = TestBed.inject(PessoasDaFamilia);
  });

  afterEach(() => http.verify());

  it('busca a familia uma vez e devolve o nome; sem conta, ex-membro; o id zero e o Piggu', () => {
    TestBed.tick();
    expect(pessoas.nome('u-ana')).toBe('');
    expect(pessoas.nome(SISTEMA)).toBe('Piggu');
    http.expectOne('/api/family').flush(familia([{ id: 'u-ana', nome: 'Ana' }]));

    expect(pessoas.nome('u-ana')).toBe('Ana');
    expect(pessoas.nome('u-sumiu')).toBe('Ex-membro');
    expect(pessoas.nome(null)).toBe('Ex-membro');
    TestBed.tick();
    http.expectNone('/api/family');
  });

  it('outra pessoa entra: os nomes sao os da familia dela', () => {
    TestBed.tick();
    http.expectOne('/api/family').flush(familia([{ id: 'u-ana', nome: 'Ana' }]));

    auth.usuario.set(usuarioDeTeste({ familia: 'outra-familia' }));
    TestBed.tick();
    expect(pessoas.nome('u-ana')).toBe('');
    http.expectOne('/api/family').flush(familia([{ id: 'u-caio', nome: 'Caio' }]));
    expect(pessoas.nome('u-caio')).toBe('Caio');
    expect(pessoas.nome('u-ana')).toBe('Ex-membro');
  });
});
