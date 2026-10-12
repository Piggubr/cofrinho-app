import { TestBed } from '@angular/core/testing';
import { AuthService } from '../auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { BLOCOS_DO_PAINEL, PainelOrganizado, ordemValida } from './painel';

/** Ordem do Painel: por pessoa e aparelho, e sempre com todos os blocos conhecidos. */
describe('PainelOrganizado', () => {
  const chave = `piggu_painel_${usuarioDeTeste().id}`;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: auth }] });
  });

  afterEach(() => localStorage.clear());

  it('sem nada salvo, segue a ordem padrao com tudo visivel', () => {
    const painel = TestBed.inject(PainelOrganizado);
    expect(painel.blocos().map((b) => b.id)).toEqual(BLOCOS_DO_PAINEL.map((b) => b.id));
    expect(painel.blocos().every((b) => b.visivel)).toBe(true);
  });

  it('subir, esconder e restaurar ficam guardados para a pessoa', () => {
    const painel = TestBed.inject(PainelOrganizado);
    painel.mover('notas', -1);
    painel.alternar('categorias');

    const ids = painel.blocos().map((b) => b.id);
    expect(ids.indexOf('notas')).toBe(ids.indexOf('girls-math') - 1);
    expect(painel.posicoes().has('categorias')).toBe(false);
    expect(JSON.parse(localStorage.getItem(chave)!)).toEqual(painel.blocos());

    painel.restaurar();
    expect(painel.blocos().map((b) => b.id)).toEqual(BLOCOS_DO_PAINEL.map((b) => b.id));
  });

  it('o primeiro nao sobe e o ultimo nao desce', () => {
    const painel = TestBed.inject(PainelOrganizado);
    const antes = painel.blocos();
    painel.mover('resumo', -1);
    painel.mover('categorias', 1);
    expect(painel.blocos()).toEqual(antes);
  });

  it('outra pessoa no mesmo aparelho tem a propria ordem', () => {
    const painel = TestBed.inject(PainelOrganizado);
    painel.mover('cofrinho', -1);
    auth.usuario.set(usuarioDeTeste({ id: '22222222-2222-2222-2222-222222222222' }));
    expect(painel.blocos().map((b) => b.id)).toEqual(BLOCOS_DO_PAINEL.map((b) => b.id));
  });

  it('descarta lixo e bloco desconhecido, e poe o bloco novo no fim', () => {
    expect(ordemValida('lixo').length).toBe(BLOCOS_DO_PAINEL.length);
    const ordem = ordemValida([
      { id: 'categorias', visivel: false },
      { id: 'sumiu', visivel: true },
      { id: 'categorias', visivel: true },
    ]);
    expect(ordem[0]).toEqual({ id: 'categorias', visivel: false });
    expect(ordem.length).toBe(BLOCOS_DO_PAINEL.length);
    expect(ordem.at(-1)!.id).not.toBe('categorias');
  });
});
