import { BRASIL, CPF, ddiDoPais, listaDePaises } from './documentos';

describe('paises e documento', () => {
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

  it('o documento e o CPF, com mascara, conferido pelo digito', () => {
    expect(CPF.valido('529.982.247-25')).toBe(true);
    expect(CPF.limpar('529.982.247-25')).toBe('52998224725');
    expect(CPF.valido('529.982.247-26')).toBe(false);
    expect(CPF.formatar('52998224725')).toBe('529.982.247-25');
  });
});
