import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { TelaDeErro } from './tela-de-erro';

describe('TelaDeErro', () => {
  afterEach(() => vi.unstubAllGlobals());

  function abrir(tipo: 'nao-encontrada' | 'servidor'): HTMLElement {
    TestBed.configureTestingModule({ imports: [TelaDeErro], providers: [provideRouter([])] });
    const tela = TestBed.createComponent(TelaDeErro);
    tela.componentRef.setInput('tipo', tipo);
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('404: diz que a pagina nao existe e leva ao inicio', () => {
    const tela = abrir('nao-encontrada');
    expect(tela.textContent).toContain('404');
    expect(tela.querySelector('h1')?.textContent).toContain('Essa página fugiu do cofrinho');
    expect(tela.querySelector('a')?.getAttribute('href')).toBe('/painel');
  });

  it('500: pede desculpa e oferece tentar de novo', () => {
    const tela = abrir('servidor');
    expect(tela.textContent).toContain('500');
    expect(tela.querySelector('h1')?.textContent).toContain('O Piggu tropeçou');
    expect(tela.querySelector('button')?.textContent).toContain('Tentar de novo');
  });

  it('sem internet: nao culpa o servidor', () => {
    vi.stubGlobal('navigator', { ...navigator, onLine: false });
    const tela = abrir('servidor');
    expect(tela.textContent).not.toContain('500');
    expect(tela.querySelector('h1')?.textContent).toContain('Você está sem internet');
  });

  it('endereco que nao existe abre a 404 em vez de voltar calado ao Painel', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/tela-que-nao-existe');
    expect(TestBed.inject(Router).url).toBe('/tela-que-nao-existe');
    expect(harness.routeNativeElement?.textContent).toContain('Essa página fugiu do cofrinho');
  });
});
