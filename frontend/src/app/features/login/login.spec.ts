import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { Login } from './login';

/** Entrada pelo Google, com o aceite dos termos na primeira vez. */
describe('Login', () => {
  let credencial: (idToken: string) => void;
  let botaoFalha: boolean;
  let sessaoGuardada: boolean;
  let entradas: [string, boolean, string | undefined][];
  let respostaDaEntrada: () => Promise<void>;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    botaoFalha = false;
    sessaoGuardada = false;
    entradas = [];
    respostaDaEntrada = async () => {};
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            restaurarSessao: async () => sessaoGuardada,
            entrarComGoogle: (idToken: string, lembrar: boolean, versao?: string) => {
              entradas.push([idToken, lembrar, versao]);
              return respostaDaEntrada();
            },
          },
        },
        {
          provide: GoogleIdentityService,
          useValue: {
            renderizarBotao: async (_: HTMLElement, aoReceber: (idToken: string) => void) => {
              if (botaoFalha) {
                throw new Error('sem rede');
              }
              credencial = aoReceber;
            },
          },
        },
      ],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  async function abrir() {
    const tela = TestBed.createComponent(Login);
    tela.detectChanges();
    await tela.whenStable();
    tela.detectChanges();
    return tela;
  }

  async function esperar(tela: Awaited<ReturnType<typeof abrir>>) {
    await new Promise((pronto) => setTimeout(pronto));
    tela.detectChanges();
  }

  it('quem ja tem sessao guardada vai direto para o painel', async () => {
    sessaoGuardada = true;
    await abrir();

    expect(navegar).toHaveBeenCalledWith(['/painel']);
  });

  it('o Google confirma, o backend aceita e a pessoa vai para o painel', async () => {
    const tela = await abrir();

    credencial('token-do-google');
    await esperar(tela);

    expect(entradas).toEqual([['token-do-google', true, undefined]]);
    expect(navegar).toHaveBeenCalledWith(['/painel']);
  });

  it('desmarcar "continuar conectado" vai junto na entrada', async () => {
    const tela = await abrir();
    const caixa = (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '.lembrar input[type=checkbox]',
    )!;
    caixa.checked = false;
    caixa.dispatchEvent(new Event('change'));

    credencial('token');
    await esperar(tela);

    expect(entradas[0][1]).toBe(false);
  });

  it('conta nova pede o aceite e so cria depois de marcado', async () => {
    respostaDaEntrada = async () => {
      if (entradas.length === 1) {
        throw new HttpErrorResponse({ status: 428, error: { codigo: 'TERMOS_NECESSARIOS' } });
      }
    };
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    credencial('token-novo');
    await esperar(tela);
    expect(pagina.textContent).toContain('Falta pouco para criar sua conta.');
    const criar = [...pagina.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Criar minha conta'),
    )!;
    expect(criar.disabled).toBe(true);

    const aceite = pagina.querySelector<HTMLInputElement>('.aceite input[type=checkbox]')!;
    aceite.checked = true;
    aceite.dispatchEvent(new Event('change'));
    tela.detectChanges();
    criar.click();
    await esperar(tela);

    expect(entradas[1]).toEqual(['token-novo', true, VERSAO_DO_AVISO]);
    expect(navegar).toHaveBeenCalledWith(['/painel']);
  });

  it('outro erro do backend aparece para a pessoa', async () => {
    respostaDaEntrada = async () => {
      throw new HttpErrorResponse({ status: 403, error: { erro: 'Conta desativada.' } });
    };
    const tela = await abrir();

    credencial('token');
    await esperar(tela);

    const alerta = (tela.nativeElement as HTMLElement).querySelector('[role=alert]');
    expect(alerta?.textContent).toContain('Conta desativada.');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('sem o script do Google, avisa para recarregar', async () => {
    botaoFalha = true;
    const tela = await abrir();
    await esperar(tela);

    expect(
      (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent,
    ).toContain('Recarregue a pagina');
  });
});
