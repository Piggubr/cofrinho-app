import {
  TAMANHO_MAXIMO_DE_IMAGEM,
  baixar,
  imagemCabeNoLimite,
  lerImagemComoBase64,
  prepararFotoDePerfil,
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

  describe('foto de perfil', () => {
    /** Imagem falsa: o jsdom nao decodifica imagens, entao o tamanho vem do teste. */
    function imagemFalsa(largura: number, altura: number, quebrada = false) {
      class ImagemFalsa {
        naturalWidth = largura;
        naturalHeight = altura;
        onload: (() => void) | null = null;
        onerror: (() => void) | null = null;
        set src(_valor: string) {
          queueMicrotask(() => (quebrada ? this.onerror?.() : this.onload?.()));
        }
      }
      vi.stubGlobal('Image', ImagemFalsa);
    }

    let desenho: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      desenho = vi.fn();
      vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
      vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
      vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
        fillRect: vi.fn(),
        drawImage: desenho,
        fillStyle: '',
      } as unknown as CanvasRenderingContext2D);
      vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockReturnValue(
        'data:image/jpeg;base64,QUJD',
      );
    });

    afterEach(() => vi.unstubAllGlobals());

    const foto = () => new File([new Uint8Array([1])], 'eu.png', { type: 'image/png' });

    it('recorta o quadrado do centro e reduz para 320 px', async () => {
      imagemFalsa(1200, 800);

      const pronta = await prepararFotoDePerfil(foto());

      expect(pronta).toEqual({ base64: 'QUJD', dataUrl: 'data:image/jpeg;base64,QUJD' });
      expect(desenho).toHaveBeenCalledWith(expect.anything(), 200, 0, 800, 800, 0, 0, 320, 320);
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:foto');
    });

    it('foto menor que 320 px nao e ampliada', async () => {
      imagemFalsa(100, 150);

      await prepararFotoDePerfil(foto());

      expect(desenho).toHaveBeenCalledWith(expect.anything(), 0, 25, 100, 100, 0, 0, 100, 100);
    });

    it('arquivo que nao e imagem, imagem quebrada ou sem canvas: recusa', async () => {
      const texto = new File(['oi'], 'nota.txt', { type: 'text/plain' });
      await expect(prepararFotoDePerfil(texto)).rejects.toThrow('Nao consegui ler essa imagem.');

      imagemFalsa(10, 10, true);
      await expect(prepararFotoDePerfil(foto())).rejects.toThrow('Nao consegui ler essa imagem.');

      imagemFalsa(10, 10);
      vi.mocked(HTMLCanvasElement.prototype.getContext).mockReturnValue(null);
      await expect(prepararFotoDePerfil(foto())).rejects.toThrow('Nao consegui ler essa imagem.');
    });
  });
});
