import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Vazio } from './vazio';

@Component({
  imports: [Vazio],
  template: `
    <app-vazio icone="recibo" titulo="Nenhum gasto neste mês">
      <p>Lance o primeiro.</p>
      <p class="vazio-acao"><button>Lançar um gasto</button></p>
    </app-vazio>
  `,
})
class Hospedeiro {}

describe('Vazio', () => {
  it('mostra o titulo, o texto e a acao para sair do vazio', () => {
    const tela = TestBed.createComponent(Hospedeiro);
    tela.detectChanges();
    const vazio: HTMLElement = tela.nativeElement.querySelector('app-vazio');
    expect(vazio.querySelector('.titulo')?.textContent).toBe('Nenhum gasto neste mês');
    expect(vazio.textContent).toContain('Lance o primeiro.');
    expect(vazio.querySelector('button')?.textContent).toBe('Lançar um gasto');
    expect(vazio.querySelector('.selo app-icone')).not.toBeNull();
  });
});
