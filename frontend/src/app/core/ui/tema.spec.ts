import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { aplicarTemaSalvo, esquecerQueVoltouAoPadrao, Temas, voltouAoPadrao } from './tema';

/** Tema de cor: lido do aparelho antes do Angular subir e trocado pelo Perfil. */
describe('Temas', () => {
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
  });

  afterEach(() => {
    localStorage.removeItem('piggu_tema');
    esquecerQueVoltouAoPadrao();
    delete document.documentElement.dataset['tema'];
  });

  it('aplica o tema salvo e ignora um valor desconhecido', () => {
    localStorage.setItem('piggu_tema', 'azul');
    aplicarTemaSalvo();
    expect(document.documentElement.dataset['tema']).toBe('azul');

    localStorage.setItem('piggu_tema', 'verde-limao');
    aplicarTemaSalvo();
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(TestBed.inject(Temas).atual().id).toBe('chiclete');
  });

  it('troca a cor da barra do navegador junto com o tema', () => {
    const claro = document.createElement('meta');
    claro.name = 'theme-color';
    claro.media = '(prefers-color-scheme: light)';
    const escuro = document.createElement('meta');
    escuro.name = 'theme-color';
    escuro.media = '(prefers-color-scheme: dark)';
    document.head.append(claro, escuro);

    expect(TestBed.inject(Temas).escolher('lilas')).toBe(true);

    expect(claro.content).toBe('#faf8fc');
    expect(escuro.content).toBe('#131017');
    claro.remove();
    escuro.remove();
  });

  it('sem Premium, so Chiclete, Rosa, Menta e Azul podem ser escolhidas', () => {
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const temas = TestBed.inject(Temas);

    expect(temas.escolher('coral')).toBe(false);
    expect(temas.escolher('tangerina')).toBe(false);
    expect(temas.atual().id).toBe('chiclete');
    expect(temas.escolher('rosa')).toBe(true);
    expect(temas.escolher('azul')).toBe(true);
    expect(temas.escolher('menta')).toBe(true);
    expect(temas.atual().id).toBe('menta');
  });

  it('quando o Premium acaba, a cor do Premium volta para o Chiclete e o aviso fica marcado', () => {
    const temas = TestBed.inject(Temas);
    temas.escolher('grafite');
    TestBed.tick();
    expect(document.documentElement.dataset['tema']).toBe('grafite');

    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.tick();

    expect(temas.atual().id).toBe('chiclete');
    expect(localStorage.getItem('piggu_tema')).toBe('chiclete');
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(voltouAoPadrao()).toBe(true);
  });

  it('cor gratis fica quando o Premium acaba; sem login nada muda', () => {
    const temas = TestBed.inject(Temas);
    temas.escolher('azul');
    auth.atualizarUsuario(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.tick();
    expect(temas.atual().id).toBe('azul');

    localStorage.setItem('piggu_tema', 'vinho');
    auth.usuario.set(null);
    const deNovo = TestBed.runInInjectionContext(() => new Temas());
    TestBed.tick();
    expect(deNovo.atual().id).toBe('vinho');
    expect(voltouAoPadrao()).toBe(false);
  });
});
