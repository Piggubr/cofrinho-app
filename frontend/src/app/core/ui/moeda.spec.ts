import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { Usuario } from '../api/models';
import { MoedaService, simboloDe } from './moeda';

/** A moeda dos valores vem da preferencia da pessoa e muda sem recarregar a tela. */
describe('MoedaService', () => {
  const usuario = signal<Partial<Usuario> | null>(null);
  let moeda: MoedaService;

  beforeEach(() => {
    usuario.set(null);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { usuario } }],
    });
    moeda = TestBed.inject(MoedaService);
  });

  function preferir(codigo: string): void {
    usuario.set({ preferencias: { moeda: codigo, moedaConversao: 'BRL', mostrarCotacao: true } });
  }

  it('sem sessao usa euro, a moeda historica do app', () => {
    expect(moeda.codigo()).toBe('EUR');
    expect(moeda.formatar(1.29)).toContain('1,29');
    expect(moeda.formatar(1.29)).toContain('€');
  });

  it('segue a moeda escolhida na hora', () => {
    preferir('BRL');
    expect(moeda.formatar(1234.5)).toContain('R$');
    expect(moeda.formatar(1234.5)).toContain('1.234,50');

    preferir('USD');
    expect(moeda.simbolo()).toBe('US$');
  });

  it('trata valor ausente como zero', () => {
    expect(moeda.formatar(null)).toContain('0,00');
    expect(moeda.formatar(undefined)).toContain('0,00');
  });

  it('moeda sem simbolo proprio mostra o codigo', () => {
    expect(simboloDe('CHF')).toBe('CHF');
  });
});
