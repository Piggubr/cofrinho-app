import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CampoDeValor, escreverValor, lerValor } from './campo-de-valor';

describe('lerValor', () => {
  it('aceita virgula e ponto como decimal', () => {
    expect(lerValor('12,50')).toBe(12.5);
    expect(lerValor('12.50')).toBe(12.5);
    expect(lerValor('0,99')).toBe(0.99);
    expect(lerValor(',5')).toBe(0.5);
    expect(lerValor('7')).toBe(7);
  });

  it('entende o milhar nos dois padroes', () => {
    expect(lerValor('1.234,56')).toBe(1234.56);
    expect(lerValor('1,234.56')).toBe(1234.56);
    expect(lerValor('1.500')).toBe(1500);
    expect(lerValor('1.234.567')).toBe(1234567);
    expect(lerValor('1.234.567,89')).toBe(1234567.89);
  });

  it('ignora simbolo e espacos, e o separador ainda sem digito depois', () => {
    expect(lerValor('R$ 12,50')).toBe(12.5);
    expect(lerValor('12,')).toBe(12);
    expect(lerValor('1.5')).toBe(1.5);
  });

  it('le igual ao app nativo: 0.500 nao e milhar, agrupamento torto e tres casas sao nulos', () => {
    expect(lerValor('0.500')).toBeNull();
    expect(lerValor('0.50')).toBe(0.5);
    expect(lerValor('1.2.3')).toBeNull();
    expect(lerValor('1,234,567')).toBeNull();
    expect(lerValor('1,005')).toBeNull();
    expect(lerValor('10,005')).toBeNull();
    expect(lerValor('12,345')).toBeNull();
    expect(lerValor('-5')).toBeNull();
    expect(lerValor('12,5a')).toBeNull();
    expect(lerValor(',')).toBeNull();
    expect(lerValor('R$')).toBeNull();
  });

  it('sem numero, devolve nulo', () => {
    expect(lerValor('')).toBeNull();
    expect(lerValor(' ')).toBeNull();
    expect(lerValor('abc')).toBeNull();
    expect(lerValor(null)).toBeNull();
  });

  it('escreve no padrao brasileiro', () => {
    expect(escreverValor(12.5)).toBe('12,50');
    expect(escreverValor(1234)).toBe('1234,00');
    expect(escreverValor(null)).toBe('');
  });
});

@Component({
  imports: [CampoDeValor, FormsModule],
  template: `<input appValor [ngModel]="valor()" (ngModelChange)="valor.set($event)" />`,
})
class Hospedeiro {
  readonly valor = signal<number | null>(null);
}

describe('CampoDeValor', () => {
  async function montar() {
    const tela = TestBed.createComponent(Hospedeiro);
    tela.detectChanges();
    await tela.whenStable();
    const campo: HTMLInputElement = tela.nativeElement.querySelector('input');
    return { tela, campo };
  }

  it('e campo de texto com teclado decimal', async () => {
    const { campo } = await montar();
    expect(campo.type).toBe('text');
    expect(campo.getAttribute('inputmode')).toBe('decimal');
  });

  it('digitar com virgula vira numero e, ao sair, aparece com virgula', async () => {
    const { tela, campo } = await montar();
    campo.value = '12,5';
    campo.dispatchEvent(new Event('input'));
    expect(tela.componentInstance.valor()).toBe(12.5);
    expect(campo.value).toBe('12,5');
    campo.dispatchEvent(new Event('blur'));
    expect(campo.value).toBe('12,50');
  });

  it('digitar com ponto tambem vale', async () => {
    const { tela, campo } = await montar();
    campo.value = '23.90';
    campo.dispatchEvent(new Event('input'));
    expect(tela.componentInstance.valor()).toBe(23.9);
  });

  it('valor vindo de fora aparece com virgula, e limpar esvazia o campo', async () => {
    const { tela, campo } = await montar();
    tela.componentInstance.valor.set(18.9);
    tela.detectChanges();
    await tela.whenStable();
    expect(campo.value).toBe('18,90');
    tela.componentInstance.valor.set(null);
    tela.detectChanges();
    await tela.whenStable();
    expect(campo.value).toBe('');
  });
});
