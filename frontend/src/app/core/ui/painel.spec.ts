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

  it('sem nada salvo, mostra so os blocos principais e deixa o resto para ligar', () => {
    const painel = TestBed.inject(PainelOrganizado);
    expect(painel.blocos().map((b) => b.id)).toEqual(BLOCOS_DO_PAINEL.map((b) => b.id));
    expect([...painel.posicoes().keys()]).toEqual([
      'resumo',
      'avisos',
      'mes',
      'cofrinho',
      'objetivos',
    ]);

    painel.alternar('notas');
    expect(painel.posicoes().has('notas')).toBe(true);
  });

  it('subir, esconder e restaurar ficam guardados para a pessoa', () => {
    const painel = TestBed.inject(PainelOrganizado);
    painel.mover('girls-math', -1);
    painel.alternar('cofrinho');

    const ids = painel.blocos().map((b) => b.id);
    expect(ids.indexOf('girls-math')).toBe(ids.indexOf('notas') - 1);
    expect(painel.posicoes().has('cofrinho')).toBe(false);
    expect(JSON.parse(localStorage.getItem(chave)!)).toEqual(painel.blocos());

    painel.restaurar();
    expect(painel.blocos().map((b) => b.id)).toEqual(BLOCOS_DO_PAINEL.map((b) => b.id));
  });

  it('o primeiro nao sobe e o ultimo nao desce', () => {
    const painel = TestBed.inject(PainelOrganizado);
    const antes = painel.blocos();
    painel.mover('resumo', -1);
    painel.mover('girls-math', 1);
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
    expect(ordem.at(-1)).toEqual({ id: 'girls-math', visivel: false });
    expect(ordem.find((b) => b.id === 'resumo')!.visivel).toBe(true);
  });
});
