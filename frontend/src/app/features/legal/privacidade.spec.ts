import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONTATO_PRIVACIDADE, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { Privacidade } from './privacidade';

/** O aviso lista quem recebe dado pessoal e como falar com o encarregado. */
describe('Privacidade', () => {
  it('mostra versao, operadores e canal de contato', () => {
    TestBed.configureTestingModule({ imports: [Privacidade], providers: [provideRouter([])] });
    const tela = TestBed.createComponent(Privacidade);
    tela.detectChanges();
    const texto: string = tela.nativeElement.textContent;

    expect(texto).toContain(VERSAO_DO_AVISO);
    expect(texto).toContain(CONTATO_PRIVACIDADE);
    for (const operador of ['Gemini', 'Pluggy', 'Stripe', 'hospedagem']) {
      expect(texto).toContain(operador);
    }
  });
});
