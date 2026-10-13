import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, DadosDoCadastro } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { Login } from './login';

/** Entrada por e-mail e senha ou pelo Google, com o aceite dos termos na primeira vez. */
describe('Login', () => {
  let credencial: (idToken: string) => void;
  let botaoFalha: boolean;
  let sessaoGuardada: boolean;
  let entradas: [string, boolean, string | undefined][];
  let respostaDaEntrada: () => Promise<void>;
  let entradasComSenha: [string, string, boolean][];
  let cadastros: [DadosDoCadastro, boolean][];
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    botaoFalha = false;
    sessaoGuardada = false;
    entradas = [];
    respostaDaEntrada = async () => {};
    entradasComSenha = [];
    cadastros = [];
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
            entrarComSenha: (email: string, senha: string, lembrar: boolean) => {
              entradasComSenha.push([email, senha, lembrar]);
              return respostaDaEntrada();
            },
            cadastrar: (dados: DadosDoCadastro, lembrar: boolean) => {
              cadastros.push([dados, lembrar]);
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
    ).toContain('recarregue a pagina');
  });

  function preencher(tela: Awaited<ReturnType<typeof abrir>>, id: string, valor: string) {
    const campo = (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  function botao(tela: Awaited<ReturnType<typeof abrir>>, texto: string) {
    return [...(tela.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      b.textContent?.includes(texto),
    )!;
  }

  async function irParaCadastro(tela: Awaited<ReturnType<typeof abrir>>) {
    botao(tela, 'Criar conta').click();
    tela.detectChanges();
    await tela.whenStable();
  }

  async function enviar(tela: Awaited<ReturnType<typeof abrir>>) {
    (tela.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));
    await esperar(tela);
  }

  it('entra com e-mail e senha, sem passar pelo Google', async () => {
    const tela = await abrir();
    preencher(tela, 'email-entrar', ' ana@piggu.test ');
    preencher(tela, 'senha-entrar', 'cofrinho123');

    await enviar(tela);

    expect(entradasComSenha).toEqual([['ana@piggu.test', 'cofrinho123', true]]);
    expect(navegar).toHaveBeenCalledWith(['/painel']);
  });

  it('senha errada mostra o erro do backend e fica na tela', async () => {
    respostaDaEntrada = async () => {
      throw new HttpErrorResponse({ status: 401, error: { erro: 'E-mail ou senha incorretos.' } });
    };
    const tela = await abrir();
    preencher(tela, 'email-entrar', 'ana@piggu.test');
    preencher(tela, 'senha-entrar', 'errada123');

    await enviar(tela);

    expect(
      (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent,
    ).toContain('E-mail ou senha incorretos.');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('o botao do Google continua na tela como alternativa ao formulario', async () => {
    const tela = await abrir();
    const pagina: HTMLElement = tela.nativeElement;

    expect(pagina.querySelector('#senha-entrar')).not.toBeNull();
    expect(pagina.querySelector<HTMLElement>('.area-do-botao')?.hidden).toBe(false);
    botao(tela, 'Criar conta').click();
    tela.detectChanges();
    expect(pagina.querySelector('#confirmacao-cadastro')).not.toBeNull();
    expect(pagina.querySelector<HTMLElement>('.area-do-botao')?.hidden).toBe(false);
  });

  it('cadastro confere senha, confirmacao, celular, CPF (se vier) e aceite antes de chamar o backend', async () => {
    const tela = await abrir();
    await irParaCadastro(tela);
    preencher(tela, 'nome-cadastro', 'Ana');
    preencher(tela, 'email-cadastro', 'ana@piggu.test');
    preencher(tela, 'senha-cadastro', 'curta');
    preencher(tela, 'confirmacao-cadastro', 'curta');
    const alerta = () =>
      (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent ?? '';

    await enviar(tela);
    expect(alerta()).toContain('pelo menos 8');

    preencher(tela, 'senha-cadastro', 'cofrinho123');
    preencher(tela, 'confirmacao-cadastro', 'cofrinho124');
    await enviar(tela);
    expect(alerta()).toContain('nao sao iguais');

    preencher(tela, 'confirmacao-cadastro', 'cofrinho123');
    await enviar(tela);
    expect(alerta()).toContain('Informe seu celular');

    preencher(tela, 'telefone-cadastro', '(11) 99999-8888');
    await enviar(tela);
    expect(alerta()).toContain('Informe o número do seu documento (CPF)');

    preencher(tela, 'documento-cadastro', '529.982.247-26');
    await enviar(tela);
    expect(alerta()).toContain('CPF inválido');

    preencher(tela, 'documento-cadastro', '52998224725');
    expect(
      (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#documento-cadastro')!
        .value,
    ).toBe('529.982.247-25');
    await enviar(tela);
    expect(alerta()).toContain('aceite os Termos');
    expect(cadastros).toEqual([]);

    const aceite = (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '.aceite-do-cadastro input[type=checkbox]',
    )!;
    aceite.checked = true;
    aceite.dispatchEvent(new Event('change'));
    await enviar(tela);

    expect(cadastros).toEqual([
      [
        {
          nome: 'Ana',
          email: 'ana@piggu.test',
          senha: 'cofrinho123',
          pais: 'BR',
          documento: '52998224725',
          telefone: '+55 (11) 99999-8888',
        },
        true,
      ],
    ]);
    expect(navegar).toHaveBeenCalledWith(['/painel']);
  });

  it('e-mail que ja tem conta mostra a mensagem do backend', async () => {
    respostaDaEntrada = async () => {
      throw new HttpErrorResponse({
        status: 409,
        error: { erro: 'Este e-mail ja tem conta no Piggu.', codigo: 'EMAIL_JA_CADASTRADO' },
      });
    };
    const tela = await abrir();
    await irParaCadastro(tela);
    preencher(tela, 'nome-cadastro', 'Ana');
    preencher(tela, 'email-cadastro', 'ana@piggu.test');
    preencher(tela, 'senha-cadastro', 'cofrinho123');
    preencher(tela, 'confirmacao-cadastro', 'cofrinho123');
    preencher(tela, 'telefone-cadastro', '11999998888');
    preencher(tela, 'documento-cadastro', '52998224725');
    const aceite = (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '.aceite-do-cadastro input[type=checkbox]',
    )!;
    aceite.checked = true;
    aceite.dispatchEvent(new Event('change'));

    await enviar(tela);

    expect(
      (tela.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent,
    ).toContain('ja tem conta');
  });

  it('o campo do documento muda com o pais; onde nao se pede documento, some', async () => {
    const tela = await abrir();
    await irParaCadastro(tela);
    const pagina: HTMLElement = tela.nativeElement;
    const pais = pagina.querySelector<HTMLSelectElement>('#pais-cadastro')!;
    expect(pais.options.length).toBeGreaterThan(200);
    expect(pais.options[0].textContent).toContain('Brasil');
    const escolher = async (codigo: string) => {
      pais.value = codigo;
      pais.dispatchEvent(new Event('change'));
      tela.detectChanges();
      await tela.whenStable();
    };

    await escolher('US');
    expect(pagina.querySelector('#documento-cadastro')).toBeNull();

    await escolher('PT');
    expect(pagina.querySelector('label[for=documento-cadastro]')?.textContent).toContain('NIF');
    preencher(tela, 'nome-cadastro', 'Rita');
    preencher(tela, 'email-cadastro', 'rita@piggu.test');
    preencher(tela, 'senha-cadastro', 'cofrinho123');
    preencher(tela, 'confirmacao-cadastro', 'cofrinho123');
    preencher(tela, 'telefone-cadastro', '912 345 678');
    const aceite = pagina.querySelector<HTMLInputElement>(
      '.aceite-do-cadastro input[type=checkbox]',
    )!;
    aceite.checked = true;
    aceite.dispatchEvent(new Event('change'));
    preencher(tela, 'documento-cadastro', '123456780');
    await enviar(tela);
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('NIF inválido');

    preencher(tela, 'documento-cadastro', '123 456 789');
    await enviar(tela);
    expect(cadastros[0][0]).toEqual({
      nome: 'Rita',
      email: 'rita@piggu.test',
      senha: 'cofrinho123',
      pais: 'PT',
      documento: '123456789',
      telefone: '+351 912 345 678',
    });

    await escolher('US');
    await enviar(tela);
    expect(cadastros[1][0]).toEqual(expect.objectContaining({ pais: 'US', documento: undefined }));
  });
});
