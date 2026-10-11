import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { UsersService } from '../api/users.service';

const plugin = {
  requestPermissions: vi.fn(),
  checkPermissions: vi.fn(),
  register: vi.fn(async () => undefined),
  addListener: vi.fn(),
};

import { AvisosNoCelular, PUSH_NATIVO } from './avisos-no-celular';

/** Push no iPhone: permissao primeiro, e o token que o sistema der vai para o backend. */
describe('AvisosNoCelular', () => {
  const registrarAparelho = vi.fn(() => of(undefined));

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: UsersService, useValue: { registrarAparelho } },
        { provide: PUSH_NATIVO, useValue: { disponivel: true, plugin } },
      ],
    });
  });

  it('com a permissao, registra e manda o token do aparelho', async () => {
    plugin.requestPermissions.mockResolvedValue({ receive: 'granted' });
    plugin.addListener.mockImplementation(async (_evento, ouvir) => {
      ouvir({ value: 'abc123' });
      return { remove: async () => undefined };
    });

    expect(await TestBed.inject(AvisosNoCelular).ativar()).toBe(true);
    expect(plugin.register).toHaveBeenCalled();
    expect(registrarAparelho).toHaveBeenCalledWith('abc123');
  });

  it('sem a permissao nao registra; na abertura so renova se ja tinha permissao', async () => {
    plugin.requestPermissions.mockResolvedValue({ receive: 'denied' });
    plugin.checkPermissions.mockResolvedValue({ receive: 'prompt' });
    const avisos = TestBed.inject(AvisosNoCelular);

    expect(await avisos.ativar()).toBe(false);
    await avisos.renovar();
    expect(plugin.register).not.toHaveBeenCalled();
  });
});
