import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { digitar } from '../../testing/tela';
import { EsqueciSenha } from './esqueci-senha';
import { RedefinirSenha } from './redefinir-senha';

async function abrirRedefinir(token: string | null, redefinirSenha: ReturnType<typeof vi.fn>) {
  TestBed.configureTestingModule({
    imports: [RedefinirSenha],
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { redefinirSenha } },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(RedefinirSenha);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture;
}

async function enviar(fixture: { nativeElement: HTMLElement; whenStable(): Promise<unknown>; detectChanges(): void }) {
  fixture.nativeElement.querySelector('form')!.dispatchEvent(new Event('submit'));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('EsqueciSenha', () => {
  it('pede o link e diz o mesmo com ou sem conta', async () => {
    const esqueciSenha = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      imports: [EsqueciSenha],
      providers: [provideRouter([]), { provide: AuthService, useValue: { esqueciSenha } }],
    });
    const fixture = TestBed.createComponent(EsqueciSenha);
    fixture.detectChanges();
    await fixture.whenStable();

    digitar(fixture.nativeElement, '#email-esqueci', ' duda@exemplo.test ');
    await enviar(fixture);

    expect(esqueciSenha).toHaveBeenCalledWith('duda@exemplo.test');
    expect(fixture.nativeElement.textContent).toContain('Se duda@exemplo.test tem conta no Piggu');
  });

  it('confere o e-mail antes de chamar o servidor', async () => {
    const esqueciSenha = vi.fn();
    TestBed.configureTestingModule({
      imports: [EsqueciSenha],
      providers: [provideRouter([]), { provide: AuthService, useValue: { esqueciSenha } }],
    });
    const fixture = TestBed.createComponent(EsqueciSenha);
    fixture.detectChanges();
    await fixture.whenStable();

    digitar(fixture.nativeElement, '#email-esqueci', 'nao-e-email');
    await enviar(fixture);

    expect(esqueciSenha).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Confira o e-mail.');
  });
});

describe('RedefinirSenha', () => {
  it('manda o token do link com a senha nova e manda entrar de novo', async () => {
    const redefinirSenha = vi.fn().mockResolvedValue(undefined);
    const fixture = await abrirRedefinir('abc', redefinirSenha);

    digitar(fixture.nativeElement, '#senha-nova', 'porquinho456');
    digitar(fixture.nativeElement, '#senha-nova-repetida', 'porquinho456');
    await enviar(fixture);

    expect(redefinirSenha).toHaveBeenCalledWith('abc', 'porquinho456');
    expect(fixture.nativeElement.textContent).toContain('Senha nova salva');
  });

  it('recusa senha fraca ou diferente sem chamar o servidor', async () => {
    const redefinirSenha = vi.fn();
    const fixture = await abrirRedefinir('abc', redefinirSenha);

    digitar(fixture.nativeElement, '#senha-nova', 'curta1');
    await enviar(fixture);
    expect(fixture.nativeElement.textContent).toContain('pelo menos 8 caracteres');

    digitar(fixture.nativeElement, '#senha-nova', 'porquinho456');
    digitar(fixture.nativeElement, '#senha-nova-repetida', 'porquinho789');
    await enviar(fixture);
    expect(fixture.nativeElement.textContent).toContain('As duas senhas nao sao iguais.');
    expect(redefinirSenha).not.toHaveBeenCalled();
  });

  it('link vencido ou sem token oferece pedir outro', async () => {
    const vencido = vi.fn().mockRejectedValue(
      new HttpErrorResponse({ status: 422, error: { erro: 'venceu', codigo: 'LINK_INVALIDO' } }),
    );
    const fixture = await abrirRedefinir('velho', vencido);
    digitar(fixture.nativeElement, '#senha-nova', 'porquinho456');
    digitar(fixture.nativeElement, '#senha-nova-repetida', 'porquinho456');
    await enviar(fixture);
    expect(fixture.nativeElement.textContent).toContain('Pedir outro link');

    TestBed.resetTestingModule();
    const semToken = await abrirRedefinir(null, vi.fn());
    expect(semToken.nativeElement.textContent).toContain('Link vencido');
  });
});
