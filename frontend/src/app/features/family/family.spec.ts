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
  convites: [
    {
      id: 'c1',
      email: 'caio@piggu.test',
      venceEm: '2026-10-15T00:00:00Z',
      familia: 'Familia de Ana',
    },
  ],
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

  function abrir(recebidos: Familia['convites'] = []): HTMLElement {
    const tela = TestBed.createComponent(Family);
    tela.detectChanges();
    http.expectOne('/api/family').flush(FAMILIA);
    http.expectOne('/api/family/invites/mine').flush(recebidos);
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
    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Convidar')!
      .click();

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
    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Convidar')!
      .click();

    http.expectNone('/api/family/invites');
  });

  it('convite recebido de outra familia aparece com o nome dela', () => {
    const pagina = abrir([
      {
        id: 'c9',
        email: 'titular@piggu.test',
        venceEm: '2026-10-15T00:00:00Z',
        familia: 'Familia Souza',
      },
    ]);
    expect(pagina.textContent).toContain('Convite para entrar na Familia Souza');
  });

  it('titular torna o membro parceiro depois de confirmar', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const pagina = abrir();

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Tornar parceiro')!
      .click();

    const pedido = http.expectOne('/api/family/members/u2/role');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ papel: 'PARCEIRO' });
    pedido.flush({
      ...FAMILIA,
      membros: [FAMILIA.membros[0], { ...FAMILIA.membros[1], papel: 'PARCEIRO' }],
    });
  });

  it('parceiro nao convida, nao remove nem muda papel', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'PARCEIRO' }));
    const pagina = abrir();

    expect(pagina.querySelector('#email-convite')).toBeNull();
    expect(pagina.textContent).not.toContain('Remover');
    expect(pagina.textContent).not.toContain('Tornar parceiro');
    expect(pagina.textContent).toContain('Sair da família');
  });

  it('historico junta dinheiro e pessoas, do mais novo ao mais velho, com o nome de quem fez', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'PARCEIRO' }));
    const tela = TestBed.createComponent(Family);
    tela.detectChanges();
    http.expectOne('/api/family').flush(FAMILIA);
    http.expectOne('/api/family/invites/mine').flush([]);
    tela.detectChanges();
    const pagina: HTMLElement = tela.nativeElement;

    [...pagina.querySelectorAll('button')]
      .find((b) => b.textContent?.trim() === 'Ver histórico')!
      .click();
    http
      .expectOne((r) => r.url === '/api/history')
      .flush([
        {
          id: 7,
          autor: 'u2',
          acao: 'APAGOU',
          entidade: 'gasto',
          entidadeId: 'g1',
          antes: '2026-09-10 · Pao · Lazer · 15.00',
          depois: null,
          quando: '2026-10-10T12:00:00Z',
        },
      ]);
    http
      .expectOne((r) => r.url === '/api/family/history')
      .flush([
        {
          id: 3,
          autor: 'u1',
          acao: 'MUDOU_PAPEL',
          entidade: 'pessoa',
          entidadeId: 'u2',
          antes: 'Bia · MEMBRO',
          depois: 'Bia · PARCEIRO',
          quando: '2026-10-09T12:00:00Z',
        },
      ]);
    tela.detectChanges();

    const linhas = [...pagina.querySelectorAll('.historico .linha-titulo')].map((l) =>
      l.textContent?.trim(),
    );
    expect(linhas).toEqual(['Bia apagou gasto', 'Ana mudou o papel de']);
    expect(pagina.textContent).toContain('Bia · MEMBRO → Bia · PARCEIRO');
  });

  it('membro nao ve o historico', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    expect(abrir().textContent).not.toContain('Ver histórico');
  });

  it('membro nao ve convites nem botao de remover', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    const pagina = abrir();

    expect(pagina.querySelector('#email-convite')).toBeNull();
    expect(pagina.textContent).not.toContain('Remover');
    expect(pagina.textContent).toContain('Sair da família');
  });
});
