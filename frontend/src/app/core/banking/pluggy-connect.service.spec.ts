import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { PluggyConnectService } from './pluggy-connect.service';

type Opcoes = ConstructorParameters<NonNullable<Window['PluggyConnect']>>[0];

/**
 * Fluxo de conexao de banco.
 *
 * <p>O widget real da Pluggy e trocado por um falso que guarda as opcoes recebidas,
 * para o teste decidir se o usuario conectou ou fechou.</p>
 */
describe('PluggyConnectService', () => {
  let http: HttpTestingController;
  let servico: PluggyConnectService;
  let opcoes: Opcoes | undefined;

  beforeEach(() => {
    opcoes = undefined;
    window.PluggyConnect = class {
      constructor(recebidas: Opcoes) {
        opcoes = recebidas;
      }
      init(): void {}
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servico = TestBed.inject(PluggyConnectService);
  });

  afterEach(() => {
    http.verify();
    delete window.PluggyConnect;
  });

  /** Devolve o resultado embrulhado: uma funcao async achataria a promessa interna. */
  async function abrirWidget(): Promise<{
    resultado: ReturnType<PluggyConnectService['conectar']>;
  }> {
    const resultado = servico.conectar('2026-10-08');
    const pedido = http.expectOne('/api/banking/connect-token');
    // A autorizacao vai no mesmo pedido que abre a conexao.
    expect(pedido.request.body).toEqual({ autorizo: true, versaoDoAviso: '2026-10-08' });
    pedido.flush({ accessToken: 'token-1', sandbox: true });
    await vi.waitFor(() => expect(opcoes).toBeDefined());
    return { resultado };
  }

  it('abre o widget com o token do backend, registra o item e entrega as contas', async () => {
    const { resultado } = await abrirWidget();
    expect(opcoes!.connectToken).toBe('token-1');
    expect(opcoes!.includeSandbox).toBe(true);

    opcoes!.onSuccess({ item: { id: 'item-1' } });
    const registro = await vi.waitFor(() => http.expectOne('/api/banking/items'));
    expect(registro.request.body).toEqual({ itemId: 'item-1' });
    registro.flush([{ id: 'c1', nome: 'NuConta', saldo: 10, moeda: 'BRL' }]);

    expect(await resultado).toEqual([{ id: 'c1', nome: 'NuConta', saldo: 10, moeda: 'BRL' }]);
  });

  it('fechar o widget sem conectar nao registra nada', async () => {
    const { resultado } = await abrirWidget();
    opcoes!.onClose!();

    expect(await resultado).toBeNull();
  });
});
