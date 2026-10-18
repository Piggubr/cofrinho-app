import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, digitar } from '../../testing/tela';
import { Assistente } from './assistente';

/** Assistente de IA: Premium, autorizacao no primeiro uso e a conversa so no aparelho. */
describe('Assistente', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    localStorage.clear();
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Assistente],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(autorizado = true) {
    const tela = TestBed.createComponent(Assistente);
    tela.detectChanges();
    (tela.nativeElement.querySelector('.abrir') as HTMLButtonElement).click();
    tela.detectChanges();
    if (auth.ehPremium()) {
      http
        .expectOne('/api/assistant')
        .flush({ habilitado: true, autorizado, limite: 150, restantes: 150 });
      tela.detectChanges();
    }
    return tela;
  }

  function botao(pagina: HTMLElement, texto: string): HTMLButtonElement {
    return [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!;
  }

  it('no gratuito mostra o convite do Premium, sem chamar a IA', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO' }));
    const pagina: HTMLElement = abrir().nativeElement;
    expect(pagina.querySelector('a[href="/plano"]')).not.toBeNull();
    expect(pagina.querySelector('#pergunta-ao-assistente')).toBeNull();
  });

  it('primeiro uso: pede a autorizacao e a manda junto com a primeira pergunta', () => {
    const tela = abrir(false);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Google (Gemini)');
    expect(pagina.querySelector('#pergunta-ao-assistente')).toBeNull();

    botao(pagina, 'Autorizo').click();
    tela.detectChanges();
    botao(pagina, 'Quanto gastei este mês?').click();
    const pedido = http.expectOne('/api/assistant/ask');
    expect(pedido.request.body).toEqual({
      pergunta: 'Quanto gastei este mês?',
      historico: [],
      moeda: 'EUR',
      autorizoIa: true,
      versaoDoAviso: VERSAO_DO_AVISO,
    });
    pedido.flush({ texto: 'Você gastou R$ 1.200,00.', tela: 'relatorios', restantes: 149 });
    tela.detectChanges();

    expect(pagina.textContent).toContain('Você gastou R$ 1.200,00.');
    expect(pagina.querySelector('a[href="/relatorios"]')?.textContent).toContain(
      'Abrir Relatórios',
    );
    expect(pagina.textContent).toContain('149 de 150 perguntas');
  });

  it('segunda pergunta leva o historico e nao repete a autorizacao', async () => {
    const tela = abrir();
    // O ngModel do formulario escreve o valor inicial depois do ciclo.
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#pergunta-ao-assistente', 'E com mercado?');
    (pagina.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectOne('/api/assistant/ask').flush({ texto: 'R$ 300,00.', tela: null, restantes: 149 });
    tela.detectChanges();

    digitar(pagina, '#pergunta-ao-assistente', 'E no mes passado?');
    (pagina.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const pedido = http.expectOne('/api/assistant/ask');
    expect(pedido.request.body.historico).toEqual([
      { pessoa: true, texto: 'E com mercado?' },
      { pessoa: false, texto: 'R$ 300,00.' },
    ]);
    expect(pedido.request.body.autorizoIa).toBeUndefined();
    pedido.flush({ texto: 'R$ 280,00.', tela: null, restantes: 148 });
  });

  it('erro devolve a pergunta ao campo e mostra a mensagem', async () => {
    const tela = abrir();
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;

    digitar(pagina, '#pergunta-ao-assistente', 'Quanto gastei?');
    (pagina.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/assistant/ask')
      .flush(
        { erro: 'Você já fez as 150 perguntas deste mês.', codigo: 'NEGOCIO', campos: [] },
        { status: 429, statusText: 'Too Many Requests' },
      );
    tela.detectChanges();
    await tela.whenStable();

    expect(pagina.querySelector('[role="alert"]')?.textContent).toContain('150 perguntas');
    expect((pagina.querySelector('#pergunta-ao-assistente') as HTMLInputElement).value).toBe(
      'Quanto gastei?',
    );
  });

  async function perguntar(tela: ReturnType<typeof abrir>, pergunta: string, resposta: string) {
    const pagina: HTMLElement = tela.nativeElement;
    digitar(pagina, '#pergunta-ao-assistente', pergunta);
    (pagina.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const pedido = http.expectOne('/api/assistant/ask');
    pedido.flush({ texto: resposta, tela: null, restantes: 149 });
    tela.detectChanges();
    return pedido;
  }

  it('a conversa volta ao abrir de novo e "Nova conversa" apaga do aparelho', async () => {
    const primeira = abrir();
    await primeira.whenStable();
    await perguntar(primeira, 'E com mercado?', 'R$ 300,00.');
    primeira.destroy();

    const segunda = abrir();
    const pagina: HTMLElement = segunda.nativeElement;
    expect(pagina.textContent).toContain('E com mercado?');
    expect(pagina.textContent).toContain('R$ 300,00.');

    botao(pagina, 'Nova conversa').click();
    segunda.detectChanges();
    expect(pagina.textContent).not.toContain('R$ 300,00.');
    segunda.destroy();

    const terceira = abrir();
    expect((terceira.nativeElement as HTMLElement).textContent).not.toContain('R$ 300,00.');
  });

  it('manda para a IA so as ultimas 10 falas da conversa guardada', async () => {
    const falas = Array.from({ length: 30 }, (_, i) => ({
      pessoa: i % 2 === 0,
      texto: `fala ${i}`,
      em: new Date().toISOString(),
    }));
    localStorage.setItem(
      'piggu_conversa_do_assistente',
      JSON.stringify({ usuario: auth.usuario()!.id, falas }),
    );
    const tela = abrir();
    await tela.whenStable();

    const pedido = await perguntar(tela, 'E agora?', 'Certo.');
    expect(pedido.request.body.historico).toHaveLength(10);
    expect(pedido.request.body.historico[0]).toEqual({ pessoa: true, texto: 'fala 20' });
  });
});
