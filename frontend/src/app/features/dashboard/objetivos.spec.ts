import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ObjetivoDoCofrinho } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA, digitar } from '../../testing/tela';
import { Objetivos } from './objetivos';

function objetivo(campos: Partial<ObjetivoDoCofrinho> = {}): ObjetivoDoCofrinho {
  return {
    id: 'o1',
    nome: 'Viagem',
    alvo: 1000,
    prazo: '2027-01-31',
    guardado: 200,
    falta: 800,
    percentual: 20,
    mesesRestantes: 4,
    porMes: 200,
    concluido: false,
    atrasado: false,
    ...campos,
  };
}

/** Cofrinhos por objetivo no painel: quanto guardar por mes, deposito e o limite do gratuito. */
describe('Objetivos', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;

  beforeEach(() => {
    auth = new AuthFalso();
    TestBed.configureTestingModule({
      imports: [Objetivos],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function abrir(objetivos: ObjetivoDoCofrinho[]) {
    const tela = TestBed.createComponent(Objetivos);
    tela.detectChanges();
    http.expectOne('/api/piggy-bank/goals').flush(objetivos);
    tela.detectChanges();
    return tela;
  }

  function botao(pagina: HTMLElement, texto: string): HTMLButtonElement {
    return [...pagina.querySelectorAll('button')].find((b) => b.textContent?.includes(texto))!;
  }

  it('mostra quanto guardar por mes, o prazo vencido e o objetivo alcancado', () => {
    const pagina: HTMLElement = abrir([
      objetivo(),
      objetivo({ id: 'o2', nome: 'Sofá', atrasado: true, porMes: 800 }),
      objetivo({ id: 'o3', nome: 'Bike', concluido: true, falta: 0, percentual: 100 }),
    ]).nativeElement;
    const situacoes = [...pagina.querySelectorAll('.situacao')].map((p) => p.textContent);
    expect(situacoes[0]).toContain('por mês até 31/01/2027');
    expect(situacoes[1]).toContain('O prazo passou');
    expect(situacoes[2]).toContain('Objetivo alcançado');
    // Alcancado nao pede mais deposito.
    expect(pagina.querySelector('#deposito-o3')).toBeNull();
  });

  it('deposito vai para o objetivo e avisa o painel', () => {
    const tela = abrir([objetivo()]);
    const pagina: HTMLElement = tela.nativeElement;
    let avisos = 0;
    tela.componentInstance.depositou.subscribe(() => avisos++);

    digitar(pagina, '#deposito-o1', '150');
    botao(pagina, 'Guardar').click();
    const pedido = http.expectOne('/api/piggy-bank/deposits');
    expect(pedido.request.body).toEqual(expect.objectContaining({ valor: 150, objetivo: 'o1' }));
    pedido.flush({});
    http.expectOne('/api/piggy-bank/goals').flush([objetivo({ guardado: 350 })]);
    expect(avisos).toBe(1);
  });

  it('cria objetivo com nome, valor e prazo', () => {
    const tela = abrir([]);
    const pagina: HTMLElement = tela.nativeElement;
    expect(pagina.textContent).toContain('Criar o primeiro objetivo');

    digitar(pagina, '#nome-do-objetivo', ' Viagem ');
    digitar(pagina, '#alvo-do-objetivo', '1000');
    digitar(pagina, '#prazo-do-objetivo', '2027-01-31');
    botao(pagina, 'Salvar objetivo').click();
    const pedido = http.expectOne('/api/piggy-bank/goals');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ nome: 'Viagem', alvo: 1000, prazo: '2027-01-31' });
    pedido.flush(objetivo());
    http.expectOne('/api/piggy-bank/goals').flush([objetivo()]);
  });

  it('no gratuito, com um objetivo, o formulario da lugar ao convite do Premium', () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO' }));
    const pagina: HTMLElement = abrir([objetivo()]).nativeElement;
    expect(pagina.querySelector('#nome-do-objetivo')).toBeNull();
    expect(pagina.querySelector('a[href="/plano"]')).not.toBeNull();
  });

  it('editar abre o formulario mesmo no gratuito', async () => {
    auth.usuario.set(usuarioDeTeste({ plano: 'GRATUITO' }));
    const tela = abrir([objetivo()]);
    const pagina: HTMLElement = tela.nativeElement;
    (pagina.querySelector('button[aria-label="Editar"]') as HTMLButtonElement).click();
    tela.detectChanges();
    // O ngModel escreve no campo depois do ciclo.
    await tela.whenStable();
    expect((pagina.querySelector('#nome-do-objetivo') as HTMLInputElement).value).toBe('Viagem');
  });
});
