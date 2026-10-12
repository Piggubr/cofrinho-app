/** Grupo de dados do ngsw-config.json com as listas usadas para lancar sem sinal. */
export const GRUPO_DAS_LISTAS = 'listas-para-lancar-sem-sinal';

/**
 * Apaga do aparelho as respostas que o service worker guardou para lancar sem sinal
 * (categorias e contas). O cache e por endereco, nao por pessoa: sem isto, quem entrasse
 * depois no mesmo navegador, sem sinal, veria as contas de quem saiu.
 */
export async function esquecerListasGuardadas(): Promise<void> {
  try {
    if (typeof caches === 'undefined') {
      return;
    }
    const nomes = await caches.keys();
    await Promise.all(
      nomes.filter((nome) => nome.includes(GRUPO_DAS_LISTAS)).map((nome) => caches.delete(nome)),
    );
  } catch {
    // Sem Cache Storage (navegador antigo, modo privado): nao ha o que apagar.
  }
}
