import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FotoDoFeed } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { mesKey } from '../../core/ui/datas';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, clicar } from '../../testing/tela';
import { Feed } from './feed';

function foto(mudancas: Partial<FotoDoFeed> = {}): FotoDoFeed {
  return {
    id: 'p1',
    mesKey: mesKey(new Date()),
    assetId: 'a1',
    legenda: 'Praia',
    usuario: '11111111-1111-1111-1111-111111111111',
    criadoEm: '2026-10-01T10:00:00Z',
    ...mudancas,
  } as FotoDoFeed;
}

/** Fotos do mes: enviar e Premium; ver, legendar e apagar seguem livres. */
describe('Feed', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;
  const mes = mesKey(new Date());

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Feed],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function abrir(fotos: FotoDoFeed[] = [foto()]) {
    const tela = TestBed.createComponent(Feed);
    tela.detectChanges();
    http.expectOne((r) => r.url === '/api/feed' && r.params.get('mes') === mes).flush(fotos);
    tela.detectChanges();
    return tela;
  }

  function escolherArquivo(pagina: HTMLElement, arquivo: File) {
    const entrada = pagina.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(entrada, 'files', { value: [arquivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
  }

  const esperarLeitura = () => new Promise((pronto) => setTimeout(pronto, 20));

  it('mostra as fotos do mes pelo endereco do arquivo', () => {
    const pagina: HTMLElement = abrir().nativeElement;
    const imagem = pagina.querySelector<HTMLImageElement>('figure img')!;

    expect(imagem.src).toContain('/api/assets/a1/content');
    expect(imagem.alt).toBe('Praia');
  });

  it('no gratuito, enviar vira convite ao Premium', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    const pagina: HTMLElement = abrir().nativeElement;

    expect(pagina.querySelector('input[type=file]')).toBeNull();
    expect(pagina.textContent).toContain('Adicionar fotos · Premium');
  });

  it('envia a foto do mes aberto em base64 e coloca no topo', async () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;

    escolherArquivo(pagina, new File([new Uint8Array([1, 2, 3])], 'f.png', { type: 'image/png' }));
    await esperarLeitura();
    const pedido = http.expectOne((r) => r.method === 'POST' && r.url === '/api/feed');
    expect(pedido.request.body).toEqual({
      mesKey: mes,
      imageBase64: 'AQID',
      mimeType: 'image/png',
    });
    pedido.flush(foto({ id: 'p2', legenda: '' }));
    tela.detectChanges();

    expect(pagina.querySelectorAll('figure').length).toBe(1);
  });

  it('foto acima de 5 MB nem sai do aparelho', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    const grande = new File([new Uint8Array(1)], 'g.jpg', { type: 'image/jpeg' });
    Object.defineProperty(grande, 'size', { value: 6 * 1024 * 1024 });

    escolherArquivo(pagina, grande);
    tela.detectChanges();

    expect(pagina.querySelector('[role=alert]')?.textContent).toContain('O limite é de 5 MB.');
  });

  it('legendar manda o texto e atualiza a foto', () => {
    const tela = abrir();
    const legenda = (tela.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      'figcaption input',
    )!;

    legenda.value = 'Pôr do sol';
    legenda.dispatchEvent(new Event('change'));
    const pedido = http.expectOne((r) => r.method === 'PATCH' && r.url === '/api/feed/p1/caption');
    expect(pedido.request.body).toEqual({ legenda: 'Pôr do sol' });
    pedido.flush(foto({ legenda: 'Pôr do sol' }));
  });

  it('apagar pede confirmacao e tira a foto', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const tela = abrir();
    const pagina: HTMLElement = tela.nativeElement;

    clicar(pagina, 'Apagar');
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/feed/p1').flush(null);
    tela.detectChanges();

    expect(pagina.textContent).toContain('Nenhuma foto neste mês ainda.');
  });

  it('trocar de mes busca as fotos do outro mes', () => {
    const tela = abrir();
    const proximo = mesKey(new Date(new Date().getFullYear(), new Date().getMonth() + 1, 1));

    clicar(tela.nativeElement, 'Próximo mês');

    http.expectOne((r) => r.url === '/api/feed' && r.params.get('mes') === proximo).flush([]);
  });
});
