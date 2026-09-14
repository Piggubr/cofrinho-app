import { dataCurta, dataIso, gradeDoMes, mesKey, mesPorExtenso, somarMeses } from './datas';

/**
 * Datas.
 *
 * <p>A API trabalha com dia civil, sem fuso. Converter por Date e deixar o
 * navegador aplicar fuso deslocaria um gasto de dia 1 para o dia 31 do mes
 * anterior, dependendo de onde a pessoa esta. Por isso as funcoes montam e leem
 * o texto direto.</p>
 */
describe('datas', () => {
  it('monta a chave de mes com dois digitos', () => {
    expect(mesKey(new Date(2026, 0, 15))).toBe('2026-01');
    expect(mesKey(new Date(2026, 11, 1))).toBe('2026-12');
  });

  it('monta a data ISO sem passar por fuso', () => {
    expect(dataIso(new Date(2026, 8, 5))).toBe('2026-09-05');
  });

  it('mostra o mes por extenso', () => {
    expect(mesPorExtenso('2026-09')).toBe('Setembro de 2026');
    expect(mesPorExtenso('2026-01')).toBe('Janeiro de 2026');
  });

  it('devolve a chave crua quando o mes nao faz sentido', () => {
    expect(mesPorExtenso('2026-99')).toBe('2026-99');
  });

  it('formata a data no padrao brasileiro sem deslocar o dia', () => {
    expect(dataCurta('2026-09-05')).toBe('05/09/2026');
    expect(dataCurta('2026-01-01T10:00:00Z')).toBe('01/01/2026');
  });

  it('trata data ausente sem quebrar', () => {
    expect(dataCurta(null)).toBe('');
    expect(dataCurta(undefined)).toBe('');
  });

  it('soma meses virando o ano', () => {
    expect(mesKey(somarMeses(new Date(2026, 11, 1), 1))).toBe('2027-01');
    expect(mesKey(somarMeses(new Date(2026, 0, 1), -1))).toBe('2025-12');
  });

  it('monta a grade do mes com os vazios do comeco', () => {
    // Setembro de 2026 comeca numa terca-feira: duas celulas vazias antes do dia 1.
    const grade = gradeDoMes(new Date(2026, 8, 1));

    expect(grade.slice(0, 2)).toEqual([null, null]);
    expect(grade[2]).toBe(1);
    expect(grade.filter((dia) => dia !== null)).toHaveLength(30);
  });
});
