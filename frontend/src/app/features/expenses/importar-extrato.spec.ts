import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { ImportarExtrato } from './importar-extrato';

/** Previa: o que ja foi importado vem desmarcado e so os marcados sao enviados. */
describe('ImportarExtrato', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
    TestBed.configureTestingModule({
      imports: [ImportarExtrato],
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

  it('importa so as linhas novas marcadas', async () => {
    const tela = TestBed.createComponent(ImportarExtrato);
    tela.componentRef.setInput('categorias', ['Outros', 'Transporte']);
    tela.componentRef.setInput('contas', []);
    tela.componentRef.setInput('mes', '2026-09');
    tela.detectChanges();

    const arquivo = new File(['data;descricao;valor\n10/09/2026;Uber;-23,50\n'], 'extrato.csv', {
      type: 'text/csv',
    });
    const campo = tela.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(campo, 'files', { value: [arquivo] });
    campo.dispatchEvent(new Event('change'));
    await new Promise((pronto) => setTimeout(pronto));

    const previa = http.expectOne('/api/expenses/import/preview');
    expect(previa.request.body.conteudo).toContain('Uber');
    previa.flush([
      {
        data: '2026-09-10',
        descricao: 'Uber',
        valor: 23.5,
        idExterno: 'h1',
        categoria: 'Transporte',
        jaImportada: false,
      },
      {
        data: '2026-09-09',
        descricao: 'Velho',
        valor: 5,
        idExterno: 'h0',
        categoria: 'Outros',
        jaImportada: true,
      },
    ]);
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('1 de 2 marcados');

    const botao = [...tela.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.trim() === 'Importar marcados',
    ) as HTMLButtonElement;
    botao.click();
    const envio = http.expectOne('/api/expenses/import');
    expect(envio.request.body.linhas).toEqual([
      {
        data: '2026-09-10',
        descricao: 'Uber',
        valor: 23.5,
        idExterno: 'h1',
        categoria: 'Transporte',
      },
    ]);
    envio.flush({ importados: 1, pulados: 0 });
    tela.detectChanges();
    expect(tela.nativeElement.textContent).toContain('Importados: 1.');
  });

  describe('com banco conectado (Premium)', () => {
    beforeEach(() => auth.usuario.set(usuarioDeTeste()));

    function abrir() {
      const tela = TestBed.createComponent(ImportarExtrato);
      tela.componentRef.setInput('categorias', ['Outros', 'Transporte']);
      tela.componentRef.setInput('contas', []);
      tela.componentRef.setInput('mes', '2026-09');
      tela.detectChanges();
      http.expectOne('/api/banking/status').flush({ habilitado: true });
      return tela;
    }

    it('traz os debitos do banco para a mesma previa, com a conta de origem', () => {
      const tela = abrir();
      http.expectOne('/api/banking/accounts').flush([{ id: 'b1' }]);
      tela.detectChanges();
      const botao = [...tela.nativeElement.querySelectorAll('button')].find(
        (b: HTMLButtonElement) => b.textContent?.trim() === 'Trazer do banco',
      ) as HTMLButtonElement;
      botao.click();

      const banco = http.expectOne((r) => r.url === '/api/banking/transactions');
      expect(banco.request.params.get('desde')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      banco.flush([
        {
          data: '2026-09-10',
          descricao: 'Uber',
          valor: 23.5,
          idExterno: 'pluggy:t1',
          conta: 'Nubank · Conta',
          moeda: 'BRL',
        },
      ]);
      const previa = http.expectOne('/api/expenses/import/preview-lines');
      expect(previa.request.body.linhas).toEqual([
        { data: '2026-09-10', descricao: 'Uber', valor: 23.5, idExterno: 'pluggy:t1' },
      ]);
      previa.flush([
        {
          data: '2026-09-10',
          descricao: 'Uber',
          valor: 23.5,
          idExterno: 'pluggy:t1',
          categoria: 'Transporte',
          jaImportada: false,
        },
      ]);
      tela.detectChanges();
      expect(tela.nativeElement.textContent).toContain('Nubank · Conta');
      expect(tela.nativeElement.textContent).toContain('1 de 1 marcados');
    });

    it('sem banco conectado nao mostra o botao; no gratuito nem pergunta', () => {
      const tela = abrir();
      http.expectOne('/api/banking/accounts').flush([]);
      tela.detectChanges();
      expect(tela.nativeElement.textContent).not.toContain('Trazer do banco');

      auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO', premiumAte: null }));
      TestBed.createComponent(ImportarExtrato);
      http.expectNone('/api/banking/status');
    });
  });
});
