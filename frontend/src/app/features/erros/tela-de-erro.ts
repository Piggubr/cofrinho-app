import { Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HumorDoPorquinho, Porquinho } from '../../core/ui/porquinho';

export type TipoDeErro = 'nao-encontrada' | 'servidor';

/**
 * Telas de 404 e de 500.
 *
 * <p>A 404 e o destino de todo endereco que nao existe (antes ele voltava calado para o
 * Painel). A 500 aparece quando uma tela nao consegue abrir: o arquivo dela nao chegou
 * (rede caiu, ou um deploy novo trocou os arquivos) ou deu erro ao montar. Sem internet,
 * a mesma tela diz isso em vez de culpar o servidor.</p>
 *
 * <p>Fica fora da moldura e e carregada junto com o app, nao sob demanda: se ela mesma
 * dependesse da rede, a falha que a chamou impediria de abri-la.</p>
 */
@Component({
  selector: 'app-tela-de-erro',
  imports: [RouterLink, Porquinho],
  templateUrl: './tela-de-erro.html',
  styleUrl: './tela-de-erro.scss',
})
export class TelaDeErro {
  /** Vem do data da rota (withComponentInputBinding). */
  readonly tipo = input<TipoDeErro>('nao-encontrada');

  /** Endereco que nao abriu, para o "Tentar de novo" ir direto a ele. */
  private readonly destino: string | null =
    (inject(Router).currentNavigation()?.extras.state?.['destino'] as string | undefined) ?? null;

  protected readonly caso = computed(() => {
    if (this.tipo() === 'nao-encontrada') {
      return 'nao-encontrada';
    }
    return navigator.onLine ? 'servidor' : 'sem-conexao';
  });

  protected readonly humor = computed<HumorDoPorquinho>(() =>
    this.caso() === 'nao-encontrada' ? 'perdido' : 'tonto',
  );

  protected tentarDeNovo(): void {
    // Recarrega de verdade: depois de um deploy, so assim os arquivos novos chegam.
    if (this.destino) {
      location.assign(this.destino);
    } else {
      location.reload();
    }
  }

  protected voltar(): void {
    if (history.length > 1) {
      history.back();
    } else {
      location.assign('/');
    }
  }
}
