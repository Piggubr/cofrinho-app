import { problemaDaSenha } from './regras-da-senha';

/** A mesma regra do backend, conferida antes de enviar. */
describe('problemaDaSenha', () => {
  it('aceita 8 ou mais caracteres com letra e numero', () => {
    expect(problemaDaSenha('cofrinho1')).toBe('');
    expect(problemaDaSenha('ção12345')).toBe('');
  });

  it('recusa curta, sem letra, sem numero, longa demais ou igual ao e-mail', () => {
    expect(problemaDaSenha('abc123')).toContain('pelo menos 8');
    expect(problemaDaSenha('12345678')).toContain('letras e numeros');
    expect(problemaDaSenha('abcdefgh')).toContain('letras e numeros');
    expect(problemaDaSenha('a1' + 'x'.repeat(71))).toContain('no maximo');
    expect(problemaDaSenha('Ana1@piggu.test', 'ana1@piggu.test')).toContain('e-mail');
  });
});
