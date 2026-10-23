import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ConviteDaFamilia,
  EventoDeAuditoria,
  Familia,
  MembroDaFamilia,
} from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AuthFalso, usuarioDeTeste } from '../../testing/auth-falso';
import { PROVEDORES_DE_TELA } from '../../testing/tela';
import { Family } from './family';

const BIA: MembroDaFamilia = {
  id: 'u2',
  nome: 'Bia',
  email: 'bia@x',
  foto: null,
  papel: 'MEMBRO',
  premium: false,
};

const FAMILIA: Familia = {
  id: 'f1',
  nome: 'Familia de Ana',
  plano: 'GRATUITO',
  planoFamilia: false,
  membros: [
    { id: 'u1', nome: 'Ana', email: 'ana@x', foto: null, papel: 'TITULAR', premium: false },
    BIA,
  ],
  convites: [],
};

const CONVITE: ConviteDaFamilia = {
  id: 'c9',
  email: 'eu@x',
  venceEm: '2026-11-01T00:00:00Z',
  familia: 'Casa da Lu',
};

/** Familia: as acoes de quem convida, de quem e convidado e as confirmacoes antes de cada uma. */
describe('Family: acoes', () => {
  let http: HttpTestingController;
  let auth: AuthFalso;
  let confirmar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    auth = new AuthFalso();
    auth.usuario.set(usuarioDeTeste({ id: 'u1' }));
    confirmar = vi.spyOn(window, 'confirm').mockReturnValue(true);
    TestBed.configureTestingModule({
      imports: [Family],
      providers: [...PROVEDORES_DE_TELA, { provide: AuthService, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function abrir(familia: Familia = FAMILIA, recebidos: ConviteDaFamilia[] = []) {
    const tela = TestBed.createComponent(Family);
    tela.detectChanges();
    http.expectOne('/api/family').flush(familia);
    http.expectOne('/api/family/invites/mine').flush(recebidos);
    tela.detectChanges();
    return tela;
  }

  /** As acoes da tela, chamadas como os botoes chamam. */
  function acoes(tela: ComponentFixture<Family>) {
    return tela.componentInstance as unknown as {
      aceitar(c: ConviteDaFamilia): void;
      verHistorico(): void;
      descrever(e: EventoDeAuditoria): string;
      quando(e: EventoDeAuditoria): string;
      convidar(): void;
      renomear(): void;
      cancelarConvite(id: string): void;
      mudarPapel(m: MembroDaFamilia): void;
      conviteDeParceria(): boolean;
      aceitarParceria(): void;
      recusarParceria(): void;
      remover(m: MembroDaFamilia): void;
      sairDaFamilia(): void;
      erro(): string;
      aviso(): string;
      email: { set(v: string): void };
      nome: { set(v: string): void };
      historico(): EventoDeAuditoria[] | null;
    };
  }

  it('aceitar convite de outra familia avisa o que acontece e encerra a sessao', () => {
    const tela = abrir(FAMILIA, [CONVITE]);
    const tela$ = acoes(tela);

    confirmar.mockReturnValueOnce(false);
    tela$.aceitar(CONVITE);
    expect(confirmar.mock.calls[0][0]).toContain('Entrar na Casa da Lu?');

    tela$.aceitar(CONVITE);
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/family/invites/c9/accept')
      .flush(null);
    expect(auth.usuario()).toBeNull();
  });

  it('erro ao aceitar convite aparece', () => {
    const tela$ = acoes(abrir(FAMILIA, [CONVITE]));

    tela$.aceitar(CONVITE);
    http
      .expectOne((r) => r.url.endsWith('/accept'))
      .flush({ erro: 'Convite vencido.' }, { status: 410, statusText: 'x' });

    expect(tela$.erro()).toBe('Convite vencido.');
  });

  it('o historico junta dinheiro e pessoas e descreve cada evento', () => {
    const tela$ = acoes(abrir());

    tela$.verHistorico();
    const evento = (mudancas: Partial<EventoDeAuditoria>): EventoDeAuditoria => ({
      id: 1,
      autor: 'u2',
      acao: 'APAGOU',
      entidade: 'gasto',
      entidadeId: null,
      antes: null,
      depois: null,
      quando: '2026-10-01T10:00:00Z',
      ...mudancas,
    });
    http.expectOne((r) => r.url === '/api/history').flush([evento({})]);
    http
      .expectOne((r) => r.url === '/api/family/history')
      .flush([
        evento({ id: 2, acao: 'ENTROU', entidade: 'pessoa', quando: '2026-10-02T10:00:00Z' }),
      ]);

    const [entrou, apagou] = tela$.historico()!;
    expect(tela$.descrever(entrou)).toBe('Bia entrou na família');
    expect(tela$.descrever(apagou)).toBe('Bia apagou gasto');
    expect(
      tela$.descrever(evento({ acao: 'NOVA' as EventoDeAuditoria['acao'], entidade: 'coisa' })),
    ).toBe('Bia NOVA coisa');
    expect(tela$.quando(apagou)).toMatch(/01\/10\/2026/);
  });

  it('convite precisa de e-mail valido; nome da familia nao pode ficar vazio', () => {
    const tela$ = acoes(abrir());

    tela$.email.set('sem-arroba');
    tela$.convidar();
    expect(tela$.erro()).toBe('Digite um e-mail válido.');

    tela$.nome.set('  ');
    tela$.renomear();
    expect(tela$.erro()).toBe('Digite o nome da família.');

    tela$.nome.set(' Casa nova ');
    tela$.renomear();
    const pedido = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/family');
    expect(pedido.request.body).toEqual({ nome: 'Casa nova' });
    pedido.flush({ ...FAMILIA, nome: 'Casa nova' });
    expect(tela$.aviso()).toBe('Nome salvo.');
  });

  it('cancelar convite e o erro do servidor', () => {
    const tela$ = acoes(abrir());

    tela$.cancelarConvite('c1');
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/family/invites/c1')
      .flush(FAMILIA);
    expect(tela$.aviso()).toBe('Convite cancelado.');

    tela$.cancelarConvite('c1');
    http
      .expectOne((r) => r.method === 'DELETE')
      .flush({ erro: 'Já aceito.' }, { status: 409, statusText: 'x' });
    expect(tela$.erro()).toBe('Já aceito.');
  });

  it('titular convida para parceiro, desiste do convite e volta o parceiro a membro', () => {
    const tela$ = acoes(abrir());

    confirmar.mockReturnValueOnce(false);
    tela$.mudarPapel(BIA);
    expect(confirmar.mock.calls[0][0]).toContain('Convidar Bia para ser parceiro?');

    tela$.mudarPapel(BIA);
    const convite = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/family/members/u2/role',
    );
    expect(convite.request.body).toEqual({ papel: 'PARCEIRO' });
    convite.flush(FAMILIA);
    expect(tela$.aviso()).toContain('Bia vê o convite');

    tela$.mudarPapel({ ...BIA, parceriaPendente: true });
    const desistir = http.expectOne((r) => r.url === '/api/family/members/u2/role');
    expect(desistir.request.body).toEqual({ papel: 'MEMBRO' });
    desistir.flush(FAMILIA);
    expect(tela$.aviso()).toBe('Convite de parceria cancelado.');

    tela$.mudarPapel({ ...BIA, papel: 'PARCEIRO' });
    expect(confirmar.mock.calls.at(-1)![0]).toContain('Voltar Bia a membro?');
    http.expectOne((r) => r.url === '/api/family/members/u2/role').flush(FAMILIA);
    expect(tela$.aviso()).toContain('Papel alterado.');
  });

  it('quem recebeu convite de parceria aceita (e entra de novo) ou recusa', () => {
    auth.usuario.set(usuarioDeTeste({ id: 'u2', role: 'MEMBRO' }));
    const tela$ = acoes(
      abrir({ ...FAMILIA, membros: [FAMILIA.membros[0], { ...BIA, parceriaPendente: true }] }),
    );
    expect(tela$.conviteDeParceria()).toBe(true);

    confirmar.mockReturnValueOnce(false);
    tela$.aceitarParceria();
    expect(http.match((r) => r.url.includes('partnership'))).toEqual([]);

    tela$.recusarParceria();
    http.expectOne((r) => r.url === '/api/family/partnership/decline').flush(FAMILIA);
    expect(tela$.aviso()).toContain('Convite recusado.');
    expect(tela$.conviteDeParceria()).toBe(false);

    tela$.aceitarParceria();
    http
      .expectOne((r) => r.url === '/api/family/partnership/accept')
      .flush({ erro: 'Convite cancelado.' }, { status: 409, statusText: 'x' });
    expect(tela$.erro()).toBe('Convite cancelado.');

    tela$.aceitarParceria();
    http.expectOne((r) => r.url === '/api/family/partnership/accept').flush(null);
    expect(auth.usuario()).toBeNull();
  });

  it('tirar alguem da familia pede confirmacao', () => {
    const tela$ = acoes(abrir());

    confirmar.mockReturnValueOnce(false);
    tela$.remover(BIA);
    expect(http.match((r) => r.method === 'DELETE')).toEqual([]);

    tela$.remover(BIA);
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/family/members/u2')
      .flush(FAMILIA);
    expect(tela$.aviso()).toBe('Pessoa removida da família.');
  });

  it('sair da familia pede confirmacao, sai da conta e mostra o erro', () => {
    const sair = vi.spyOn(auth, 'sair');
    const tela$ = acoes(abrir());

    confirmar.mockReturnValueOnce(false);
    tela$.sairDaFamilia();
    expect(http.match((r) => r.url === '/api/family/leave')).toEqual([]);

    tela$.sairDaFamilia();
    http
      .expectOne('/api/family/leave')
      .flush({ erro: 'Titular não sai.' }, { status: 409, statusText: 'x' });
    expect(tela$.erro()).toBe('Titular não sai.');

    tela$.sairDaFamilia();
    http.expectOne('/api/family/leave').flush(null);
    expect(sair).toHaveBeenCalledOnce();
  });

  it('sem familia carregada o erro aparece e os convites recebidos ficam vazios', () => {
    const tela = TestBed.createComponent(Family);
    tela.detectChanges();
    http.expectOne('/api/family').flush({ erro: 'Fora do ar.' }, { status: 503, statusText: 'x' });
    http.expectOne('/api/family/invites/mine').flush(null, { status: 503, statusText: 'x' });
    tela.detectChanges();

    expect(acoes(tela).erro()).toBe('Fora do ar.');
    expect(
      acoes(tela).descrever({ autor: null, acao: 'CRIOU', entidade: 'meta' } as EventoDeAuditoria),
    ).toContain('lançou meta do mês');
  });
});
