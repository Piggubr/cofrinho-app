/**
 * Le uma imagem escolhida pelo usuario e devolve o conteudo em base64 puro.
 *
 * <p>O FileReader entrega uma data URL completa; o backend espera so os bytes
 * codificados, entao o prefixo sai aqui.</p>
 */
export function lerImagemComoBase64(
  arquivo: File,
): Promise<{ base64: string; mimeType: string; dataUrl: string }> {
  return new Promise((resolver, rejeitar) => {
    const leitor = new FileReader();
    leitor.onerror = () => rejeitar(new Error($localize`Nao consegui ler essa imagem.`));
    leitor.onload = () => {
      const dataUrl = String(leitor.result ?? '');
      const separador = dataUrl.indexOf(',');
      if (separador < 0) {
        rejeitar(new Error($localize`Nao consegui ler essa imagem.`));
        return;
      }
      resolver({
        base64: dataUrl.slice(separador + 1),
        mimeType: arquivo.type || 'image/jpeg',
        dataUrl,
      });
    };
    leitor.readAsDataURL(arquivo);
  });
}

/** Entrega um arquivo para a pessoa salvar, sem passar por outro servidor. */
export function baixar(conteudo: Blob, nome: string): void {
  const endereco = URL.createObjectURL(conteudo);
  const link = document.createElement('a');
  link.href = endereco;
  link.download = nome;
  link.click();
  URL.revokeObjectURL(endereco);
}

/** Cinco megabytes, o mesmo teto que o backend aplica. */
export const TAMANHO_MAXIMO_DE_IMAGEM = 5 * 1024 * 1024;

export function imagemCabeNoLimite(arquivo: File): boolean {
  return arquivo.size <= TAMANHO_MAXIMO_DE_IMAGEM;
}
