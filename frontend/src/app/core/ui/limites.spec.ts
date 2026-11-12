import { TETO_DE_ENTRADA, TETO_DE_GASTO, confirmarData, dataPedeConfirmacao, erroDeData, erroDeValor } from './limites';

/** Limites de valor e data iguais aos do backend (D-189, D-194). */
describe('limites', () => {
  it('pede valor a partir de 0,01 e respeita o teto de cada campo', () => {
    expect(erroDeValor(null, TETO_DE_GASTO)).toBe('Digite um valor a partir de R$ 0,01.');
    expect(erroDeValor(0, TETO_DE_GASTO)).toBe('Digite um valor a partir de R$ 0,01.');
    expect(erroDeValor(0.009, TETO_DE_GASTO)).toBe('Digite um valor a partir de R$ 0,01.');
    expect(erroDeValor(0.01, TETO_DE_GASTO)).toBeNull();
    expect(erroDeValor(1_000_000, TETO_DE_GASTO)).toBeNull();
    expect(erroDeValor(1_000_000.01, TETO_DE_GASTO)).toBe('O valor máximo é R$ 1.000.000,00.');
    expect(erroDeValor(10_000_000, TETO_DE_ENTRADA)).toBeNull();
    expect(erroDeValor(10_000_000.01, TETO_DE_ENTRADA)).toBe('O valor máximo é R$ 10.000.000,00.');
  });

  it('recusa data fora de 2000 a hoje + 2 anos, como o backend', () => {
    const hoje = '2026-11-11';
    expect(erroDeData('1999-12-31', hoje)).toBe('Confira a data: use uma data entre 2000 e 2028.');
    expect(erroDeData('2000-01-01', hoje)).toBeNull();
    expect(erroDeData('2028-11-11', hoje)).toBeNull();
    expect(erroDeData('2028-11-12', hoje)).not.toBeNull();
    expect(erroDeData('', hoje)).not.toBeNull();
  });

  it('pede confirmacao para data futura ou de mais de 1 ano atras', () => {
    const hoje = '2026-11-11';
    expect(dataPedeConfirmacao('2026-11-11', hoje)).toBe(false);
    expect(dataPedeConfirmacao('2025-11-11', hoje)).toBe(false);
    expect(dataPedeConfirmacao('2025-11-10', hoje)).toBe(true);
    expect(dataPedeConfirmacao('2026-11-12', hoje)).toBe(true);
    // A receber: futuro ate 1 ano e normal.
    expect(dataPedeConfirmacao('2027-11-11', hoje, true)).toBe(false);
    expect(dataPedeConfirmacao('2027-11-12', hoje, true)).toBe(true);
  });

  it('so abre o confirm quando a data pede e mostra a data por extenso', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    expect(confirmarData('2026-11-01', '2026-11-11')).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
    expect(confirmarData('1900-01-01', '2026-11-11')).toBe(false);
    expect(confirm).toHaveBeenCalledWith('Essa data é 01/01/1900. Lançar mesmo assim?');
    confirm.mockRestore();
  });
});
