import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, digitar } from '../../testing/tela';
import { PrimeirosPassos } from './primeiros-passos';

const CHAVE = `piggu_guia_${usuarioDeTeste().id}`;

/** Guia de primeiro uso: moeda, limite e primeiro gasto, e some quando a conta ja anda. */
describe('PrimeirosPassos', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    localStorage.removeItem(CHAVE);
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [PrimeirosPassos],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CHAVE);
  });

  function abrir(temLimite = false, temGasto = false) {
    const tela = TestBed.createComponent(PrimeirosPassos);
    tela.componentRef.setInput('temLimite', temLimite);
    tela.componentRef.setInput('temGasto', temGasto);
    tela.detectChanges();
    return tela;
  }

  it('conta nova ve os tres passos, com o limite e o gasto levando as telas', () => {
    const pagina: HTMLElement = abrir().nativeElement;
    expect(pagina.textContent).toContain('0 de 3');
    const links = [...pagina.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/orcamentos', '/gastos', '/gastos#importar']);
  });

  it('escolher a moeda grava a preferencia e conta o passo', () => {
    const tela = abrir(true);
    const pagina: HTMLElement = tela.nativeElement;
    digitar(pagina, '#moeda-do-guia', 'BRL');
    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Usar esta'))!
      .click();
    const pedido = http.expectOne('/api/auth/me/preferences');
    expect(pedido.request.body).toEqual({
      moeda: 'BRL',
      moedaConversao: 'BRL',
      mostrarCotacao: true,
    });
    pedido.flush(
      usuarioDeTeste({
        preferencias: { moeda: 'BRL', moedaConversao: 'BRL', mostrarCotacao: true },
      }),
    );
    tela.detectChanges();
    expect(pagina.textContent).toContain('2 de 3');
    expect(pagina.textContent).toContain('(BRL)');
    expect(JSON.parse(localStorage.getItem(CHAVE)!)).toEqual({ moeda: true });
  });

  it('pular esconde o guia e fica lembrado', () => {
    const tela = abrir();
    [...tela.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Pular'))!
      .click();
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('.guia')).toBeNull();
    expect(abrir().nativeElement.querySelector('.guia')).toBeNull();
  });

  it('conta que ja tem limite e gasto nao ve o guia', () => {
    expect(abrir(true, true).nativeElement.querySelector('.guia')).toBeNull();
  });
});
