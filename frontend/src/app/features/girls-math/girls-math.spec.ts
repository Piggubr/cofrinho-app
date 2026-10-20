import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { ItemDoGirlsMath } from '../../core/api/models';
import { hojeIso, mesKey } from '../../core/ui/datas';
import { GirlsMath, totaisDoGirlsMath } from './girls-math';

const HOJE = hojeIso();
const BOLSA: ItemDoGirlsMath = {
  id: 'g1',
  produto: 'Bolsa',
  loja: 'Shopping',
  valor: 200,
  data: HOJE,
  fotoAssetId: 'a1',
  temFoto: true,
};
const ANTIGO: ItemDoGirlsMath = {
  ...BOLSA,
  id: 'g2',
  produto: 'Tênis',
  valor: 300,
  data: '2020-01-10',
  fotoAssetId: null,
  temFoto: false,
};

/** Girls Math: placar do mes e de sempre, guardar com foto opcional, apagar. */
describe('GirlsMath', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [GirlsMath],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(itens: ItemDoGirlsMath[]) {
    const tela = TestBed.createComponent(GirlsMath);
    tela.detectChanges();
    http.expectOne('/api/girls-math').flush(itens);
    tela.detectChanges();
    return { tela, pagina: tela.nativeElement as HTMLElement };
  }

  it('soma o mes e o total, e mostra a foto pelo endereco do arquivo', () => {
    expect(totaisDoGirlsMath([BOLSA, ANTIGO], mesKey(new Date()))).toEqual({
      noMes: 200,
      total: 500,
    });
    const { pagina } = abrir([BOLSA, ANTIGO]);
    expect(pagina.querySelector('.valor-grande')!.textContent).toContain('200');
    expect(pagina.querySelector('img.miniatura')!.getAttribute('src')).toContain(
      '/api/assets/a1/content',
    );
    expect(pagina.querySelectorAll('.item').length).toBe(2);
  });

  it('sem produto ou valor nao guarda; com eles, guarda com a data de hoje', async () => {
    const { tela, pagina } = abrir([]);
    const guardar = () =>
      [...pagina.querySelectorAll('button')]
        .find((b) => b.textContent?.includes('Guardar no Girls Math'))!
        .click();

    guardar();
    tela.detectChanges();
    expect(pagina.querySelector('[role="alert"]')!.textContent).toContain('nome do produto');

    const preencher = (id: string, valor: string) => {
      const campo = pagina.querySelector<HTMLInputElement>(`#${id}`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    };
    preencher('produtoGm', 'Bolsa');
    preencher('lojaGm', 'Shopping');
    preencher('valorGm', '200');
    tela.detectChanges();
    guardar();

    const pedido = http.expectOne('/api/girls-math');
    expect(pedido.request.body).toEqual({
      produto: 'Bolsa',
      loja: 'Shopping',
      valor: 200,
      data: HOJE,
      imageBase64: null,
      mimeType: null,
    });
    pedido.flush(BOLSA);
    tela.detectChanges();
    await tela.whenStable();
    expect(pagina.querySelectorAll('.item').length).toBe(1);
    expect(pagina.querySelector<HTMLInputElement>('#produtoGm')!.value).toBe('');
  });

  it('apagar pede confirmacao', () => {
    const { tela, pagina } = abrir([BOLSA]);
    const confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true);
    pagina.querySelector<HTMLButtonElement>('.item .perigo')!.click();
    http.expectOne('/api/girls-math/g1').flush(null);
    tela.detectChanges();
    expect(confirmar).toHaveBeenCalled();
    expect(pagina.textContent).toContain('Nenhum item guardado ainda');
    confirmar.mockRestore();
  });

  it('o membro tambem coloca foto', () => {
    auth.usuario.set(usuarioDeTeste({ role: 'MEMBRO' }));
    const { pagina } = abrir([]);
    expect(pagina.querySelector('label.foto')).not.toBeNull();
    expect(pagina.querySelector('#produtoGm')).not.toBeNull();
  });
});
