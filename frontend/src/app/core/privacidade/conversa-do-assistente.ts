const CHAVE = 'piggu_conversa_do_assistente';

/** Quanto tempo a conversa fica no aparelho. Igual ao que diz o aviso de privacidade. */
export const DIAS_GUARDADA = 30;

/** Teto de falas guardadas, para o armazenamento do aparelho nao crescer sem fim. */
const FALAS_GUARDADAS = 200;

export interface FalaGuardada {
  pessoa: boolean;
  texto: string;
  tela?: string | null;
  /** Quando a fala aconteceu (ISO), para sair depois de {@link DIAS_GUARDADA} dias. */
  em: string;
}

export interface ConversaGuardada {
  /** De quem e: outra pessoa que entre no mesmo aparelho nao ve. */
  usuario: string;
  falas: FalaGuardada[];
}

/**
 * Conversa com o assistente guardada so neste aparelho, por {@link DIAS_GUARDADA} dias.
 *
 * <p>O servidor continua sem guardar nada da conversa. Fica uma por aparelho, da ultima
 * pessoa que conversou, e sai inteira ao sair da conta: a conversa traz numeros da familia
 * e o aparelho pode ser de outra pessoa depois.</p>
 */
export function lerConversa(usuario: string, agora = new Date()): FalaGuardada[] {
  const guardada = podarConversa(agora);
  return guardada?.usuario === usuario ? guardada.falas : [];
}

/**
 * Tira do aparelho as falas com mais de {@link DIAS_GUARDADA} dias, de quem quer que seja.
 * Roda ao abrir o app, para o prazo valer mesmo para quem nao abre mais o assistente.
 */
export function podarConversa(agora = new Date()): ConversaGuardada | null {
  try {
    const guardada = JSON.parse(localStorage.getItem(CHAVE) ?? 'null') as ConversaGuardada | null;
    if (!guardada || typeof guardada.usuario !== 'string' || !Array.isArray(guardada.falas)) {
      esquecerConversa();
      return null;
    }
    const limite = agora.getTime() - DIAS_GUARDADA * 24 * 60 * 60 * 1000;
    const falas = guardada.falas.filter((fala) => Date.parse(fala.em) >= limite);
    if (falas.length !== guardada.falas.length) {
      guardarConversa(guardada.usuario, falas);
    }
    return { usuario: guardada.usuario, falas };
  } catch {
    esquecerConversa();
    return null;
  }
}

export function guardarConversa(usuario: string, falas: FalaGuardada[]): void {
  try {
    if (falas.length) {
      const conversa: ConversaGuardada = { usuario, falas: falas.slice(-FALAS_GUARDADAS) };
      localStorage.setItem(CHAVE, JSON.stringify(conversa));
    } else {
      localStorage.removeItem(CHAVE);
    }
  } catch {
    // Sem armazenamento (modo privado, cheio): a conversa vale ate fechar o app.
  }
}

/** Apaga a conversa do aparelho, ao sair da conta ou em "Nova conversa". */
export function esquecerConversa(): void {
  try {
    localStorage.removeItem(CHAVE);
  } catch {
    // Nada guardado.
  }
}
