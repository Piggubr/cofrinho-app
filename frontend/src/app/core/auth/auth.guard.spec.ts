import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { PigguRole } from '../api/models';
import { autenticadoGuard, perfilGuard } from './auth.guard';
import { AuthService } from './auth.service';

/** Quem abre cada tela: sessao valida primeiro, depois o perfil. */
describe('guards de rota', () => {
  const autenticado = signal(false);
  let restaurou = false;
  let perfil: PigguRole | null = null;
  let tentativasDeRestaurar = 0;

  beforeEach(() => {
    autenticado.set(false);
    restaurou = false;
    perfil = null;
    tentativasDeRestaurar = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            autenticado,
            restaurarSessao: async () => {
              tentativasDeRestaurar++;
              return restaurou;
            },
            temPerfil: (...perfis: PigguRole[]) => perfil != null && perfis.includes(perfil),
          },
        },
      ],
    });
  });

  function rodar(guarda: typeof autenticadoGuard) {
    return TestBed.runInInjectionContext(() =>
      guarda({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  function destino(resultado: unknown): string {
    return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
  }

  it('com sessao aberta, passa sem perguntar ao servidor', async () => {
    autenticado.set(true);

    expect(await rodar(autenticadoGuard)).toBe(true);
    expect(tentativasDeRestaurar).toBe(0);
  });

  it('sem sessao em memoria, recupera a guardada no cookie e passa', async () => {
    restaurou = true;

    expect(await rodar(autenticadoGuard)).toBe(true);
    expect(tentativasDeRestaurar).toBe(1);
  });

  it('sem sessao nenhuma, manda para o login', async () => {
    expect(destino(await rodar(autenticadoGuard))).toBe('/entrar');
  });

  it('perfil permitido passa', () => {
    perfil = 'PARCEIRO';

    expect(rodar(perfilGuard('TITULAR', 'PARCEIRO'))).toBe(true);
  });

  it('perfil fora da lista volta para o painel', () => {
    perfil = 'MEMBRO';

    expect(destino(rodar(perfilGuard('TITULAR', 'PARCEIRO')))).toBe('/painel');
  });

  it('sem usuario, nenhum perfil passa', () => {
    expect(destino(rodar(perfilGuard('ADMIN')))).toBe('/painel');
  });
});
