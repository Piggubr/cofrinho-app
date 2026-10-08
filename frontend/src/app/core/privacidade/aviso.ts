import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../api/models';

/**
 * Versao vigente dos Termos e do Aviso de Privacidade.
 *
 * <p>Igual a Consentimentos.VERSAO_DO_AVISO no backend. Mudou o texto, muda nos dois:
 * o backend recusa consentimento com versao diferente, entao um front antigo nao colhe
 * aceite sobre um texto que ja saiu do ar.</p>
 */
export const VERSAO_DO_AVISO = '2026-10-08';

/** Canal do encarregado (LGPD art. 41). Trocar quando o dominio definitivo existir. */
export const CONTATO_PRIVACIDADE = 'privacidade@piggu.app';

/** O backend recusou porque falta a pessoa autorizar (ou aceitar) algo agora. */
export function pedeConsentimento(erro: unknown): boolean {
  return codigoDoErro(erro) === 'CONSENTIMENTO_NECESSARIO';
}

export function codigoDoErro(erro: unknown): string | null {
  if (!(erro instanceof HttpErrorResponse) || typeof erro.error !== 'object' || !erro.error) {
    return null;
  }
  return (erro.error as Partial<ApiError>).codigo ?? null;
}
