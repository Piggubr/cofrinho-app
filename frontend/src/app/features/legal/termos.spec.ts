import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONTATO_PRIVACIDADE, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { Termos } from './termos';

/** Os termos abrem sem login e citam a versao que a pessoa aceita ao criar a conta. */
describe('Termos', () => {
  it('mostra a versao vigente e o contato', () => {
    TestBed.configureTestingModule({ imports: [Termos], providers: [provideRouter([])] });
    const tela = TestBed.createComponent(Termos);
    tela.detectChanges();
    const texto = (tela.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Termos de Uso do Piggu');
    expect(texto).toContain(VERSAO_DO_AVISO);
    expect(texto).toContain(CONTATO_PRIVACIDADE);
  });
});
