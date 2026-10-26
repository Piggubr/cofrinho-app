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
 * 1,234.56 ou R$ 12,50. Nulo quando nao e um valor.
 *
 * <p>As mesmas regras do app nativo (Formatos.lerValor), para os dois lerem igual. Com os
 * dois separadores, o ultimo e o decimal. Virgula sozinha e decimal e nao se repete. Ponto
 * sozinho e milhar quando todo grupo depois dele tem tres digitos e o primeiro nao e "0"
 * (1.500 e mil e quinhentos, 0.500 nao); senao e decimal, uma vez so. Mais de duas casas
 * decimais, sinal ou letra: nulo.</p>
 */
export function lerValor(texto: string | null | undefined): number | null {
  const limpo = (texto ?? '').replaceAll('R$', '').replace(/\s/g, '');
  if (!/^[\d.,]+$/.test(limpo)) {
    return null;
  }
  const virgula = limpo.lastIndexOf(',');
  const ponto = limpo.lastIndexOf('.');
  let inteiro = limpo;
  let fracao = '';
  if (virgula >= 0 && ponto >= 0) {
    const decimal = Math.max(virgula, ponto);
    inteiro = limpo.slice(0, decimal).replaceAll(decimal === virgula ? '.' : ',', '');
    fracao = limpo.slice(decimal + 1);
  } else if (virgula >= 0) {
    if (limpo.indexOf(',') !== virgula) {
      return null;
    }
    [inteiro, fracao] = [limpo.slice(0, virgula), limpo.slice(virgula + 1)];
  } else if (ponto >= 0) {
    const grupos = limpo.split('.');
    if (grupos.slice(1).every((grupo) => grupo.length === 3) && !['', '0'].includes(grupos[0])) {
      inteiro = grupos.join('');
    } else if (grupos.length === 2) {
      [inteiro, fracao] = grupos;
    } else {
      return null;
    }
  }
  if (fracao.length > 2 || !/^\d*$/.test(inteiro) || !/^\d*$/.test(fracao) || !(inteiro || fracao)) {
    return null;
  }
  return Number(`${inteiro || '0'}.${fracao || '0'}`);
}

/** Valor no padrao brasileiro, sem separador de milhar para editar sem tropeco: 1234,50. */
export function escreverValor(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) {
    return '';
  }
  return valor.toFixed(2).replace('.', ',');
}
