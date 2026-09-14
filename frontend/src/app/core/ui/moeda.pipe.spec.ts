import { EuroPipe } from './moeda.pipe';

describe('EuroPipe', () => {
  const pipe = new EuroPipe();

  it('formata em euro', () => {
    // O separador do pt-PT e um espaco nao separavel, entao a comparacao e por partes.
    expect(pipe.transform(1.29)).toContain('1,29');
    expect(pipe.transform(1.29)).toContain('€');
  });

  it('trata valor ausente como zero', () => {
    expect(pipe.transform(null)).toContain('0,00');
    expect(pipe.transform(undefined)).toContain('0,00');
  });
});
