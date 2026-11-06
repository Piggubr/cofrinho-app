import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConferenciaDaFatura, ContaOuCartao, LinhaDaConferencia } from '../../core/api/models';
import { APP_CONFIG } from '../../core/config/app-config';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { ConferirFatura } from './conferir-fatura';

const URL_CONFERIR = '/api/expenses/import/card-statement';

const CARTAO = { id: 'c1', nome: 'Nubank', tipo: 'CARTAO' } as ContaOuCartao;

function linha(extra: Partial<LinhaDaConferencia>): LinhaDaConferencia {
  return {
    data: '2026-10-10',
    descricao: 'Mercado',
    valor: 10,
    tipo: 'COMPRA',
    parcelaNumero: null,
    parcelaTotal: null,
    situacao: 'FALTA',
    gastoId: null,
    gastoDescricao: null,
    gastoData: null,
    categoria: 'Mercado',
    idExterno: 'fatura:' + Math.random(),
    ...extra,
  };
}

function resposta(linhas: LinhaDaConferencia[] | null): ConferenciaDaFatura {
  return {
    banco: 'nubank',
    vencimento: '2026-11-10',
    total: 100,
    somaLida: 100,
    origem: 'PROPRIA',
    aviso: null,
    contagem: { compras: 47, tarifas: 0, ignoradas: 0, jaLancadas: 41, confira: 0, faltam: 6 },
    linhas,
  };
}

describe('ConferirFatura', () => {
  let http: HttpTestingController;
  let tela: ComponentFixture<ConferirFatura>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConferirFatura],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    tela = TestBed.createComponent(ConferirFatura);
    tela.componentRef.setInput('cartao', CARTAO);
    tela.componentRef.setInput('mes', '2026-11');
    tela.detectChanges();
  });

  afterEach(() => http.verify());

  const texto = () => tela.nativeElement.textContent as string;
  const botao = (rotulo: string) =>
    [...tela.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.trim().startsWith(rotulo),
    ) as HTMLButtonElement | undefined;

  async function escolher(arquivo: File): Promise<void> {
    const campo = tela.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(campo, 'files', { value: [arquivo], configurable: true });
    campo.dispatchEvent(new Event('change'));
    await new Promise((pronto) => setTimeout(pronto, 20));
  }
  const pdf = () => new File(['%PDF-1.4 fatura'], 'fatura.pdf', { type: 'application/pdf' });

  it('plano gratuito ve so as contagens e o convite ao Premium', async () => {
    await escolher(pdf());
    const pedido = http.expectOne(URL_CONFERIR);
    expect(pedido.request.body.contaId).toBe('c1');
    expect(atob(pedido.request.body.pdfBase64)).toContain('%PDF');
    pedido.flush(resposta(null));
    tela.detectChanges();
    expect(texto()).toContain('41 já lançadas, faltam 6');
    expect(texto()).toContain('Premium');
    expect(tela.nativeElement.querySelector('input[type="checkbox"]')).toBeNull();
    expect(tela.nativeElement.querySelector('a[href="/plano"]')).not.toBeNull();
  });

  it('Premium agrupa as linhas e adiciona so as marcadas, com a parcela', async () => {
    await escolher(pdf());
    http.expectOne(URL_CONFERIR).flush(
      resposta([
        linha({ descricao: 'Padaria', idExterno: 'f1' }),
        linha({ descricao: 'Fone', idExterno: 'f2', parcelaNumero: 2, parcelaTotal: 5, valor: 80 }),
        linha({ descricao: 'Posto', idExterno: 'f3', situacao: 'CONFIRA', gastoDescricao: 'Posto Shell' }),
        linha({ descricao: 'Anuidade', idExterno: 'f4', tipo: 'TARIFA' }),
        linha({ descricao: 'Cinema', idExterno: 'f5', situacao: 'JA_LANCADA' }),
      ]),
    );
    tela.detectChanges();
    for (const grupo of ['Faltam', 'Confira', 'Tarifas', 'Já lançadas']) {
      expect(texto()).toContain(grupo);
    }
    const caixas = [...tela.nativeElement.querySelectorAll('input[type="checkbox"]')] as HTMLInputElement[];
    expect(caixas.map((c) => c.checked)).toEqual([true, true, false, false]);
    expect(botao('Adicionar 2')).toBeDefined();

    botao('Adicionar 2')!.click();
    const importar = http.expectOne('/api/expenses/import');
    expect(importar.request.body.contaId).toBe('c1');
    expect(importar.request.body.linhas).toEqual([
      { data: '2026-10-10', descricao: 'Padaria', valor: 10, idExterno: 'f1', categoria: 'Mercado', parcelaNumero: null, parcelaTotal: null },
      { data: '2026-10-10', descricao: 'Fone', valor: 80, idExterno: 'f2', categoria: 'Mercado', parcelaNumero: 2, parcelaTotal: 5 },
    ]);
    importar.flush({ importados: 2, pulados: 0 });
    tela.detectChanges();

    botao('Marcar fatura como paga')!.click();
    const pagar = http.expectOne((r) => r.url === '/api/accounts/c1/statement/paid');
    expect(pagar.request.params.get('mes')).toBe('2026-11');
    pagar.flush(null);
  });

  it('sem cartao aberto nao oferece marcar como paga', async () => {
    tela.componentRef.setInput('cartao', null);
    tela.componentRef.setInput('cartoes', [CARTAO]);
    tela.detectChanges();
    const seletor = tela.nativeElement.querySelector('select') as HTMLSelectElement;
    seletor.value = 'c1';
    seletor.dispatchEvent(new Event('change'));
    await escolher(pdf());
    http.expectOne(URL_CONFERIR).flush(resposta([linha({ idExterno: 'f1' })]));
    tela.detectChanges();
    botao('Adicionar 1')!.click();
    http.expectOne('/api/expenses/import').flush({ importados: 1, pulados: 0 });
    tela.detectChanges();
    expect(botao('Marcar fatura como paga')).toBeUndefined();
  });

  it('PDF com senha pede a senha e tenta de novo', async () => {
    await escolher(pdf());
    http
      .expectOne(URL_CONFERIR)
      .flush(
        { erro: 'Este PDF tem senha. Digite a senha da fatura.', codigo: 'PDF_COM_SENHA' },
        { status: 422, statusText: 'x' },
      );
    tela.detectChanges();
    const campo = tela.nativeElement.querySelector('input[type="password"]') as HTMLInputElement;
    expect(campo).not.toBeNull();
    expect(tela.nativeElement.querySelector(`label[for="${campo.id}"]`)).not.toBeNull();
    campo.value = 'segredo';
    campo.dispatchEvent(new Event('input'));
    botao('Tentar de novo')!.click();
    expect(http.expectOne(URL_CONFERIR).request.body.senha).toBe('segredo');
  });

  it('pede consentimento e reenvia com a versao do aviso', async () => {
    await escolher(pdf());
    http
      .expectOne(URL_CONFERIR)
      .flush({ erro: 'Autorize o envio.', codigo: 'CONSENTIMENTO_NECESSARIO' }, { status: 422, statusText: 'x' });
    tela.detectChanges();
    expect(texto()).toContain('Autorize o envio.');
    botao('Autorizar e continuar')!.click();
    const novo = http.expectOne(URL_CONFERIR);
    expect(novo.request.body.versaoDoAviso).toBe(VERSAO_DO_AVISO);
    expect(novo.request.body.autorizoIa).toBe(true);
    novo.flush(resposta([]));
  });

  it('PLANO_PREMIUM mostra a mensagem e o convite', async () => {
    await escolher(pdf());
    http
      .expectOne(URL_CONFERIR)
      .flush({ erro: 'A leitura pela IA faz parte do Piggu Premium.', codigo: 'PLANO_PREMIUM' }, { status: 422, statusText: 'x' });
    tela.detectChanges();
    expect(texto()).toContain('A leitura pela IA faz parte do Piggu Premium.');
    expect(tela.nativeElement.querySelector('a[href="/plano"]')).not.toBeNull();
  });

  it('arquivo maior que 5 MB nem e enviado', async () => {
    const grande = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.pdf');
    await escolher(grande);
    tela.detectChanges();
    http.expectNone(URL_CONFERIR);
    expect(texto()).toContain('PDF grande demais (máximo 5 MB).');
  });
});
