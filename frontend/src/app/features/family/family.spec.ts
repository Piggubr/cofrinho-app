import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Familia } from '../../core/api/models';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Family } from './family';

const FAMILIA: Familia = {
  id: 'f1',
  nome: 'Familia de Ana',
  plano: 'GRATUITO',
  membros: [
    { id: 'u1', nome: 'Ana', email: 'ana@piggu.test', foto: null, papel: 'TITULAR' },
    { id: 'u2', nome: 'Bia', email: 'bia@piggu.test', foto: null, papel: 'MEMBRO' },
  ],
  convites: [{ id: 'c1', email: 'caio@piggu.test', venceEm: '2026-10-15T00:00:00Z' }],
};

/** O titular convida e remove; o membro so ve a familia e pode sair. */
describe('Family', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Family],
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

  function abrir(): HTMLElement {
    const tela = TestBed.createComponent(Family);
    tela.detectChanges();
    http.expectOne('/api/family').flush(FAMILIA);
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('titular ve membros e convites e convida pelo e-mail', () => {
    const pagina = abrir();
    expect(pagina.textContent).toContain('Bia');
    expect(pagina.textContent).toContain('caio@piggu.test');

    const campo = pagina.querySelector<HTMLInputElement>('#email-convite')!;
    campo.value = 'novo@piggu.test';
    campo.dispatchEvent(new Event('input'));
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Convidar')!.click();

    const pedido = http.expectOne('/api/family/invites');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ email: 'novo@piggu.test' });
    pedido.flush(FAMILIA);
  });

  it('e-mail invalido nem chega ao backend', () => {
    const pagina = abrir();
    const campo = pagina.querySelector<HTMLInputElement>('#email-convite')!;
    campo.value = 'sem-arroba';
    campo.dispatchEvent(new Event('input'));
    [...pagina.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Convidar')!.click();

    http.expectNone('/api/family/invites');
  });

  it('membro nao ve convites nem botao de remover', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    const pagina = abrir();

    expect(pagina.querySelector('#email-convite')).toBeNull();
    expect(pagina.textContent).not.toContain('Remover');
    expect(pagina.textContent).toContain('Sair da família');
  });
});
