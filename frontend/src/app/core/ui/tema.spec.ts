import { TestBed } from '@angular/core/testing';
import { aplicarTemaSalvo, Temas } from './tema';

/** Tema de cor: lido do aparelho antes do Angular subir e trocado pelo Perfil. */
describe('Temas', () => {
  afterEach(() => {
    localStorage.removeItem('piggu_tema');
    delete document.documentElement.dataset['tema'];
  });

  it('aplica o tema salvo e ignora um valor desconhecido', () => {
    localStorage.setItem('piggu_tema', 'azul');
    aplicarTemaSalvo();
    expect(document.documentElement.dataset['tema']).toBe('azul');

    localStorage.setItem('piggu_tema', 'verde-limao');
    aplicarTemaSalvo();
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(TestBed.inject(Temas).atual().id).toBe('rosa');
  });

  it('troca a cor da barra do navegador junto com o tema', () => {
    const claro = document.createElement('meta');
    claro.name = 'theme-color';
    claro.media = '(prefers-color-scheme: light)';
    const escuro = document.createElement('meta');
    escuro.name = 'theme-color';
    escuro.media = '(prefers-color-scheme: dark)';
    document.head.append(claro, escuro);

    TestBed.inject(Temas).escolher('lilas');

    expect(claro.content).toBe('#faf8fc');
    expect(escuro.content).toBe('#131017');
    claro.remove();
    escuro.remove();
  });
});
