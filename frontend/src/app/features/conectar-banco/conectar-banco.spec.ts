import { TestBed } from '@angular/core/testing';
import { PluggyConnectService } from '../../core/banking/pluggy-connect.service';
import { ConectarBanco } from './conectar-banco';

describe('ConectarBanco', () => {
  let destinos: string[];
  const abrirWidget = vi.fn();

  function criar(hash: string): ConectarBanco {
    history.replaceState(null, '', `/conectar-banco${hash}`);
    destinos = [];
    // Sem navegar de verdade: guarda para onde a pagina mandaria.
    vi.spyOn(
      ConectarBanco.prototype as unknown as { ir(destino: string): void },
      'ir',
    ).mockImplementation((destino) => {
      destinos.push(destino);
    });
    TestBed.configureTestingModule({
      providers: [{ provide: PluggyConnectService, useValue: { abrirWidget } }],
    });
    return TestBed.createComponent(ConectarBanco).componentInstance;
  }

  afterEach(() => {
    vi.restoreAllMocks();
    abrirWidget.mockReset();
  });

  it('abre o widget com o token do fragmento e devolve o item ao app', async () => {
    abrirWidget.mockResolvedValue('item 1');
    criar('#token=tk&sandbox=1');
    await vi.waitFor(() => expect(destinos).toEqual(['piggu://banco-conectado?item=item%201']));
    expect(abrirWidget).toHaveBeenCalledWith('tk', true);
    // O token sai da barra de endereco e do historico.
    expect(window.location.hash).toBe('');
  });

  it('fechar sem conectar volta ao app sem item', async () => {
    abrirWidget.mockResolvedValue(null);
    criar('#token=tk');
    await vi.waitFor(() => expect(destinos).toEqual(['piggu://banco-conectado?fechou=1']));
    expect(abrirWidget).toHaveBeenCalledWith('tk', false);
  });

  it('erro do widget volta ao app com a mensagem', async () => {
    abrirWidget.mockRejectedValue(new Error('Banco fora do ar'));
    criar('#token=tk');
    await vi.waitFor(() => expect(destinos[0]).toContain('piggu://banco-conectado?erro='));
    expect(decodeURIComponent(destinos[0])).toContain('Banco fora do ar');
  });

  it('sem token nao abre nada', () => {
    criar('');
    expect(abrirWidget).not.toHaveBeenCalled();
    expect(destinos).toEqual([]);
  });
});
