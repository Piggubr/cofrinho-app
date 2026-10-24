import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, DadosDoCadastro } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { GoogleNoApp } from '../../core/auth/google-no-app';
import { Atalhos } from '../../core/nativo/atalhos';
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
  let cadastros: DadosDoCadastro[];
  let navegar: ReturnType<typeof vi.spyOn>;
  let noApp: boolean;
  let aberturas: boolean[];
  let trocas: string[];
  let atalhosLigados: number;

  beforeEach(() => {
    credencial = undefined!;
    noApp = false;
    aberturas = [];
    trocas = [];
    atalhosLigados = 0;
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
            cadastrar: (dados: DadosDoCadastro) => {
              cadastros.push(dados);
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
        {
          provide: GoogleNoApp,
          useValue: {
            get disponivel() {
              return noApp;
            },
            abrir: async (lembrar: boolean) => {
              aberturas.push(lembrar);
            },
            concluir: async (codigo: string) => {
              trocas.push(codigo);
              return { idToken: 'token-do-app', lembrar: false };
            },
          },
        },
        { provide: Atalhos, useValue: { ligar: async () => atalhosLigados++ } },
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
    preencher(tela, 'nascimento-cadastro', '1990-05-20');
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
      {
        nome: 'Ana',
        email: 'ana@piggu.test',
        senha: 'cofrinho123',
        pais: 'BR',
        documento: '52998224725',
        telefone: '+55 (11) 99999-8888',
        nascimento: '1990-05-20',
      },
    ]);
    // Sem sessao: pede para confirmar o e-mail e entrar, a mesma coisa para e-mail repetido.
    expect(navegar).not.toHaveBeenCalled();
    expect(
      (tela.nativeElement as HTMLElement).querySelector('[role=status]')?.textContent,
    ).toContain('Mandamos um link para ana@piggu.test');
  });

  it('erro do backend no cadastro aparece na tela', async () => {
    respostaDaEntrada = async () => {
      throw new HttpErrorResponse({
        status: 422,
        error: { erro: 'Confira o celular.', codigo: 'TELEFONE_INVALIDO' },
      });
    };
    const tela = await abrir();
    await irParaCadastro(tela);
    preencher(tela, 'nome-cadastro', 'Ana');
    preencher(tela, 'nascimento-cadastro', '1990-05-20');
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
    ).toContain('Confira o celular');
  });

  it('quem mora fora tambem informa o CPF; o DDI segue o pais', async () => {
    const tela = await abrir();
    await irParaCadastro(tela);
    const pagina: HTMLElement = tela.nativeElement;
    const pais = pagina.querySelector<HTMLSelectElement>('#pais-cadastro')!;
    expect(pais.options.length).toBeGreaterThan(200);
    expect(pais.options[0].textContent).toContain('Brasil');
    pais.value = 'PT';
    pais.dispatchEvent(new Event('change'));
    tela.detectChanges();
    await tela.whenStable();

    expect(pagina.querySelector('label[for=documento-cadastro]')?.textContent).toContain('CPF');
    preencher(tela, 'nome-cadastro', 'Rita');
    preencher(tela, 'nascimento-cadastro', '1990-05-20');
    preencher(tela, 'email-cadastro', 'rita@piggu.test');
    preencher(tela, 'senha-cadastro', 'cofrinho123');
    preencher(tela, 'confirmacao-cadastro', 'cofrinho123');
    preencher(tela, 'telefone-cadastro', '912 345 678');
    const aceite = pagina.querySelector<HTMLInputElement>(
      '.aceite-do-cadastro input[type=checkbox]',
    )!;
    aceite.checked = true;
    aceite.dispatchEvent(new Event('change'));
    await enviar(tela);
    expect(pagina.querySelector('[role=alert]')?.textContent).toContain(
      'Informe o número do seu documento (CPF)',
    );

    preencher(tela, 'documento-cadastro', '529.982.247-25');
    await enviar(tela);
    expect(cadastros[0]).toEqual({
      nome: 'Rita',
      email: 'rita@piggu.test',
      senha: 'cofrinho123',
      pais: 'PT',
      documento: '52998224725',
      telefone: '+351 912 345 678',
      nascimento: '1990-05-20',
    });
  });

  describe('no app instalado', () => {
    beforeEach(() => {
      noApp = true;
    });

    it('o botao abre o Google no navegador do celular, sem o botao da janela do Google', async () => {
      const tela = await abrir();
      const google = [...(tela.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
        b.textContent?.includes('Fazer login com o Google'),
      )!;

      google.click();
      await esperar(tela);

      expect(aberturas).toEqual([true]);
      expect(credencial).toBeUndefined();
      expect(atalhosLigados).toBe(1);
    });

    it('a volta pelo piggu:// troca o codigo e entra com o ID token', async () => {
      await TestBed.inject(Router).navigateByUrl('/?google=codigo-1');
      const tela = await abrir();
      await esperar(tela);

      expect(trocas).toEqual(['codigo-1']);
      expect(entradas).toEqual([['token-do-app', false, undefined]]);
      expect(navegar).toHaveBeenCalledWith(['/painel']);
    });
  });
});
