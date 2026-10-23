import { pareceEmail } from './email';

describe('pareceEmail', () => {
  it('aceita o formato nome@dominio.algo', () => {
    expect(pareceEmail('ana@piggu.com.br')).toBe(true);
    expect(pareceEmail('a@b.c')).toBe(true);
    expect(pareceEmail('a@b.c.')).toBe(true);
  });

  it('recusa sem arroba, com dois, com espaco ou sem ponto no meio do dominio', () => {
    expect(pareceEmail('ana.piggu.com')).toBe(false);
    expect(pareceEmail('@piggu.com')).toBe(false);
    expect(pareceEmail('ana@@piggu.com')).toBe(false);
    expect(pareceEmail('a@b@c.com')).toBe(false);
    expect(pareceEmail('ana @piggu.com')).toBe(false);
    expect(pareceEmail('ana@piggu')).toBe(false);
    expect(pareceEmail('ana@.com')).toBe(false);
    expect(pareceEmail('ana@piggu.')).toBe(false);
  });

  it('nao demora num texto comprido cheio de pontos', () => {
    const inicio = performance.now();
    expect(pareceEmail('a@' + '.'.repeat(100_000) + ' ')).toBe(false);
    expect(performance.now() - inicio).toBeLessThan(100);
  });
});
