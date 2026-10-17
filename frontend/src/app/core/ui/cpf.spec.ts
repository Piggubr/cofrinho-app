import { cpfValido, digitosDoCpf, formatarCpf } from './cpf';

describe('CPF', () => {
  it('confere os digitos verificadores', () => {
    expect(cpfValido('529.982.247-25')).toBe(true);
    expect(cpfValido('11144477735')).toBe(true);
    expect(cpfValido('529.982.247-24')).toBe(false);
    expect(cpfValido('111.111.111-11')).toBe(false);
    expect(cpfValido('1234567890')).toBe(false);
  });

  it('formata enquanto digita e corta no 11o digito', () => {
    expect(formatarCpf('529')).toBe('529');
    expect(formatarCpf('5299822')).toBe('529.982.2');
    expect(formatarCpf('529982247251234')).toBe('529.982.247-25');
    expect(digitosDoCpf('529.982.247-25')).toBe('52998224725');
  });
});
