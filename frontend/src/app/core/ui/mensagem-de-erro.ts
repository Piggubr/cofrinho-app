import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../api/models';

/**
 * Extrai a mensagem que o backend escreveu para o usuario.
 *
 * <p>Todos os servicos respondem erro no mesmo formato, com o campo erro ja em
 * portugues e pronto para a tela. O resto e rede fora do ar ou algo que nao veio
 * da nossa API, e entao precisa de um texto generico.</p>
 */
export function mensagemDeErro(erro: unknown, padrao = $localize`Não foi possível concluir.`): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return padrao;
  }

  if (erro.status === 0) {
    return $localize`Sem conexão com o servidor. Confira a internet e tente de novo.`;
  }

  const corpo = erro.error as Partial<ApiError> | string | null;
  if (corpo && typeof corpo === 'object' && typeof corpo.erro === 'string') {
    return corpo.erro;
  }
  // Servico fora do ar ou lento (503/504, D-191) sem a mensagem da nossa API: o texto do
  // contrato, e nunca o HTML que um proxy pode devolver nesses casos.
  const codigo = corpo && typeof corpo === 'object' ? corpo.codigo : null;
  if (erro.status === 503 || erro.status === 504 || codigo === 'SERVICO_INDISPONIVEL') {
    return $localize`Essa parte do Piggu está fora do ar agora. Tente de novo em instantes.`;
  }
  if (typeof corpo === 'string' && corpo.trim()) {
    return corpo;
  }

  return padrao;
}
