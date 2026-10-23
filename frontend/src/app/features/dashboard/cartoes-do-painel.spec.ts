import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth/auth.service';
import { ItemDoGirlsMath, MetaDoMes } from '../../core/api/models';
import { hojeIso } from '../../core/ui/datas';
import { AuthFalso } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA } from '../../testing/tela';
import { GirlsMathDoPainel } from './girls-math-do-painel';
import { MetasDoPainel } from './metas-do-painel';

const META: MetaDoMes = {
  id: 'm1',
  titulo: 'Correr',
  alvo: 4,
  atual: 4,
  mes: '2026-10',
  concluida: true,
};
const ITEM: ItemDoGirlsMath = {
  id: 'g1',
  produto: 'Bolsa',
  loja: 'Shopping',
  valor: 200,
  data: hojeIso(),
  fotoAssetId: null,
  temFoto: false,
};

/** Cartoes pequenos do Painel: metas do mes e Girls Math. */
describe('Cartoes do painel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MetasDoPainel, GirlsMathDoPainel],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: new AuthFalso() }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir<T>(
    componente: new () => T,
    url: string,
    resposta: object | null,
    status = 200,
  ): HTMLElement {
    const tela = TestBed.createComponent(componente);
    const pedido = http.expectOne((r) => r.url === url);
    if (status === 200) {
      pedido.flush(resposta);
    } else {
      pedido.flush({ erro: 'Fora do ar.' }, { status, statusText: 'Erro' });
    }
    tela.detectChanges();
    return tela.nativeElement;
  }

  it('metas: conta as concluidas', () => {
    const pagina = abrir(MetasDoPainel, '/api/personal-goals', [
      META,
      { ...META, id: 'm2', concluida: false },
    ]);
    expect(pagina.textContent).toContain('1 de 2 concluídas');
  });

  it('metas: sem nenhuma, convida a criar a primeira', () => {
    const pagina = abrir(MetasDoPainel, '/api/personal-goals', []);
    expect(pagina.textContent).toContain('Crie sua primeira meta');
  });

  it('metas: mostra o erro da API', () => {
    const pagina = abrir(MetasDoPainel, '/api/personal-goals', null, 500);
    expect(pagina.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('girls math: soma o que deixou de gastar no mes', () => {
    const pagina = abrir(GirlsMathDoPainel, '/api/girls-math', [ITEM]);
    expect(pagina.textContent).toContain('Você deixou de gastar este mês');
    expect(pagina.querySelector('.valor')?.textContent).toContain('200');
  });

  it('girls math: mostra o erro da API', () => {
    const pagina = abrir(GirlsMathDoPainel, '/api/girls-math', null, 500);
    expect(pagina.querySelector('[role="alert"]')).not.toBeNull();
    expect(pagina.querySelector('.valor')).toBeNull();
  });
});
