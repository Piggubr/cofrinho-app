import { Injectable, Pipe, PipeTransform, computed, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/** Formatador de uma moeda ISO 4217 no padrao brasileiro de separadores. */
export function formatadorDe(codigo: string): Intl.NumberFormat {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: codigo });
}

/** Simbolo curto da moeda (€, R$, US$...), ou o proprio codigo quando nao ha simbolo. */
export function simboloDe(codigo: string): string {
  return (
    formatadorDe(codigo)
      .formatToParts(0)
      .find((parte) => parte.type === 'currency')?.value ?? codigo
  );
}

/**
 * Moeda em que a pessoa logada ve os valores.
 *
 * <p>O app original escrevia o euro a mao em varios lugares. A moeda agora e
 * preferencia de cada conta, guardada no backend; os valores gravados nao mudam,
 * so a forma de mostrar.</p>
 */
@Injectable({ providedIn: 'root' })
export class MoedaService {
  private readonly auth = inject(AuthService);

  readonly codigo = computed(() => this.auth.usuario()?.preferencias?.moeda ?? 'EUR');
  readonly simbolo = computed(() => simboloDe(this.codigo()));
  private readonly formatador = computed(() => formatadorDe(this.codigo()));

  formatar(valor: number | null | undefined): string {
    return this.formatador().format(Number(valor ?? 0));
  }
}

/**
 * Formata um valor na moeda escolhida pela pessoa.
 *
 * <p>Impuro de proposito: a moeda pode mudar na tela de perfil sem que o valor mude,
 * e o formatador ja fica em cache no servico.</p>
 */
@Pipe({ name: 'moeda', pure: false })
export class MoedaPipe implements PipeTransform {
  private readonly moeda = inject(MoedaService);

  /** @param codigo moeda fixa do valor (ex.: saldo de banco em BRL); sem ela, a da pessoa */
  transform(valor: number | null | undefined, codigo?: string): string {
    return codigo ? formatadorDe(codigo).format(Number(valor ?? 0)) : this.moeda.formatar(valor);
  }
}
