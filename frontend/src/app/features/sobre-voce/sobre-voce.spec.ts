import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, digitar } from '../../testing/tela';
import { SobreVoce } from './sobre-voce';

/** Apelido e nascimento de quem entrou pelo Google ou tem conta antiga. */
describe('SobreVoce', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [SobreVoce],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrir() {
    const tela = TestBed.createComponent(SobreVoce);
    tela.detectChanges();
    await tela.whenStable();
    tela.detectChanges();
    return tela;
  }

  async function enviar(tela: Awaited<ReturnType<typeof abrir>>): Promise<void> {
    tela.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await tela.whenStable();
    tela.detectChanges();
  }

  it('comeca com o primeiro nome e pede o nascimento de quem nao tem', async () => {
    const pagina: HTMLElement = (await abrir()).nativeElement;
    expect(pagina.querySelector<HTMLInputElement>('#apelido')!.value).toBe('Titular');
    expect(pagina.querySelector('#nascimento')).not.toBeNull();
  });

  it('sem apelido ou sem data, avisa e nao manda', async () => {
    const tela = await abrir();
    digitar(tela.nativeElement, '#apelido', '  ');
    await enviar(tela);
    expect(tela.nativeElement.textContent).toContain('Conte como podemos te chamar.');

    digitar(tela.nativeElement, '#apelido', 'Ti');
    await enviar(tela);
    expect(tela.nativeElement.textContent).toContain('Informe sua data de nascimento.');
    expect(http.match(() => true)).toEqual([]);
  });

  it('salva apelido e nascimento, atualiza a conta e vai ao painel', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const tela = await abrir();
    digitar(tela.nativeElement, '#apelido', ' Ti ');
    digitar(tela.nativeElement, '#nascimento', '1990-05-10');
    await enviar(tela);

    const pedido = http.expectOne('/api/me/sobre-voce');
    expect(pedido.request.body).toEqual({ apelido: 'Ti', nascimento: '1990-05-10' });
    pedido.flush(usuarioDeTeste({ apelido: 'Ti', nascimento: '1990-05-10' }));
    await tela.whenStable();

    expect(auth.usuario()?.apelido).toBe('Ti');
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/painel'));
    expect(auth.renovacoes()).toBe(1);
  });

  it('quem entrou pelo Google sem CPF e celular completa o cadastro antes do painel', async () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.usuario.set(
      usuarioDeTeste({ cpfInformado: false, celularInformado: false, cadastroCompleto: false }),
    );
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('complete seu cadastro');
    digitar(pagina, '#nascimento', '1990-05-10');

    await enviar(tela);
    expect(pagina.textContent).toContain('Informe seu CPF.');
    digitar(pagina, '#cpf', '52998224724');
    await enviar(tela);
    expect(pagina.textContent).toContain('CPF inválido');
    digitar(pagina, '#cpf', '52998224725');
    expect(pagina.querySelector<HTMLInputElement>('#cpf')!.value).toBe('529.982.247-25');
    await enviar(tela);
    expect(pagina.textContent).toContain('Informe seu celular.');
    expect(http.match(() => true)).toEqual([]);

    digitar(pagina, '#telefone', '(11) 99999-8888');
    await enviar(tela);
    const pedido = http.expectOne('/api/me/sobre-voce');
    expect(pedido.request.body).toEqual({
      apelido: 'Titular',
      nascimento: '1990-05-10',
      cpf: '52998224725',
      telefone: '+55 (11) 99999-8888',
    });
    pedido.flush(usuarioDeTeste({ cadastroCompleto: true }));
    await vi.waitFor(() => expect(navegar).toHaveBeenCalledWith('/painel'));
    expect(auth.renovacoes()).toBe(1);
  });

  it('quem ja tem nascimento so manda o apelido; erro da API aparece', async () => {
    auth.usuario.set(usuarioDeTeste({ apelido: 'Tita', nascimento: '1990-05-10' }));
    const tela = await abrir();
    expect(tela.nativeElement.querySelector('#nascimento')).toBeNull();
    expect(tela.nativeElement.querySelector('#apelido').value).toBe('Tita');
    await enviar(tela);

    const pedido = http.expectOne('/api/me/sobre-voce');
    expect(pedido.request.body).toEqual({ apelido: 'Tita', nascimento: undefined });
    pedido.flush({ erro: 'Tente de novo.' }, { status: 500, statusText: 'Erro' });
    await tela.whenStable();
    tela.detectChanges();
    expect(tela.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('sair encerra a sessao', async () => {
    const sair = vi.spyOn(auth, 'sair');
    const tela = await abrir();
    [...tela.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === 'Sair')!
      .click();
    expect(sair).toHaveBeenCalled();
  });
});
