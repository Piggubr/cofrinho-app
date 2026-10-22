import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../config/app-config';
import { CampoDeCategoria } from './campo-de-categoria';

@Component({
  imports: [CampoDeCategoria],
  template: `
    <app-campo-de-categoria
      campoId="categoria"
      [categorias]="categorias()"
      [valor]="valor()"
      (valorChange)="valor.set($event)"
      (criou)="categorias.set($event)"
    />
  `,
})
class Hospedeiro {
  readonly categorias = signal(['Alimentação', 'Lazer']);
  readonly valor = signal('');
}

/** "+ Nova categoria" cria na hora e ja deixa a nova escolhida. */
describe('CampoDeCategoria', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Hospedeiro],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrirCriacao() {
    const tela = TestBed.createComponent(Hospedeiro);
    tela.detectChanges();
    await tela.whenStable();
    const pagina: HTMLElement = tela.nativeElement;
    const lista = pagina.querySelector('select')!;
    const opcoes = [...lista.options].map((o) => o.textContent?.trim());
    expect(opcoes).toEqual(['Automática', 'Alimentação', 'Lazer', '+ Nova categoria…']);
    lista.value = lista.options[lista.options.length - 1].value;
    lista.dispatchEvent(new Event('change'));
    tela.detectChanges();
    await tela.whenStable();
    return { tela, pagina };
  }

  function digitar(pagina: HTMLElement, texto: string): void {
    const campo = pagina.querySelector<HTMLInputElement>('input')!;
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
  }

  it('cria a categoria e ela ja fica escolhida', async () => {
    const { tela, pagina } = await abrirCriacao();
    expect(document.activeElement).toBe(pagina.querySelector('input'));
    digitar(pagina, '  Pet ');
    pagina.querySelector<HTMLButtonElement>('button[aria-label="Criar categoria"]')!.click();

    const pedido = http.expectOne('/api/categories');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ nome: 'Pet' });
    pedido.flush({ categorias: ['Alimentação', 'Lazer', 'Pet'] });
    tela.detectChanges();
    await tela.whenStable();

    expect(tela.componentInstance.valor()).toBe('Pet');
    expect(tela.componentInstance.categorias()).toContain('Pet');
    expect(pagina.querySelector('select')!.value).toBe('Pet');
  });

  it('nome que ja existe so escolhe, sem chamar o servidor', async () => {
    const { tela, pagina } = await abrirCriacao();
    digitar(pagina, 'lazer');
    pagina.querySelector('input')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    tela.detectChanges();

    expect(tela.componentInstance.valor()).toBe('Lazer');
    expect(pagina.querySelector('select')).not.toBeNull();
  });

  it('sair do campo com o nome escrito cria, para o nome nao se perder', async () => {
    const { tela, pagina } = await abrirCriacao();
    digitar(pagina, 'Pet');
    pagina.querySelector('input')!.dispatchEvent(new Event('blur'));
    // Ja escolhida enquanto o servidor responde: um "Lançar" agora leva a nova.
    expect(tela.componentInstance.valor()).toBe('Pet');

    http.expectOne('/api/categories').flush({ categorias: ['Alimentação', 'Lazer', 'Pet'] });
    tela.detectChanges();
    expect(tela.componentInstance.valor()).toBe('Pet');
  });

  it('cancelar volta para a lista sem mudar a escolha', async () => {
    const { tela, pagina } = await abrirCriacao();
    pagina.querySelector<HTMLButtonElement>('button.fantasma')!.click();
    tela.detectChanges();
    await tela.whenStable();

    expect(tela.componentInstance.valor()).toBe('');
    expect(pagina.querySelector('select')!.value).toBe('');
  });

  it('nome curto demais avisa e nao cria', async () => {
    const { tela, pagina } = await abrirCriacao();
    digitar(pagina, 'a');
    pagina.querySelector<HTMLButtonElement>('button[aria-label="Criar categoria"]')!.click();
    tela.detectChanges();

    expect(pagina.querySelector('[role="alert"]')?.textContent).toContain('Digite o nome');
  });
});
