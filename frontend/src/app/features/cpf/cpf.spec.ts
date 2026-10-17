import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Usuario } from '../../core/api/models';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { Cpf } from './cpf';

/** Tela do documento: o campo segue o pais e so libera o app com o que o pais pede. */
describe('Cpf', () => {
  let auth: AuthFalso;
  let enviados: [string, string | undefined][];
  let resposta: () => ReturnType<UsersService['informarPais']>;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ cpfInformado: false, pais: 'BR' }));
    enviados = [];
    resposta = () => of(usuarioDeTeste({ cpfInformado: true }) as Usuario);
    TestBed.configureTestingModule({
      imports: [Cpf],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: UsersService,
          useValue: {
            informarPais: (pais: string, documento?: string) => {
              enviados.push([pais, documento]);
              return resposta();
            },
          },
        },
      ],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  async function abrir() {
    const tela = TestBed.createComponent(Cpf);
    tela.detectChanges();
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;
    const digitar = (valor: string) => {
      const campo = pagina.querySelector<HTMLInputElement>('#documento')!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
      tela.detectChanges();
    };
    const escolher = (codigo: string) => {
      const pais = pagina.querySelector<HTMLSelectElement>('#pais')!;
      pais.value = codigo;
      pais.dispatchEvent(new Event('change'));
      tela.detectChanges();
    };
    const continuar = async () => {
      pagina.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await tela.whenStable();
      tela.detectChanges();
    };
    const alerta = () => pagina.querySelector('[role=alert]')?.textContent ?? '';
    return { tela, pagina, digitar, escolher, continuar, alerta };
  }

  it('no Brasil pede o CPF com mascara e renova a sessao depois de gravar', async () => {
    const { pagina, digitar, continuar, alerta } = await abrir();
    expect(pagina.querySelector('label[for=documento]')?.textContent).toContain('CPF');

    await continuar();
    expect(alerta()).toContain('Informe o número do seu documento (CPF)');

    digitar('52998224725');
    expect(pagina.querySelector<HTMLInputElement>('#documento')!.value).toBe('529.982.247-25');
    await continuar();

    expect(enviados).toEqual([['BR', '52998224725']]);
    expect(auth.renovacoes()).toBe(1);
    expect(navegar).toHaveBeenCalledWith('/painel');
  });

  it('troca o campo pelo documento do pais e, onde nao se pede nenhum, so grava o pais', async () => {
    const { pagina, digitar, escolher, continuar, alerta } = await abrir();

    escolher('ES');
    expect(pagina.querySelector('label[for=documento]')?.textContent).toContain('DNI/NIE');
    digitar('12345678A');
    await continuar();
    expect(alerta()).toContain('DNI/NIE inválido');
    expect(enviados).toEqual([]);

    escolher('JP');
    expect(pagina.querySelector('#documento')).toBeNull();
    expect(pagina.textContent).toContain('não pede documento');
    await continuar();
    expect(enviados).toEqual([['JP', undefined]]);
  });

  it('mostra o erro do backend (documento em outra conta) e deixa tentar de novo', async () => {
    resposta = () =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              erro: 'Este NIF já está ligado a outra conta do Piggu.',
              codigo: 'DOCUMENTO_EM_USO',
            },
          }),
      );
    const { tela, digitar, escolher, continuar, alerta } = await abrir();
    escolher('PT');
    digitar('123456789');
    await continuar();
    await tela.whenStable();
    tela.detectChanges();

    expect(alerta()).toContain('outra conta');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('sair encerra a sessao', async () => {
    const sair = vi.fn().mockResolvedValue(undefined);
    (auth as unknown as { sair: () => Promise<void> }).sair = sair;
    const { pagina } = await abrir();
    const botoes = [...pagina.querySelectorAll('button')];
    botoes.find((botao) => botao.textContent?.includes('Sair'))!.click();
    expect(sair).toHaveBeenCalled();
  });
});
