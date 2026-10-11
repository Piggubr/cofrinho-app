import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { GoogleIdentityService } from './google-identity.service';

/** O cliente do Google so e baixado quando o botao precisa aparecer. */
describe('GoogleIdentityService', () => {
  let opcoes: { client_id: string; callback: (r: { credential: string }) => void } | undefined;
  let desenhados: HTMLElement[];
  let semAutoSelecao: number;

  beforeEach(() => {
    opcoes = undefined;
    desenhados = [];
    semAutoSelecao = 0;
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'cliente-123' } },
      ],
    });
  });

  afterEach(() => {
    delete window.google;
    document.head.querySelectorAll('script[src*="accounts.google.com"]').forEach((s) => s.remove());
  });

  function googleFalso() {
    return {
      accounts: {
        id: {
          initialize: (o: typeof opcoes) => (opcoes = o),
          renderButton: (elemento: HTMLElement) => desenhados.push(elemento),
          prompt: () => {},
          disableAutoSelect: () => semAutoSelecao++,
        },
      },
    };
  }

  it('com o cliente ja carregado, desenha o botao e repassa a credencial', async () => {
    window.google = googleFalso();
    const destino = document.createElement('div');
    destino.appendChild(document.createElement('span'));
    const recebidas: string[] = [];

    await TestBed.inject(GoogleIdentityService).renderizarBotao(destino, (t) => recebidas.push(t));
    opcoes!.callback({ credential: 'id-token' });

    expect(opcoes!.client_id).toBe('cliente-123');
    expect(desenhados).toEqual([destino]);
    expect(destino.childElementCount).toBe(0);
    expect(recebidas).toEqual(['id-token']);
  });

  it('sem cliente, baixa o script uma vez so', async () => {
    const servico = TestBed.inject(GoogleIdentityService);
    const primeiro = servico.renderizarBotao(document.createElement('div'), () => {});
    const segundo = servico.renderizarBotao(document.createElement('div'), () => {});
    const scripts = document.head.querySelectorAll<HTMLScriptElement>(
      'script[src*="accounts.google.com"]',
    );
    expect(scripts.length).toBe(1);

    window.google = googleFalso();
    scripts[0].onload!(new Event('load'));
    await Promise.all([primeiro, segundo]);

    expect(desenhados.length).toBe(2);
  });

  it('script que nao carrega vira erro para a tela de login', async () => {
    const servico = TestBed.inject(GoogleIdentityService);
    const tentativa = servico.renderizarBotao(document.createElement('div'), () => {});
    document.head.querySelector<HTMLScriptElement>('script[src*="accounts.google.com"]')!.onerror!(
      new Event('error'),
    );

    await expect(tentativa).rejects.toThrow('Nao consegui carregar o login do Google.');
  });

  it('script que carrega sem o cliente tambem e erro', async () => {
    const servico = TestBed.inject(GoogleIdentityService);
    const tentativa = servico.renderizarBotao(document.createElement('div'), () => {});
    document.head.querySelector<HTMLScriptElement>('script[src*="accounts.google.com"]')!.onload!(
      new Event('load'),
    );

    await expect(tentativa).rejects.toThrow('incompleto');
  });

  it('ao sair, desliga a reentrada automatica; sem Google, nao falha', async () => {
    window.google = googleFalso();
    await TestBed.inject(GoogleIdentityService).esquecerConta();
    expect(semAutoSelecao).toBe(1);
  });

  it('esquecer a conta sem o script carregado nao quebra a saida', async () => {
    const servico = TestBed.inject(GoogleIdentityService);
    const saida = servico.esquecerConta();
    document.head.querySelector<HTMLScriptElement>('script[src*="accounts.google.com"]')!.onerror!(
      new Event('error'),
    );

    await expect(saida).resolves.toBeUndefined();
  });
});
