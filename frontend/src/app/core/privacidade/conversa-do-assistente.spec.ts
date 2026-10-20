import { beforeEach, describe, expect, it } from 'vitest';
import {
  FalaGuardada,
  esquecerConversa,
  guardarConversa,
  lerConversa,
  podarConversa,
} from './conversa-do-assistente';

const ANA = 'ana';
const BIA = 'bia';

function fala(texto: string, em: string): FalaGuardada {
  return { pessoa: true, texto, em };
}

describe('conversa do assistente no aparelho', () => {
  beforeEach(() => localStorage.clear());

  it('volta para a mesma pessoa e some para outra que entre no aparelho', () => {
    guardarConversa(ANA, [fala('Quanto gastei?', '2026-10-17T12:00:00Z')]);

    expect(lerConversa(ANA, new Date('2026-10-18T12:00:00Z'))).toEqual([
      fala('Quanto gastei?', '2026-10-17T12:00:00Z'),
    ]);
    expect(lerConversa(BIA, new Date('2026-10-18T12:00:00Z'))).toEqual([]);
  });

  it('deixa de fora o que passou de 30 dias', () => {
    guardarConversa(ANA, [
      fala('velha', '2026-09-17T11:59:00Z'),
      fala('nova', '2026-09-18T12:00:00Z'),
    ]);

    expect(lerConversa(ANA, new Date('2026-10-18T12:00:00Z')).map((f) => f.texto)).toEqual([
      'nova',
    ]);
    // E sai do aparelho, nao so da tela.
    expect(localStorage.getItem('piggu_conversa_do_assistente')).not.toContain('velha');
  });

  it('ao abrir o app, poda a conversa vencida mesmo de outra pessoa', () => {
    guardarConversa(BIA, [fala('velha', '2026-09-01T12:00:00Z')]);

    podarConversa(new Date('2026-10-18T12:00:00Z'));

    expect(localStorage.length).toBe(0);
  });

  it('guarda no maximo as 200 falas mais recentes', () => {
    const falas = Array.from({ length: 250 }, (_, i) => fala(`f${i}`, '2026-10-18T12:00:00Z'));
    guardarConversa(ANA, falas);

    const lidas = lerConversa(ANA, new Date('2026-10-18T12:00:00Z'));
    expect(lidas).toHaveLength(200);
    expect(lidas[0].texto).toBe('f50');
  });

  it('esquecer e conversa vazia apagam do aparelho', () => {
    guardarConversa(ANA, [fala('oi', '2026-10-18T12:00:00Z')]);
    esquecerConversa();
    expect(localStorage.length).toBe(0);

    guardarConversa(ANA, [fala('oi', '2026-10-18T12:00:00Z')]);
    guardarConversa(ANA, []);
    expect(localStorage.length).toBe(0);
  });

  it('armazenamento corrompido nao quebra a tela', () => {
    localStorage.setItem('piggu_conversa_do_assistente', '{nao e json');
    expect(lerConversa(ANA)).toEqual([]);
    expect(localStorage.length).toBe(0);
  });
});
