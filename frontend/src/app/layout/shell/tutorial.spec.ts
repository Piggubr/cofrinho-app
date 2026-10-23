import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { Tutorial } from '../../core/ui/tutorial';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { clicar } from '../../testing/tela';
import { TutorialDePrimeiroAcesso } from './tutorial';

/** Os baloes do tour: no centro sem alvo, presos ao botao quando ele esta na tela. */
describe('TutorialDePrimeiroAcesso', () => {
  let tutorial: Tutorial;
  let tela: ComponentFixture<TutorialDePrimeiroAcesso>;
  let pagina: HTMLElement;
  const alvos: HTMLElement[] = [];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TutorialDePrimeiroAcesso],
      providers: [{ provide: AuthService, useValue: new AuthFalso() }],
    });
    tutorial = TestBed.inject(Tutorial);
    tutorial.abrir();
    tela = TestBed.createComponent(TutorialDePrimeiroAcesso);
    pagina = tela.nativeElement;
    tela.detectChanges();
  });

  afterEach(() => {
    alvos.splice(0).forEach((alvo) => alvo.remove());
    localStorage.removeItem(`piggu_tutorial_${usuarioDeTeste().id}`);
  });

  /** Um botao com data-tour na posicao pedida (o jsdom nao calcula layout). */
  function alvo(tour: string, top: number, tag = 'button', left = 100): HTMLElement {
    const elemento = document.createElement(tag);
    elemento.dataset['tour'] = tour;
    const medido = tag.includes('-')
      ? elemento.appendChild(document.createElement('button'))
      : elemento;
    medido.getBoundingClientRect = () =>
      ({
        top,
        left,
        width: 40,
        height: 40,
        bottom: top + 40,
        right: left + 40,
        x: left,
        y: top,
      }) as DOMRect;
    document.body.appendChild(elemento);
    alvos.push(elemento);
    return medido;
  }

  function medir(): void {
    window.dispatchEvent(new Event('resize'));
    tela.detectChanges();
  }

  function irPara(id: string): void {
    tutorial.indice.set(tutorial.passos().findIndex((passo) => passo.id === id));
    tela.detectChanges();
  }

  it('sem alvo, o balao fica no centro com o fundo escuro e recebe o foco', async () => {
    await new Promise((pronto) => requestAnimationFrame(pronto));
    tela.detectChanges();

    expect(pagina.querySelector('.balao')?.classList).toContain('solto');
    expect(pagina.querySelector('.fundo')?.classList).toContain('escuro');
    expect(pagina.querySelector('.recorte')).toBeNull();
    expect(document.activeElement).toBe(pagina.querySelector('.balao'));
    expect(pagina.querySelector('.contagem')?.textContent).toBe(`1/${tutorial.passos().length}`);
  });

  it('alvo na metade de baixo: recorte em volta e balao acima dele', () => {
    alvo('lancar', 700);
    clicar(pagina, 'Próximo');
    medir();

    const recorte = pagina.querySelector<HTMLElement>('.recorte')!;
    expect(recorte.style.top).toBe('694px');
    expect(recorte.style.width).toBe('52px');
    const balao = pagina.querySelector<HTMLElement>('.balao')!;
    expect(balao.classList).toContain('acima');
    expect(balao.style.bottom).toBe(`${window.innerHeight - 694 + 14}px`);
  });

  it('alvo no alto: balao abaixo; componente aponta pelo botao que desenha', () => {
    alvo('busca', 10, 'app-busca-global', 0);
    irPara('busca');
    medir();

    const balao = pagina.querySelector<HTMLElement>('.balao')!;
    expect(balao.classList).toContain('abaixo');
    expect(balao.style.left).toBe('16px');
    expect(balao.style.top).toBe(`${10 - 6 + 52 + 14}px`);
  });

  it('tela que fica dentro do "Mais" aponta para ele e avisa', () => {
    alvo('mais', 700);
    irPara('relatorios');
    medir();

    expect(pagina.textContent).toContain('Fica dentro de "Mais".');
  });

  it('alvo fora da janela: rola ate ele e, se continuar fora, o balao fica no centro', () => {
    const fora = alvo('/familia', -200);
    fora.scrollIntoView = vi.fn();
    irPara('familia');
    medir();

    expect(fora.scrollIntoView).toHaveBeenCalledWith({ block: 'center' });
    expect(pagina.querySelector('.balao')?.classList).toContain('solto');
  });

  it('volta, pula, fecha no Esc e no fundo', () => {
    clicar(pagina, 'Próximo');
    tela.detectChanges();
    clicar(pagina, 'Voltar');
    tela.detectChanges();
    expect(tutorial.indice()).toBe(0);
    expect(pagina.textContent).not.toContain('Voltar');

    clicar(pagina, 'Pular');
    expect(tutorial.aberto()).toBe(false);

    tutorial.abrir();
    pagina.querySelector('.balao')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(tutorial.aberto()).toBe(false);

    tutorial.abrir();
    pagina.querySelector<HTMLElement>('.fundo')!.click();
    expect(tutorial.aberto()).toBe(false);
    expect(localStorage.getItem(`piggu_tutorial_${usuarioDeTeste().id}`)).toBe('1');
  });

  it('no ultimo passo, "Lançar meu primeiro gasto" fecha o tour e abre o +', () => {
    const lancou = vi.fn();
    tela.componentInstance.lancar.subscribe(lancou);
    irPara('assistente');

    expect(pagina.textContent).toContain('Para rever este tour, vá em Perfil.');
    clicar(pagina, 'Lançar meu primeiro gasto');

    expect(lancou).toHaveBeenCalledOnce();
    expect(tutorial.aberto()).toBe(false);
  });

  it('ao sair da tela para de medir no resize', () => {
    const medidas = vi.spyOn(tela.componentInstance['posicao'], 'set');
    tela.destroy();

    window.dispatchEvent(new Event('resize'));

    expect(medidas).not.toHaveBeenCalled();
  });
});
