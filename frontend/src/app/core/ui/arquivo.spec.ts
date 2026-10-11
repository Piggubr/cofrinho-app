import {
  TAMANHO_MAXIMO_DE_IMAGEM,
  baixar,
  imagemCabeNoLimite,
  lerImagemComoBase64,
} from './arquivo';

/** Imagem escolhida pela pessoa vira base64 puro; arquivo gerado vira download. */
describe('arquivo', () => {
  afterEach(() => vi.restoreAllMocks());

  it('le a imagem e tira o prefixo da data URL', async () => {
    const imagem = new File([new Uint8Array([1, 2, 3])], 'foto.png', { type: 'image/png' });

    const lida = await lerImagemComoBase64(imagem);

    expect(lida.base64).toBe('AQID');
    expect(lida.mimeType).toBe('image/png');
    expect(lida.dataUrl).toBe('data:image/png;base64,AQID');
  });

  it('sem tipo informado, assume JPEG', async () => {
    const lida = await lerImagemComoBase64(new File([new Uint8Array([1])], 'foto'));
    expect(lida.mimeType).toBe('image/jpeg');
  });

  it('o limite de 5 MB e o mesmo do backend', () => {
    const tamanho = (bytes: number) => ({ size: bytes }) as File;
    expect(imagemCabeNoLimite(tamanho(TAMANHO_MAXIMO_DE_IMAGEM))).toBe(true);
    expect(imagemCabeNoLimite(tamanho(TAMANHO_MAXIMO_DE_IMAGEM + 1))).toBe(false);
  });

  it('baixar cria o link com o nome e libera o endereco', () => {
    const criar = vi.fn(() => 'blob:x');
    const liberar = vi.fn();
    Object.assign(URL, { createObjectURL: criar, revokeObjectURL: liberar });
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.download).toBe('gastos.csv');
      expect(this.href).toBe('blob:x');
    });

    baixar(new Blob(['a;b']), 'gastos.csv');

    expect(clique).toHaveBeenCalledOnce();
    expect(liberar).toHaveBeenCalledWith('blob:x');
  });
});
