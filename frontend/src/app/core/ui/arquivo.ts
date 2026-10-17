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

/** Lado da foto de perfil enviada; o servidor ainda reduz para 256 px. */
export const LADO_DA_FOTO_DE_PERFIL = 320;

/**
 * Recorta um quadrado no centro da foto escolhida e regrava em JPEG pequeno.
 *
 * <p>Regravar no canvas descarta o EXIF (onde o celular guarda a localizacao) ja no
 * aparelho, e o envio fica em dezenas de kilobytes mesmo vindo de uma camera de 12 MP.</p>
 */
export function prepararFotoDePerfil(arquivo: File): Promise<{ base64: string; dataUrl: string }> {
  return new Promise((resolver, rejeitar) => {
    const falhou = () => rejeitar(new Error($localize`Nao consegui ler essa imagem.`));
    if (!arquivo.type.startsWith('image/')) {
      falhou();
      return;
    }
    const endereco = URL.createObjectURL(arquivo);
    const imagem = new Image();
    imagem.onerror = () => {
      URL.revokeObjectURL(endereco);
      falhou();
    };
    imagem.onload = () => {
      URL.revokeObjectURL(endereco);
      const lado = Math.min(imagem.naturalWidth, imagem.naturalHeight);
      const destino = Math.min(LADO_DA_FOTO_DE_PERFIL, lado);
      const canvas = document.createElement('canvas');
      canvas.width = destino;
      canvas.height = destino;
      const contexto = canvas.getContext('2d');
      if (!contexto || !lado) {
        falhou();
        return;
      }
      contexto.fillStyle = '#ffffff';
      contexto.fillRect(0, 0, destino, destino);
      contexto.drawImage(
        imagem,
        (imagem.naturalWidth - lado) / 2,
        (imagem.naturalHeight - lado) / 2,
        lado,
        lado,
        0,
        0,
        destino,
        destino,
      );
      const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
      resolver({ base64: dataUrl.slice(dataUrl.indexOf(',') + 1), dataUrl });
    };
    imagem.src = endereco;
  });
}
