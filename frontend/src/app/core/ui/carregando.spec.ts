import { TestBed } from '@angular/core/testing';
import { Carregando, ESPERA_LENTA_MS, ESPERA_MUITO_LENTA_MS, conexaoLenta } from './carregando';

describe('Carregando', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function abrir(): { tela: HTMLElement; atualizar: () => void } {
    const fixture = TestBed.createComponent(Carregando);
    fixture.componentRef.setInput('linhas', 4);
    fixture.detectChanges();
    return { tela: fixture.nativeElement, atualizar: () => fixture.detectChanges() };
  }

  it('desenha o formato da lista e anuncia para o leitor de tela', () => {
    const { tela } = abrir();
    expect(tela.getAttribute('role')).toBe('status');
    expect(tela.querySelectorAll('.linha-falsa').length).toBe(4);
    expect(tela.textContent).toContain('Carregando…');
    expect(tela.textContent).not.toContain('conexão parece lenta');
  });

  it('avisa da conexao lenta e depois oferece recarregar', () => {
    vi.useFakeTimers();
    const { tela, atualizar } = abrir();

    vi.advanceTimersByTime(ESPERA_LENTA_MS);
    atualizar();
    expect(tela.textContent).toContain('Sua conexão parece lenta');
    expect(tela.querySelector('button')).toBeNull();

    vi.advanceTimersByTime(ESPERA_MUITO_LENTA_MS - ESPERA_LENTA_MS);
    atualizar();
    expect(tela.querySelector('button')?.textContent).toContain('Recarregar a página');
  });

  it('reconhece rede 2G e economia de dados', () => {
    vi.stubGlobal('navigator', { connection: { effectiveType: 'slow-2g' } });
    expect(conexaoLenta()).toBe(true);
    vi.stubGlobal('navigator', { connection: { effectiveType: '4g', saveData: true } });
    expect(conexaoLenta()).toBe(true);
    vi.stubGlobal('navigator', { connection: { effectiveType: '4g' } });
    expect(conexaoLenta()).toBe(false);
    vi.stubGlobal('navigator', {});
    expect(conexaoLenta()).toBe(false);
  });
});
