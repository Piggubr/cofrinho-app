import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { MetaDoMes } from '../../core/api/models';
import { MinhasMetas, percentualConcluido, textoParaCompartilhar } from './minhas-metas';

const LER: MetaDoMes = {
  id: 'm1',
  titulo: 'Ler 2 livros',
  alvo: 2,
  atual: 1,
  mes: '2026-10',
  concluida: false,
};
const TREINAR: MetaDoMes = {
  id: 'm2',
  titulo: 'Treinar',
  alvo: 3,
  atual: 3,
  mes: '2026-10',
  concluida: true,
};

/** Minhas metas: anel por metas concluidas, +1 rapido, criar e compartilhar. */
describe('MinhasMetas', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MinhasMetas],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(metas: MetaDoMes[]) {
    const tela = TestBed.createComponent(MinhasMetas);
    tela.detectChanges();
    http.expectOne('/api/personal-goals').flush(metas);
    tela.detectChanges();
    return { tela, pagina: tela.nativeElement as HTMLElement };
  }

  function botao(pagina: HTMLElement, texto: string): HTMLButtonElement {
    return [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!;
  }

  it('o anel conta metas concluidas, com o porquinho em cima', () => {
    const { pagina } = abrir([LER, TREINAR]);
    expect(pagina.querySelector('[role="meter"]')!.getAttribute('aria-valuenow')).toBe('50');
    expect(pagina.querySelector('.porquinho')!.getAttribute('src')).toBe(
      'ilustracoes/piggu-love.svg',
    );
    expect(pagina.textContent).toContain('1 de 2 concluídas');
    expect(percentualConcluido([])).toBe(0);
  });

  it('sem metas mostra o convite e o compartilhar fica desligado', () => {
    const { pagina } = abrir([]);
    expect(pagina.textContent).toContain('Nenhuma meta criada.');
    expect(botao(pagina, 'Compartilhar').disabled).toBe(true);
  });

  it('+1 grava o passo a mais e atualiza a lista', () => {
    const { tela, pagina } = abrir([LER]);
    botao(pagina, '+1').click();

    const pedido = http.expectOne('/api/personal-goals/m1');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual({ titulo: 'Ler 2 livros', alvo: 2, atual: 2 });
    pedido.flush({ ...LER, atual: 2, concluida: true });
    tela.detectChanges();

    expect(pagina.querySelector('[role="meter"]')!.getAttribute('aria-valuenow')).toBe('100');
    expect(pagina.textContent).toContain('Você concluiu todas as metas do mês.');
  });

  it('criar recusa quantidade invalida e grava a meta certa', () => {
    const { tela, pagina } = abrir([]);
    botao(pagina, '+ Criar meta').click();
    tela.detectChanges();

    const preencher = (id: string, valor: string) => {
      const campo = pagina.querySelector<HTMLInputElement>(`#${id}`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    };
    preencher('tituloMeta', 'Correr');
    preencher('alvoMeta', '0');
    tela.detectChanges();
    botao(pagina, 'Salvar meta').click();
    tela.detectChanges();
    expect(pagina.querySelector('[role="alert"]')!.textContent).toContain('a partir de 1');
    http.expectNone('/api/personal-goals');

    preencher('alvoMeta', '5');
    tela.detectChanges();
    botao(pagina, 'Salvar meta').click();
    const pedido = http.expectOne('/api/personal-goals');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ titulo: 'Correr', alvo: 5, atual: 0 });
    pedido.flush({
      id: 'm9',
      titulo: 'Correr',
      alvo: 5,
      atual: 0,
      mes: '2026-10',
      concluida: false,
    });
    tela.detectChanges();
    expect(pagina.textContent).toContain('0 de 5');
  });

  it('o texto para compartilhar leva so titulo e progresso', () => {
    expect(textoParaCompartilhar([LER, TREINAR], 'outubro de 2026')).toBe(
      'Minhas metas de outubro de 2026:\n⏳ Ler 2 livros: 1 de 2\n✅ Treinar: 3 de 3',
    );
  });
});
