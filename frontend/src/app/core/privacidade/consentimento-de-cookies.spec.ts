import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AvisoDeCookies } from './aviso-de-cookies';
import { ConsentimentoDeCookies, VERSAO_DOS_COOKIES } from './consentimento-de-cookies';

describe('ConsentimentoDeCookies', () => {
  beforeEach(() => localStorage.clear());

  function abrir() {
    TestBed.configureTestingModule({ imports: [AvisoDeCookies], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(AvisoDeCookies);
    fixture.detectChanges();
    return fixture;
  }

  it('pergunta na primeira visita e recusar e tao facil quanto aceitar', () => {
    const fixture = abrir();
    const botoes = [...fixture.nativeElement.querySelectorAll('button')].map((b: HTMLElement) => b.textContent!.trim());
    expect(botoes).toEqual(['Só os essenciais', 'Aceitar']);

    fixture.nativeElement.querySelector('button').click();
    fixture.detectChanges();

    const consentimento = TestBed.inject(ConsentimentoDeCookies);
    expect(consentimento.analiseLiberada()).toBe(false);
    expect(consentimento.perguntar()).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Cookies no Piggu');
    expect(JSON.parse(localStorage.getItem('piggu.cookies')!)).toMatchObject({ analise: false, versao: VERSAO_DOS_COOKIES });
  });

  it('lembra o aceite, e a escolha muda quando a pessoa reabre', () => {
    localStorage.setItem('piggu.cookies', JSON.stringify({ versao: VERSAO_DOS_COOKIES, analise: true, em: '2026-10-19' }));
    const fixture = abrir();
    const consentimento = TestBed.inject(ConsentimentoDeCookies);
    expect(consentimento.analiseLiberada()).toBe(true);
    expect(fixture.nativeElement.textContent).not.toContain('Cookies no Piggu');

    consentimento.reabrir();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cookies no Piggu');
  });

  it('escolha de uma versao antiga dos cookies nao vale: pergunta de novo', () => {
    localStorage.setItem('piggu.cookies', JSON.stringify({ versao: '2000-01-01', analise: true, em: '2000-01-01' }));
    abrir();
    const consentimento = TestBed.inject(ConsentimentoDeCookies);
    expect(consentimento.perguntar()).toBe(true);
    expect(consentimento.analiseLiberada()).toBe(false);
  });
});
