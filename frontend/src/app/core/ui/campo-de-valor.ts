import { Directive, ElementRef, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Campo de dinheiro que aceita virgula e ponto.
 *
 * <p>O input type="number" so entende ponto em varios celulares: "12,50" virava campo
 * vazio e o gasto nao era lancado. Aqui o campo e texto com teclado decimal, o valor
 * digitado e lido com {@link lerValor} e, ao sair do campo, aparece no padrao
 * brasileiro (12,50).</p>
 */
@Directive({
  selector: 'input[appValor]',
  host: {
    type: 'text',
    inputmode: 'decimal',
    autocomplete: 'off',
    '(input)': 'aoDigitar()',
    '(blur)': 'aoSair()',
  },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CampoDeValor), multi: true },
  ],
})
export class CampoDeValor implements ControlValueAccessor {
  private readonly campo = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
  private avisar: (valor: number | null) => void = () => {};
  private tocar: () => void = () => {};

  writeValue(valor: number | null | undefined): void {
    this.campo.value = escreverValor(valor);
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.avisar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.tocar = fn;
  }

  setDisabledState(desativado: boolean): void {
    this.campo.disabled = desativado;
  }

  /** Enquanto digita, so le: reescrever o texto agora tiraria a virgula recem-digitada. */
  protected aoDigitar(): void {
    this.avisar(lerValor(this.campo.value));
  }

  protected aoSair(): void {
    const valor = lerValor(this.campo.value);
    if (valor !== null) {
      this.campo.value = escreverValor(valor);
    }
    this.tocar();
  }
}

/**
 * Le um valor digitado do jeito que a pessoa escreveu: 12,50, 12.50, 1.234,56,
 * 1,234.56 ou R$ 12,50. Nulo quando nao ha numero.
 *
 * <p>O ultimo separador e o decimal quando ha os dois. Com um so tipo: virgula unica e
 * decimal; ponto unico seguido de exatamente tres digitos e milhar (1.500 e mil e
 * quinhentos, porque centavo tem duas casas); repetido, e sempre milhar.</p>
 */
export function lerValor(texto: string | null | undefined): number | null {
  let limpo = (texto ?? '').replace(/[^\d.,-]/g, '');
  const negativo = limpo.startsWith('-');
  limpo = limpo.replace(/-/g, '');
  if (!/\d/.test(limpo)) {
    return null;
  }
  const virgula = limpo.lastIndexOf(',');
  const ponto = limpo.lastIndexOf('.');
  let decimal = '';
  if (virgula >= 0 && ponto >= 0) {
    decimal = virgula > ponto ? ',' : '.';
  } else if (virgula >= 0) {
    decimal = limpo.indexOf(',') === virgula ? ',' : '';
  } else if (ponto >= 0) {
    const unico = limpo.indexOf('.') === ponto;
    decimal = unico && !/^\d{1,3}\.\d{3}$/.test(limpo) ? '.' : '';
  }
  const [inteiro, fracao = ''] = decimal ? partirNoUltimo(limpo, decimal) : [limpo];
  const numero = Number(`${inteiro.replace(/[.,]/g, '') || '0'}.${fracao.replace(/[.,]/g, '') || '0'}`);
  if (!Number.isFinite(numero)) {
    return null;
  }
  const arredondado = Math.round(numero * 100) / 100;
  return negativo ? -arredondado : arredondado;
}

/** Valor no padrao brasileiro, sem separador de milhar para editar sem tropeco: 1234,50. */
export function escreverValor(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) {
    return '';
  }
  return valor.toFixed(2).replace('.', ',');
}

function partirNoUltimo(texto: string, separador: string): [string, string] {
  const posicao = texto.lastIndexOf(separador);
  return [texto.slice(0, posicao), texto.slice(posicao + 1)];
}
