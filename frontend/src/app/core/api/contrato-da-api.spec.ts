import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, firstValueFrom } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { BankingService } from './banking.service';
import { BillingService } from './billing.service';
import { FamilyService } from './family.service';
import { FinanceService } from './finance.service';
import { HistoryService } from './history.service';
import { LifestyleService } from './lifestyle.service';
import { MediaService } from './media.service';
import { EventoDeAuditoria, FilmeDoCatalogo, ItemDeGasto, LinhaDoExtrato } from './models';
import { RewardsService } from './rewards.service';
import { UsersService } from './users.service';

interface Servicos {
  banking: BankingService;
  billing: BillingService;
  family: FamilyService;
  finance: FinanceService;
  lifestyle: LifestyleService;
  media: MediaService;
  rewards: RewardsService;
  users: UsersService;
}

/** [o que, chamada, metodo, url, query esperada, corpo esperado] */
type Caso = [
  string,
  (s: Servicos) => Observable<unknown>,
  string,
  string,
  Record<string, string>?,
  unknown?,
];

const LINHA = {
  data: '2026-10-01',
  descricao: 'Pão',
  valor: 5,
  categoria: 'Mercado',
} as LinhaDoExtrato;
const FILME = { tmdbId: 1, titulo: 'Up' } as unknown as FilmeDoCatalogo;

const CASOS: Caso[] = [
  [
    'gastos do mes',
    (s) => s.finance.listarGastos('2026-10'),
    'GET',
    '/expenses',
    { mes: '2026-10' },
  ],
  [
    'gastos sem mes nao manda filtro vazio',
    (s) => s.finance.listarGastos(''),
    'GET',
    '/expenses',
    {},
  ],
  [
    'editar gasto manda so o que muda',
    (s) =>
      s.finance.editarGasto('g1', {
        item: 'Pão',
        categoria: 'Mercado',
        valor: 5,
        extra: 1,
      } as unknown as ItemDeGasto),
    'PUT',
    '/expenses/g1',
    {},
    { item: 'Pão', categoria: 'Mercado', valor: 5 },
  ],
  ['apagar gasto', (s) => s.finance.excluirGasto('g1'), 'DELETE', '/expenses/g1'],
  ['buscar gastos', (s) => s.finance.buscarGastos('pão'), 'GET', '/expenses/search', { q: 'pão' }],
  [
    'previa do extrato',
    (s) => s.finance.previaDoExtrato('csv'),
    'POST',
    '/expenses/import/preview',
    {},
    { conteudo: 'csv' },
  ],
  [
    'importar extrato',
    (s) => s.finance.importarExtrato([LINHA], 'c1'),
    'POST',
    '/expenses/import',
    {},
    { linhas: [LINHA], contaId: 'c1' },
  ],
  [
    'acerto da divisao',
    (s) => s.finance.acertoDoMes('2026-10'),
    'GET',
    '/expenses/splits',
    { mes: '2026-10' },
  ],
  [
    'conferir preco',
    (s) => s.finance.conferirPreco('Pão', 5),
    'GET',
    '/products/price-check',
    { item: 'Pão', valor: '5' },
  ],
  ['cofrinho', (s) => s.finance.consultarCofrinho(), 'GET', '/piggy-bank'],
  [
    'depositar',
    (s) => s.finance.depositar(50, '2026-10-01'),
    'POST',
    '/piggy-bank/deposits',
    {},
    { valor: 50, data: '2026-10-01' },
  ],
  ['apagar deposito', (s) => s.finance.excluirDeposito('d1'), 'DELETE', '/piggy-bank/deposits/d1'],
  ['notas', (s) => s.finance.listarNotas(), 'GET', '/notes'],
  [
    'criar nota',
    (s) => s.finance.criarNota({ titulo: 'Luz' }),
    'POST',
    '/notes',
    {},
    { titulo: 'Luz' },
  ],
  ['apagar nota', (s) => s.finance.excluirNota('n1'), 'DELETE', '/notes/n1'],
  ['produtos', (s) => s.finance.listarProdutos(), 'GET', '/products'],
  [
    'criar categoria',
    (s) => s.finance.criarCategoria('Pets'),
    'POST',
    '/categories',
    {},
    { nome: 'Pets' },
  ],
  [
    'receitas do mes',
    (s) => s.finance.listarReceitas('2026-10'),
    'GET',
    '/incomes',
    { mes: '2026-10' },
  ],
  ['apagar receita', (s) => s.finance.excluirReceita('r1'), 'DELETE', '/incomes/r1'],
  [
    'criar conta fixa',
    (s) =>
      s.finance.criarContaFixa({
        descricao: 'Luz',
        categoria: 'Casa',
        valor: 90,
        dia: 10,
        automatico: true,
      }),
    'POST',
    '/bills',
    {},
    { descricao: 'Luz', categoria: 'Casa', valor: 90, dia: 10, automatico: true },
  ],
  ['apagar conta fixa', (s) => s.finance.excluirContaFixa('b1'), 'DELETE', '/bills/b1'],
  [
    'resumo do mes',
    (s) => s.finance.resumoDoMes('2026-10'),
    'GET',
    '/reports/month',
    { mes: '2026-10' },
  ],
  [
    'relatorio do ano',
    (s) => s.finance.relatorioDoAno(2026),
    'GET',
    '/reports/year',
    { ano: '2026' },
  ],
  ['contas e cartoes', (s) => s.finance.contas(), 'GET', '/accounts'],
  [
    'criar cartao',
    (s) => s.finance.criarConta({ nome: 'Nu', tipo: 'CARTAO', fechamento: 3, vencimento: 10 }),
    'POST',
    '/accounts',
    {},
    { nome: 'Nu', tipo: 'CARTAO', fechamento: 3, vencimento: 10 },
  ],
  ['apagar conta', (s) => s.finance.excluirConta('c1'), 'DELETE', '/accounts/c1'],
  [
    'fatura',
    (s) => s.finance.fatura('c1', '2026-10'),
    'GET',
    '/accounts/c1/statement',
    { mes: '2026-10' },
  ],
  ['regras de categoria', (s) => s.finance.regrasDeCategoria(), 'GET', '/categories/rules'],
  [
    'definir regra',
    (s) => s.finance.definirRegra('uber', 'Transporte'),
    'PUT',
    '/categories/rules',
    {},
    { termo: 'uber', categoria: 'Transporte' },
  ],
  ['apagar regra', (s) => s.finance.excluirRegra('x1'), 'DELETE', '/categories/rules/x1'],
  ['uso de leituras', (s) => s.finance.usoDeLeituras(), 'GET', '/receipts/usage'],
  [
    'ler recibo com o aceite da IA',
    (s) => s.finance.lerRecibo('b64', 'image/png', 'v1'),
    'POST',
    '/receipts/parse',
    {},
    { imageBase64: 'b64', mimeType: 'image/png', autorizoIa: true, versaoDoAviso: 'v1' },
  ],
  [
    'ler recibo sem aceite novo',
    (s) => s.finance.lerRecibo('b64', 'image/png'),
    'POST',
    '/receipts/parse',
    {},
    { imageBase64: 'b64', mimeType: 'image/png', autorizoIa: undefined, versaoDoAviso: undefined },
  ],
  ['moedas', (s) => s.finance.listarMoedas(), 'GET', '/exchange-rate/currencies'],
  ['status do Open Finance', (s) => s.banking.status(), 'GET', '/banking/status'],
  [
    'token do Pluggy com o aceite',
    (s) => s.banking.gerarConnectToken('v2'),
    'POST',
    '/banking/connect-token',
    {},
    { autorizo: true, versaoDoAviso: 'v2' },
  ],
  [
    'registrar banco',
    (s) => s.banking.registrarItem('i1'),
    'POST',
    '/banking/items',
    {},
    { itemId: 'i1' },
  ],
  ['contas do banco', (s) => s.banking.listarContas(), 'GET', '/banking/accounts'],
  ['desconectar banco', (s) => s.banking.desconectar('x1'), 'DELETE', '/banking/connections/x1'],
  ['sincronizar bancos', (s) => s.banking.sincronizar(), 'POST', '/banking/sync', {}, {}],
  ['plano', (s) => s.billing.plano(), 'GET', '/billing/plan'],
  [
    'assinar',
    (s) => s.billing.checkout('ANUAL' as never),
    'POST',
    '/billing/checkout',
    {},
    { periodo: 'ANUAL' },
  ],
  ['reembolso', (s) => s.billing.reembolso(), 'POST', '/billing/refund', {}, {}],
  ['portal da Stripe', (s) => s.billing.portal(), 'POST', '/billing/portal', {}, {}],
  ['familia', (s) => s.family.ver(), 'GET', '/family'],
  ['renomear familia', (s) => s.family.renomear('Silva'), 'PUT', '/family', {}, { nome: 'Silva' }],
  [
    'convidar',
    (s) => s.family.convidar('bia@x.com'),
    'POST',
    '/family/invites',
    {},
    { email: 'bia@x.com' },
  ],
  ['cancelar convite', (s) => s.family.cancelarConvite('v1'), 'DELETE', '/family/invites/v1'],
  ['remover membro', (s) => s.family.removerMembro('m1'), 'DELETE', '/family/members/m1'],
  [
    'tornar parceiro',
    (s) => s.family.mudarPapel('m1', 'PARCEIRO'),
    'PUT',
    '/family/members/m1/role',
    {},
    { papel: 'PARCEIRO' },
  ],
  ['convites para mim', (s) => s.family.convitesParaMim(), 'GET', '/family/invites/mine'],
  [
    'aceitar convite',
    (s) => s.family.aceitarConvite('v1'),
    'POST',
    '/family/invites/v1/accept',
    {},
    {},
  ],
  ['sair da familia', (s) => s.family.sair(), 'POST', '/family/leave', {}, {}],
  ['lugares', (s) => s.lifestyle.listarLugares(), 'GET', '/places'],
  [
    'criar lugar',
    (s) => s.lifestyle.criarLugar({ nome: 'Praia' } as never),
    'POST',
    '/places',
    {},
    { nome: 'Praia' },
  ],
  [
    'mudar lugar',
    (s) => s.lifestyle.atualizarLugar('l1', { nome: 'Praia' } as never),
    'PUT',
    '/places/l1',
    {},
    { nome: 'Praia' },
  ],
  ['apagar lugar', (s) => s.lifestyle.excluirLugar('l1'), 'DELETE', '/places/l1'],
  ['marcadores', (s) => s.lifestyle.listarMarcadores(), 'GET', '/places/tags'],
  [
    'criar marcador',
    (s) => s.lifestyle.criarMarcador('Bar'),
    'POST',
    '/places/tags',
    {},
    { nome: 'Bar' },
  ],
  ['filmes', (s) => s.lifestyle.listarFilmes(), 'GET', '/movies'],
  ['buscar filme', (s) => s.lifestyle.buscarFilmes('up'), 'GET', '/movies/search', { busca: 'up' }],
  [
    'sortear filme de qualquer genero',
    (s) => s.lifestyle.sortearFilme(),
    'GET',
    '/movies/random',
    {},
  ],
  [
    'sortear filme do genero',
    (s) => s.lifestyle.sortearFilme('Drama'),
    'GET',
    '/movies/random',
    { genero: 'Drama' },
  ],
  ['adicionar filme', (s) => s.lifestyle.adicionarFilme(FILME), 'POST', '/movies', {}, FILME],
  [
    'marcar assistido',
    (s) => s.lifestyle.marcarFilmeAssistido('f1', true),
    'PATCH',
    '/movies/f1/watched',
    { assistido: 'true' },
    null,
  ],
  [
    'avaliar filme',
    (s) => s.lifestyle.avaliarFilme('f1', 4),
    'PUT',
    '/movies/f1/rating',
    {},
    { nota: 4 },
  ],
  ['apagar filme', (s) => s.lifestyle.excluirFilme('f1'), 'DELETE', '/movies/f1'],
  [
    'compras da lista',
    (s) => s.lifestyle.listarCompras('Mercado'),
    'GET',
    '/shopping/items',
    { lista: 'Mercado' },
  ],
  [
    'criar item de compra',
    (s) => s.lifestyle.criarItemDeCompra({ nome: 'Pão' } as never),
    'POST',
    '/shopping/items',
    {},
    { nome: 'Pão' },
  ],
  [
    'marcar comprado',
    (s) => s.lifestyle.marcarComprado('i1', false),
    'PATCH',
    '/shopping/items/i1/purchased',
    { comprado: 'false' },
    null,
  ],
  [
    'apagar item de compra',
    (s) => s.lifestyle.excluirItemDeCompra('i1'),
    'DELETE',
    '/shopping/items/i1',
  ],
  [
    'catalogo',
    (s) => s.lifestyle.buscarNoCatalogo('pão'),
    'GET',
    '/shopping/catalog',
    { busca: 'pão' },
  ],
  ['feed do mes', (s) => s.media.listarFeed('2026-10'), 'GET', '/feed', { mes: '2026-10' }],
  [
    'publicar foto',
    (s) => s.media.publicarNoFeed('2026-10', 'b64', 'image/jpeg'),
    'POST',
    '/feed',
    {},
    { mesKey: '2026-10', imageBase64: 'b64', mimeType: 'image/jpeg' },
  ],
  [
    'legendar foto',
    (s) => s.media.legendar('p1', 'Praia'),
    'PATCH',
    '/feed/p1/caption',
    {},
    { legenda: 'Praia' },
  ],
  ['apagar foto', (s) => s.media.excluirDoFeed('p1'), 'DELETE', '/feed/p1'],
  [
    'enviar arquivo',
    (s) => s.media.enviarArquivo('b64', 'image/png', 'premio'),
    'POST',
    '/assets',
    {},
    { imageBase64: 'b64', mimeType: 'image/png', contexto: 'premio' },
  ],
  ['apagar arquivo', (s) => s.media.excluirArquivo('a1'), 'DELETE', '/assets/a1'],
  ['saldo de moedas', (s) => s.rewards.consultarSaldo(), 'GET', '/coins'],
  [
    'ajustar moedas',
    (s) => s.rewards.ajustarMoedas(10, 'Louça'),
    'POST',
    '/coins/adjustments',
    {},
    { valor: 10, motivo: 'Louça' },
  ],
  ['premios ativos', (s) => s.rewards.listarPremios(), 'GET', '/prizes', { todos: 'false' }],
  ['todos os premios', (s) => s.rewards.listarPremios(true), 'GET', '/prizes', { todos: 'true' }],
  [
    'criar premio',
    (s) => s.rewards.criarPremio({ nome: 'Pizza', preco: 50 }),
    'POST',
    '/prizes',
    {},
    { nome: 'Pizza', preco: 50 },
  ],
  [
    'mudar premio',
    (s) => s.rewards.atualizarPremio('p1', { nome: 'Pizza', preco: 60 }),
    'PUT',
    '/prizes/p1',
    {},
    { nome: 'Pizza', preco: 60 },
  ],
  ['apagar premio', (s) => s.rewards.excluirPremio('p1'), 'DELETE', '/prizes/p1'],
  ['resgatar', (s) => s.rewards.resgatar('p1'), 'POST', '/prizes/p1/redemptions', {}, {}],
  ['resgates', (s) => s.rewards.listarResgates(), 'GET', '/prizes/redemptions'],
  ['meu perfil', (s) => s.users.meuPerfil(), 'GET', '/auth/me'],
  [
    'ligar modulos',
    (s) => s.users.salvarModulos(['filmes', 'fotos']),
    'PUT',
    '/auth/me/modules',
    {},
    { modulos: ['filmes', 'fotos'] },
  ],
  ['excluir conta', (s) => s.users.excluirConta(), 'DELETE', '/me'],
  ['contas da instalacao', (s) => s.users.listar(), 'GET', '/users'],
  [
    'ADMIN muda papel',
    (s) => s.users.alterar('u1', { role: 'MEMBRO' }),
    'PATCH',
    '/users/u1',
    {},
    { role: 'MEMBRO' },
  ],
];

/**
 * O contrato com o gateway: metodo, rota, filtros e corpo de cada chamada.
 * Rota trocada aqui quebra a tela em producao sem erro de compilacao.
 */
describe('contrato da API', () => {
  let http: HttpTestingController;
  let servicos: Servicos;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servicos = {
      banking: TestBed.inject(BankingService),
      billing: TestBed.inject(BillingService),
      family: TestBed.inject(FamilyService),
      finance: TestBed.inject(FinanceService),
      lifestyle: TestBed.inject(LifestyleService),
      media: TestBed.inject(MediaService),
      rewards: TestBed.inject(RewardsService),
      users: TestBed.inject(UsersService),
    };
  });

  afterEach(() => http.verify());

  it.each(CASOS)('%s', (_, chamar, metodo, url, query, corpo) => {
    chamar(servicos).subscribe();
    const pedido = http.expectOne((r) => r.url === `/api${url}`);

    expect(pedido.request.method).toBe(metodo);
    if (query) {
      const recebida = Object.fromEntries(
        pedido.request.params.keys().map((k) => [k, pedido.request.params.get(k)]),
      );
      expect(recebida).toEqual(query);
    }
    if (corpo !== undefined) {
      expect(pedido.request.body).toEqual(corpo);
    }
    pedido.flush(null);
  });

  it('arquivos e exportacoes chegam como Blob', async () => {
    const pedidos = [
      firstValueFrom(servicos.finance.exportarGastos('2026-10')),
      firstValueFrom(servicos.users.exportarMeusDados()),
      firstValueFrom(servicos.media.baixarArquivo('a1')),
    ];
    for (const url of ['/api/expenses/export', '/api/me/export', '/api/assets/a1/content']) {
      const pedido = http.expectOne((r) => r.url === url);
      expect(pedido.request.responseType).toBe('blob');
      pedido.flush(new Blob(['x']));
    }
    expect((await Promise.all(pedidos)).every((b) => b instanceof Blob)).toBe(true);
  });

  it('a imagem vem direto do endereco do arquivo, sem baixar em base64', () => {
    expect(servicos.media.urlDaImagem('a1')).toBe('/api/assets/a1/content');
  });
});

describe('HistoryService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function evento(id: number, quando: string): EventoDeAuditoria {
    return { id, quando } as EventoDeAuditoria;
  }

  it('junta dinheiro e pessoas, do mais novo ao mais velho, ate o limite', async () => {
    const historico = firstValueFrom(TestBed.inject(HistoryService).daFamilia(3));
    http
      .expectOne((r) => r.url === '/api/history' && r.params.get('limite') === '3')
      .flush([evento(1, '2026-10-01T10:00:00Z'), evento(2, '2026-10-03T10:00:00Z')]);
    http
      .expectOne('/api/family/history?limite=3')
      .flush([evento(3, '2026-10-02T10:00:00Z'), evento(4, '2026-09-01T10:00:00Z')]);

    expect((await historico).map((e) => e.id)).toEqual([2, 3, 1]);
  });

  it('um servico fora do ar nao esconde o que o outro tem', async () => {
    const historico = firstValueFrom(TestBed.inject(HistoryService).daFamilia());
    http
      .expectOne((r) => r.url === '/api/history')
      .flush(null, { status: 503, statusText: 'Fora' });
    http
      .expectOne((r) => r.url === '/api/family/history')
      .flush([evento(9, '2026-10-01T00:00:00Z')]);

    expect((await historico).map((e) => e.id)).toEqual([9]);
  });
});
