import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../api/models';

/**
 * Extrai a mensagem que o backend escreveu para o usuario.
 *
 * <p>Todos os servicos respondem erro no mesmo formato, com o campo erro ja em
 * portugues e pronto para a tela. O resto e rede fora do ar ou algo que nao veio
 * da nossa API, e entao precisa de um texto generico.</p>
 */
export function mensagemDeErro(erro: unknown, padrao = 'Nao foi possivel concluir.'): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return padrao;
  }

  if (erro.status === 0) {
    return 'Sem conexao com o servidor. Confira a internet e tente de novo.';
  }

  const corpo = erro.error as Partial<ApiError> | string | null;
  if (typeof corpo === 'string' && corpo.trim()) {
    return corpo;
  }
  if (corpo && typeof corpo === 'object' && typeof corpo.erro === 'string') {
    return corpo.erro;
  }

  return padrao;
}
