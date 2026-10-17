import { dataIso, problemaDoNascimento } from './nascimento';

describe('problemaDoNascimento', () => {
  const anosAtras = (anos: number) => {
    const data = new Date();
    data.setFullYear(data.getFullYear() - anos);
    return dataIso(data);
  };

  it('aceita quem tem 13 anos ou mais', () => {
    expect(problemaDoNascimento(anosAtras(13))).toBe('');
    expect(problemaDoNascimento('1990-05-20')).toBe('');
  });

  it('recusa menor de 13, data vazia e data no futuro', () => {
    expect(problemaDoNascimento(anosAtras(12))).toContain('13');
    expect(problemaDoNascimento('')).not.toBe('');
    expect(problemaDoNascimento(anosAtras(-1))).not.toBe('');
  });
});
