import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlanHub } from './plan-hub';

/** A aba Planejar junta as tres telas que olham para a frente. */
describe('PlanHub', () => {
  it('leva a Orcamentos, Metas e Contas fixas', () => {
    TestBed.configureTestingModule({ imports: [PlanHub], providers: [provideRouter([])] });
    const tela = TestBed.createComponent(PlanHub);
    tela.detectChanges();
    const links = [
      ...(tela.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('a.atalho'),
    ];

    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/orcamentos',
      '/metas',
      '/contas-fixas',
    ]);
    expect(links[0].textContent).toContain('80% e 100%');
  });
});
