import { BRASIL, ddiDoPais, documentoDoPais, listaDePaises } from './documentos';

/** Mesmos casos do DocumentosDeIdentidadeTest no identity: os dois lados concordam. */
describe('documentos por pais', () => {
  it('todo pais da lista tem DDI', () => {
    expect(
      listaDePaises().filter((pais) => !/^[1-9]\d{0,2}$/.test(ddiDoPais(pais.codigo))),
    ).toEqual([]);
    expect(ddiDoPais('BR')).toBe('55');
    expect(ddiDoPais('PT')).toBe('351');
  });

  it('lista os paises em portugues, com o Brasil primeiro e o resto em ordem', () => {
    const paises = listaDePaises();
    expect(paises[0]).toEqual({ codigo: BRASIL, nome: 'Brasil' });
    expect(paises.length).toBeGreaterThan(240);
    expect(paises.find((pais) => pais.codigo === 'PT')?.nome).toBe('Portugal');
    const nomes = paises.slice(1).map((pais) => pais.nome);
    expect(nomes).toEqual([...nomes].sort((a, b) => a.localeCompare(b, 'pt-BR')));
    expect(paises.some((pais) => pais.codigo === 'AQ')).toBe(false);
  });

  it.each([
    ['BR', '529.982.247-25', '52998224725'],
    ['PT', '123 456 789', '123456789'],
    ['ES', '12345678-z', '12345678Z'],
    ['ES', 'X1234567L', 'X1234567L'],
    ['IT', 'rssmra85t10a562s', 'RSSMRA85T10A562S'],
    ['CL', '11.111.111-1', '111111111'],
    ['CL', '12.345.678-5', '123456785'],
    ['UY', '1.234.567-2', '12345672'],
    ['EC', '1710034065', '1710034065'],
    ['MX', 'GOMC800101HDFRRR00', 'GOMC800101HDFRRR00'],
    ['AR', '30.123.456', '30123456'],
    ['PE', '12345678', '12345678'],
    ['CO', '1.020.304.050', '1020304050'],
    ['BO', '1234567', '1234567'],
    ['PY', '1234567', '1234567'],
    ['VE', 'V-12.345.678', '12345678'],
  ])('%s aceita %s e manda %s', (pais, digitado, limpo) => {
    const doc = documentoDoPais(pais)!;
    expect(doc.valido(digitado)).toBe(true);
    expect(doc.limpar(digitado)).toBe(limpo);
  });

  it.each([
    ['BR', '529.982.247-26'],
    ['PT', '123456780'],
    ['PT', '423456789'],
    ['ES', '12345678A'],
    ['IT', 'RSSMRA85T10A562T'],
    ['CL', '11.111.111-2'],
    ['UY', '1.234.567-3'],
    ['EC', '1710034066'],
    ['EC', '9910034065'],
    ['MX', 'GOMC800101HDFRRR01'],
    ['PE', '1234567'],
    ['VE', '123'],
  ])('%s recusa %s', (pais, digitado) => {
    expect(documentoDoPais(pais)!.valido(digitado)).toBe(false);
  });

  it('so o CPF tem mascara; paises fora do catalogo nao pedem documento', () => {
    expect(documentoDoPais('BR')!.formatar('52998224725')).toBe('529.982.247-25');
    expect(documentoDoPais('PT')!.formatar('123 456')).toBe('123 456');
    expect(documentoDoPais('US')).toBeNull();
    expect(documentoDoPais(null)).toBeNull();
  });
});
