import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONTATO_PRIVACIDADE } from '../../core/privacidade/aviso';
import { ExcluirConta } from './excluir-conta';

/** A pagina abre sem login e diz como excluir, pelo app ou por e-mail. */
describe('ExcluirConta', () => {
  it('mostra o caminho no app e o contato', () => {
    TestBed.configureTestingModule({ imports: [ExcluirConta], providers: [provideRouter([])] });
    const tela = TestBed.createComponent(ExcluirConta);
    tela.detectChanges();
    const texto = (tela.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Excluir minha conta');
    expect(texto).toContain(CONTATO_PRIVACIDADE);
  });
});
