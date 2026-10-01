import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Profile } from './profile';

/** Preferencias de moeda: gravadas no backend e refletidas na sessao na hora. */
describe('Profile', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('salva as moedas escolhidas e atualiza a sessao com a resposta', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([
      { codigo: 'BRL', nome: 'Real brasileiro' },
      { codigo: 'EUR', nome: 'Euro' },
      { codigo: 'USD', nome: 'Dolar americano' },
    ]);
    tela.detectChanges();
    await tela.whenStable();

    const pagina: HTMLElement = tela.nativeElement;
    const moeda = pagina.querySelector<HTMLSelectElement>('#moeda')!;
    moeda.value = 'USD';
    moeda.dispatchEvent(new Event('change'));
    const cotacao = pagina.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    cotacao.click();
    tela.detectChanges();

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.includes('Salvar preferências'))!
      .click();

    const pedido = http.expectOne('/api/auth/me/preferences');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({
      moeda: 'USD',
      moedaConversao: 'BRL',
      mostrarCotacao: false,
    });

    const salvo = usuarioDeTeste({
      preferencias: { moeda: 'USD', moedaConversao: 'BRL', mostrarCotacao: false },
    });
    pedido.flush(salvo);
    tela.detectChanges();

    expect(auth.usuario()?.preferencias.moeda).toBe('USD');
    expect(pagina.textContent).toContain('Preferências salvas.');
  });

  it('erro do backend aparece para a pessoa e nada muda na sessao', async () => {
    const tela = TestBed.createComponent(Profile);
    tela.detectChanges();
    http.expectOne('/api/exchange-rate/currencies').flush([{ codigo: 'EUR', nome: 'Euro' }]);
    tela.detectChanges();

    [...tela.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Salvar preferências'))!
      .click();
    http
      .expectOne('/api/auth/me/preferences')
      .flush(
        { erro: 'Moeda desconhecida: XYZ.', codigo: 'BusinessException', campos: [], momento: '' },
        { status: 422, statusText: 'Unprocessable' },
      );
    tela.detectChanges();

    expect(tela.nativeElement.textContent).toContain('Moeda desconhecida');
    expect(auth.usuario()?.preferencias.moeda).toBe('EUR');
  });
});
