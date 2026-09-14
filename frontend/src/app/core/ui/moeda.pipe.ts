import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formata valores em euro, a moeda em que o Piggu registra tudo.
 *
 * <p>O app original escrevia o simbolo a mao em varios lugares; centralizar evita
 * que uma tela mostre 1.29 e outra 1,29 para o mesmo numero.</p>
 */
@Pipe({ name: 'euro' })
export class EuroPipe implements PipeTransform {
  private readonly formatador = new Intl.NumberFormat('pt-PT', {
    style: 'currency',
    currency: 'EUR',
  });

  transform(valor: number | null | undefined): string {
    return this.formatador.format(Number(valor ?? 0));
  }
}
