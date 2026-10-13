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
import { autenticadoGuard, cpfGuard, perfilGuard, termosGuard } from './auth.guard';
import { AuthService } from './auth.service';

/** Quem abre cada tela: sessao valida primeiro, depois o perfil. */
describe('guards de rota', () => {
  const autenticado = signal(false);
  const termosPendentes = signal(false);
  const cpfPendente = signal(false);
  let restaurou = false;
  let perfil: PigguRole | null = null;
  let tentativasDeRestaurar = 0;

  beforeEach(() => {
    autenticado.set(false);
    termosPendentes.set(false);
    cpfPendente.set(false);
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
            termosPendentes,
            cpfPendente,
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

  it('termos novos sem aceite levam ao aceite antes do CPF; tudo em dia, passa', () => {
    termosPendentes.set(true);
    cpfPendente.set(true);
    expect(destino(rodar(cpfGuard))).toBe('/aceite');
    expect(destino(rodar(termosGuard))).toBe('/aceite');

    termosPendentes.set(false);
    expect(destino(rodar(cpfGuard))).toBe('/cpf');
    expect(rodar(termosGuard)).toBe(true);

    cpfPendente.set(false);
    expect(rodar(cpfGuard)).toBe(true);
  });

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
