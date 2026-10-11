import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ATALHOS_NATIVOS, Atalhos } from './atalhos';

/** Atalhos do app instalado: a rota guardada na abertura e a que chega com o app aberto. */
describe('Atalhos', () => {
  let ouvinte: ((dados: { rota: string }) => void) | undefined;
  let pendente: string | null;

  function preparar(disponivel = true) {
    ouvinte = undefined;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ATALHOS_NATIVOS,
          useValue: {
            disponivel,
            plugin: {
              pendente: async () => ({ rota: pendente }),
              addListener: async (_: string, funcao: (dados: { rota: string }) => void) => {
                ouvinte = funcao;
                return { remove: async () => undefined };
              },
            },
          },
        },
      ],
    });
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    return { atalhos: TestBed.inject(Atalhos), navegar };
  }

  it('app aberto pelo atalho vai para o lancamento rapido', async () => {
    pendente = '/gastos?rapido=1';
    const { atalhos, navegar } = preparar();
    await atalhos.ligar();
    expect(navegar).toHaveBeenCalledWith('/gastos?rapido=1');
  });

  it('com o app aberto, o atalho chega pelo evento; endereco de fora e ignorado', async () => {
    pendente = null;
    const { atalhos, navegar } = preparar();
    await atalhos.ligar();
    expect(navegar).not.toHaveBeenCalled();

    ouvinte!({ rota: '//golpe.example/entrar' });
    ouvinte!({ rota: 'https://golpe.example' });
    expect(navegar).not.toHaveBeenCalled();
    ouvinte!({ rota: '/gastos?rapido=1&valor=9.90' });
    expect(navegar).toHaveBeenCalledWith('/gastos?rapido=1&valor=9.90');
  });

  it('no navegador nao faz nada', async () => {
    pendente = '/gastos';
    const { atalhos, navegar } = preparar(false);
    await atalhos.ligar();
    expect(ouvinte).toBeUndefined();
    expect(navegar).not.toHaveBeenCalled();
  });
});
