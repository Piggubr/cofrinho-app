import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';

@Component({ template: '<p>Painel</p>' })
class TelaFalsa {}

describe('App', () => {
  it('mostra a abertura ate a primeira tela ficar pronta', async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: 'painel', component: TelaFalsa }])],
    });
    const tela = TestBed.createComponent(App);
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('.abertura')?.textContent).toContain('Abrindo o Piggu');

    await TestBed.inject(Router).navigateByUrl('/painel');
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('.abertura')).toBeNull();
    expect(tela.nativeElement.querySelector('.navegacao')).toBeNull();
    expect(tela.nativeElement.textContent).toContain('Painel');
  });
});
