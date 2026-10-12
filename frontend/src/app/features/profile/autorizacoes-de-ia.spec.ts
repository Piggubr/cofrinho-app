import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { AutorizacoesDeIa } from './autorizacoes-de-ia';

/** Retirar a autorizacao da IA tem de ser tao facil quanto dar (LGPD art. 8, par. 5). */
describe('AutorizacoesDeIa', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AutorizacoesDeIa],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function clicar(tela: ReturnType<typeof TestBed.createComponent>, texto: string): void {
    const pagina: HTMLElement = tela.nativeElement;
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!.click();
    tela.detectChanges();
  }

  it('retira a da foto da nota e confirma', () => {
    const tela = TestBed.createComponent(AutorizacoesDeIa);
    tela.detectChanges();

    clicar(tela, 'foto da nota');
    const pedido = http.expectOne('/api/receipts/consent');
    expect(pedido.request.method).toBe('DELETE');
    pedido.flush(null, { status: 204, statusText: 'No Content' });
    tela.detectChanges();

    expect(tela.nativeElement.textContent).toContain('só vai ao Google se você autorizar de novo');
  });

  it('retira a do assistente e mostra o erro se o servidor recusar', () => {
    const tela = TestBed.createComponent(AutorizacoesDeIa);
    tela.detectChanges();

    clicar(tela, 'do assistente');
    const pedido = http.expectOne('/api/assistant/consent');
    expect(pedido.request.method).toBe('DELETE');
    pedido.flush({ mensagem: 'Fora do ar' }, { status: 503, statusText: 'Indisponivel' });
    tela.detectChanges();

    expect(tela.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
