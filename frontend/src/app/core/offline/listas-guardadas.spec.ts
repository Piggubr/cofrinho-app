import { afterEach, describe, expect, it, vi } from 'vitest';
import { esquecerListasGuardadas } from './listas-guardadas';

describe('esquecerListasGuardadas', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('apaga so os caches das listas para lancar sem sinal', async () => {
    const apagados: string[] = [];
    vi.stubGlobal('caches', {
      keys: async () => [
        'ngsw:/:1:assets:app:cache',
        'ngsw:/:1:data:listas-para-lancar-sem-sinal:cache',
        'ngsw:/:db:1:data:listas-para-lancar-sem-sinal:lru',
      ],
      delete: async (nome: string) => {
        apagados.push(nome);
        return true;
      },
    });

    await esquecerListasGuardadas();

    expect(apagados).toEqual([
      'ngsw:/:1:data:listas-para-lancar-sem-sinal:cache',
      'ngsw:/:db:1:data:listas-para-lancar-sem-sinal:lru',
    ]);
  });

  it('sem Cache Storage nao quebra a saida', async () => {
    vi.stubGlobal('caches', {
      keys: async () => {
        throw new Error('bloqueado');
      },
    });

    await expect(esquecerListasGuardadas()).resolves.toBeUndefined();
  });
});
