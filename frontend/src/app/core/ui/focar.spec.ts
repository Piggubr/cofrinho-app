import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Focar } from './focar';

@Component({
  imports: [Focar],
  template: `<input id="alvo" /><button appFocar="alvo">Ir</button>`,
})
class Hospedeiro {}

describe('Focar', () => {
  it('leva o cursor ao campo e nao envia formulario', () => {
    const tela = TestBed.createComponent(Hospedeiro);
    tela.detectChanges();
    document.body.appendChild(tela.nativeElement);
    const botao: HTMLButtonElement = tela.nativeElement.querySelector('button');
    expect(botao.type).toBe('button');
    botao.click();
    expect(document.activeElement?.id).toBe('alvo');
    tela.nativeElement.remove();
  });
});
