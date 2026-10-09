import {
  ContaBancaria,
  Deposito,
  Filme,
  FotoDoFeed,
  Gasto,
  ItemDeCompra,
  Lugar,
  MovimentoDeMoedas,
  Nota,
  Premio,
  Receita,
  RegraDeCategoria,
  Resgate,
  Usuario,
} from '../core/api/models';
import { dataIso } from '../core/ui/datas';

/** Dados de exemplo da demonstracao: uma familia, tres meses de vida financeira. */

export interface ContaFixaDemo {
  id: string;
  descricao: string;
  categoria: string;
  valor: number;
  dia: number;
  automatico: boolean;
}

export interface ContaDemo {
  id: string;
  nome: string;
  tipo: 'CONTA' | 'CARTAO';
  fechamento: number | null;
  vencimento: number | null;
}

export interface EstadoDaDemo {
  titular: Usuario;
  membro: Usuario;
  nomeDaFamilia: string;
  convites: { id: string; email: string; venceEm: string }[];
  categoriasExtras: string[];
  gastos: Gasto[];
  /** Divisao: gastoId -> parte de cada e-mail. */
  partes: Record<string, Record<string, number>>;
  receitas: Receita[];
  contasFixas: ContaFixaDemo[];
  /** "idDaConta|AAAA-MM" das contas fixas ja pagas. */
  contasPagas: Set<string>;
  orcamentos: { id: string; categoria: string; limite: number }[];
  contas: ContaDemo[];
  regras: RegraDeCategoria[];
  metas: Record<string, number>;
  depositos: Deposito[];
  notas: Nota[];
  lugares: Lugar[];
  marcadores: string[];
  filmes: Filme[];
  compras: ItemDeCompra[];
  fotos: FotoDoFeed[];
  /** assetId -> data URL da imagem. */
  imagens: Record<string, string>;
  moedas: number;
  movimentos: MovimentoDeMoedas[];
  premios: Premio[];
  resgates: Resgate[];
  bancos: ContaBancaria[];
  leiturasNoMes: number;
}

export const CATEGORIAS_BASE = [
  'Aluguel',
  'Alimentação',
  'Transporte',
  'Farmácia/Saúde',
  'Lazer',
  'Assinaturas',
  'Gastos Extras',
  'Outros',
];

export const CATEGORIAS_DE_RECEITA = ['Salário', 'Extra', 'Reembolso', 'Investimentos', 'Outros'];

let sequencia = 0;
export function novoId(prefixo: string): string {
  sequencia += 1;
  return `${prefixo}-${Date.now().toString(36)}-${sequencia}`;
}

/** Dia do mes relativo a hoje (deslocamento 0 = este mes); dia 31 vira o ultimo dia. */
export function diaDoMes(hoje: Date, deslocamento: number, dia: number): string {
  const ultimo = new Date(hoje.getFullYear(), hoje.getMonth() + deslocamento + 1, 0).getDate();
  return dataIso(new Date(hoje.getFullYear(), hoje.getMonth() + deslocamento, Math.min(dia, ultimo)));
}

function usuario(id: string, email: string, nome: string, role: Usuario['role'], premiumAte: string): Usuario {
  return {
    id,
    email,
    nome,
    primeiroNome: nome.split(' ')[0],
    apelido: null,
    foto: null,
    role,
    ativo: true,
    permissoes: {},
    preferencias: {
      moeda: 'BRL',
      moedaConversao: 'USD',
      mostrarCotacao: false,
      fuso: 'America/Sao_Paulo',
      idioma: 'pt-BR',
    },
    plano: 'PREMIUM',
    premiumAte,
    familia: 'familia-demo',
  };
}

/** Foto de exemplo desenhada na hora (SVG), sem baixar nada. */
function fotoDeExemplo(cor1: string, cor2: string, emoji: string, texto: string): string {
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${cor1}"/>` +
    `<stop offset="1" stop-color="${cor2}"/></linearGradient></defs>` +
    `<rect width="600" height="600" fill="url(#g)"/>` +
    `<text x="300" y="300" font-size="160" text-anchor="middle" dominant-baseline="middle">${emoji}</text>` +
    `<text x="300" y="470" font-size="40" fill="#fff" text-anchor="middle" font-family="sans-serif">${texto}</text>` +
    `</svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

/** Familia Demo: Ana (titular, Premium) e Beto (membro), com tres meses de dados. */
export function criarEstado(hoje = new Date()): EstadoDaDemo {
  const hojeIso = dataIso(hoje);
  const premiumAte = dataIso(new Date(hoje.getFullYear() + 1, hoje.getMonth(), hoje.getDate()));
  const ana = usuario('u-ana', 'ana@demo.piggu.app', 'Ana Souza', 'TITULAR', premiumAte);
  const beto = usuario('u-beto', 'beto@demo.piggu.app', 'Beto Souza', 'MEMBRO', premiumAte);

  const estado: EstadoDaDemo = {
    titular: ana,
    membro: beto,
    nomeDaFamilia: 'Família Demo',
    convites: [
      { id: 'convite-1', email: 'vovo@exemplo.com', venceEm: dataIso(new Date(hoje.getTime() + 5 * 86_400_000)) },
    ],
    categoriasExtras: ['Pets'],
    gastos: [],
    partes: {},
    receitas: [],
    contasFixas: [
      { id: 'fixa-aluguel', descricao: 'Aluguel', categoria: 'Aluguel', valor: 1800, dia: 5, automatico: true },
      { id: 'fixa-netflix', descricao: 'Netflix', categoria: 'Assinaturas', valor: 55.9, dia: 10, automatico: true },
      { id: 'fixa-internet', descricao: 'Internet', categoria: 'Assinaturas', valor: 119.9, dia: 15, automatico: false },
      { id: 'fixa-academia', descricao: 'Academia', categoria: 'Farmácia/Saúde', valor: 99, dia: 25, automatico: false },
    ],
    contasPagas: new Set(),
    orcamentos: [
      { id: 'orc-alimentacao', categoria: 'Alimentação', limite: 900 },
      { id: 'orc-lazer', categoria: 'Lazer', limite: 300 },
      { id: 'orc-transporte', categoria: 'Transporte', limite: 400 },
    ],
    contas: [
      { id: 'conta-nubank', nome: 'Nubank', tipo: 'CARTAO', fechamento: 5, vencimento: 12 },
      { id: 'conta-itau', nome: 'Conta Itaú', tipo: 'CONTA', fechamento: null, vencimento: null },
    ],
    regras: [
      { id: 'regra-uber', termo: 'uber', categoria: 'Transporte' },
      { id: 'regra-padaria', termo: 'padaria', categoria: 'Alimentação' },
    ],
    metas: {},
    depositos: [],
    notas: [],
    lugares: [],
    marcadores: ['Romântico', 'Barato', 'Com as crianças', 'Vista bonita'],
    filmes: [],
    compras: [],
    fotos: [],
    imagens: {},
    moedas: 0,
    movimentos: [],
    premios: [
      { id: 'premio-cinema', nome: 'Cinema com pipoca', descricao: 'Sessão à escolha de quem resgatar', preco: 80, ativo: true },
      { id: 'premio-jantar', nome: 'Jantar fora', descricao: 'Restaurante novo da lista', preco: 150, ativo: true },
      { id: 'premio-folga', nome: 'Dia sem louça', descricao: 'O outro lava tudo', preco: 40, ativo: true },
    ],
    resgates: [],
    bancos: [
      {
        id: 'banco-1', conexaoId: 'conexao-demo', instituicao: 'Banco Exemplo', nome: 'Conta corrente',
        tipo: 'BANK', numero: '•••• 1234', saldo: 3245.67, moeda: 'BRL', status: 'UPDATED',
        atualizadoEm: hoje.toISOString(),
      },
      {
        id: 'banco-2', conexaoId: 'conexao-demo', instituicao: 'Banco Exemplo', nome: 'Poupança',
        tipo: 'BANK', numero: '•••• 9876', saldo: 12500, moeda: 'BRL', status: 'UPDATED',
        atualizadoEm: hoje.toISOString(),
      },
    ],
    leiturasNoMes: 2,
  };

  const gasto = (
    data: string,
    item: string,
    categoria: string,
    valor: number,
    extras: Partial<Gasto> = {},
  ): Gasto | null => {
    if (data > hojeIso) {
      return null;
    }
    const novo: Gasto = {
      id: novoId('gasto'),
      data,
      reciboId: novoId('recibo'),
      estabelecimento: '',
      item,
      categoria,
      valor,
      tipo: 'Variavel',
      origem: 'Manual',
      usuario: ana.email,
      registradoEm: `${data}T12:00:00Z`,
      contaId: null,
      parcela: null,
      parcelas: null,
      moedaOriginal: null,
      valorOriginal: null,
      ...extras,
    };
    estado.gastos.push(novo);
    return novo;
  };

  for (let deslocamento = -2; deslocamento <= 0; deslocamento++) {
    const dia = (d: number) => diaDoMes(hoje, deslocamento, d);
    const mes = dia(1).slice(0, 7);
    // Pequena variacao de um mes para o outro, para os graficos terem forma.
    const f = [0.92, 1.08, 1][deslocamento + 2];

    estado.metas[mes] = 4500;
    estado.depositos.push({
      id: novoId('deposito'), data: dia(1), valor: 4000, usuario: ana.email, registradoEm: `${dia(1)}T09:00:00Z`,
    });
    estado.receitas.push(
      { id: novoId('receita'), data: dia(5), descricao: 'Salário Ana', categoria: 'Salário', valor: 6500, usuario: ana.email },
      { id: novoId('receita'), data: dia(5), descricao: 'Salário Beto', categoria: 'Salário', valor: 4200, usuario: beto.email },
    );
    if (deslocamento === -1) {
      estado.receitas.push({
        id: novoId('receita'), data: dia(18), descricao: 'Freela de design', categoria: 'Extra', valor: 800, usuario: ana.email,
      });
    }

    // Contas fixas: meses passados todos pagos; neste, as que ja venceram (menos a academia).
    for (const fixa of estado.contasFixas) {
      const vence = dia(fixa.dia);
      if (vence > hojeIso || (deslocamento === 0 && fixa.id === 'fixa-academia')) {
        continue;
      }
      gasto(vence, fixa.descricao, fixa.categoria, fixa.valor, { tipo: 'Fixo', origem: 'Conta fixa' });
      estado.contasPagas.add(`${fixa.id}|${mes}`);
    }

    gasto(dia(3), 'Compras do mês', 'Alimentação', round(412.3 * f), { estabelecimento: 'Supermercado Bom Preço', contaId: 'conta-itau' });
    gasto(dia(7), 'Uber Centro', 'Transporte', round(23.5 * f), { contaId: 'conta-nubank' });
    gasto(dia(12), 'Remédios', 'Farmácia/Saúde', round(89.9 * f), { estabelecimento: 'Drogaria Popular' });
    const jantar = gasto(dia(14), 'Jantar de sexta', 'Lazer', round(156 * f), {
      estabelecimento: 'Cantina da Nonna', contaId: 'conta-nubank',
    });
    if (jantar) {
      estado.partes[jantar.id] = { [ana.email]: jantar.valor / 2, [beto.email]: jantar.valor / 2 };
    }
    gasto(dia(17), 'Feira e hortifruti', 'Alimentação', round(187.45 * f), { estabelecimento: 'Feira da Praça' });
    gasto(dia(20), 'Combustível', 'Transporte', round(250 * f), { estabelecimento: 'Posto Shell', contaId: 'conta-nubank', usuario: beto.email });
    gasto(dia(23), 'Cinema', 'Lazer', round(64 * f), { contaId: 'conta-nubank', usuario: beto.email });
    gasto(dia(26), 'Padaria', 'Alimentação', round(38.5 * f), { estabelecimento: 'Padaria Pão Quente' });
    gasto(dia(28), 'Ração do Thor', 'Pets', round(129.9 * f), { estabelecimento: 'Pet Shop Amigo' });

    // Fone em 3x no cartao, comprado ha dois meses: uma parcela por mes.
    gasto(dia(8), 'Fone de ouvido', 'Gastos Extras', 133, {
      contaId: 'conta-nubank', parcela: deslocamento + 3, parcelas: 3, reciboId: 'recibo-fone',
    });
  }

  // Compra em dolar no mes passado.
  gasto(diaDoMes(hoje, -1, 21), 'Livro importado', 'Gastos Extras', 110, {
    moedaOriginal: 'USD', valorOriginal: 20, origem: 'Manual',
  });

  estado.notas.push(
    nota(diaDoMes(hoje, 0, 15), 'Aniversário da Bia', 'Comprar presente até sexta', 0, 'Outros', ana.email),
    nota(diaDoMes(hoje, 0, 25), 'Revisão do carro', 'Agendada na oficina do bairro', 450, 'Transporte', beto.email),
    nota(diaDoMes(hoje, 1, 2), 'IPVA', 'Primeira parcela', 380, 'Transporte', ana.email),
  );

  estado.lugares.push(
    lugar('Cantina da Nonna', 'Restaurante', 'Vila Madalena, São Paulo', 5, 'Melhor lasanha da cidade', diaDoMes(hoje, -1, 14), ['Romântico'], 156, ana.email),
    lugar('Parque Ibirapuera', 'Passeio', 'São Paulo', 4, 'Piquenique no domingo', diaDoMes(hoje, -2, 9), ['Barato', 'Com as crianças'], 0, beto.email),
    lugar('Café do Mirante', 'Café', 'Campos do Jordão', 5, 'Vista incrível no fim de tarde', diaDoMes(hoje, -2, 22), ['Vista bonita'], 48, ana.email),
  );

  estado.filmes.push(
    filme('Divertida Mente 2', '2024', 'A Riley entra na adolescência e novas emoções aparecem.', true, { 'Ana': 5, 'Beto': 4 }),
    filme('Duna: Parte Dois', '2024', 'Paul Atreides se une aos Fremen.', true, { 'Beto': 5 }),
    filme('Ainda Estou Aqui', '2024', 'A história de Eunice Paiva.', false, {}),
  );

  estado.compras.push(
    compra('Leite integral', '6 caixas', 'Compras', false, 'Italac'),
    compra('Café 500g', '2', 'Compras', false, 'Pilão'),
    compra('Detergente', '3', 'Compras', true, ''),
    compra('Air fryer', '1', 'Desejos', false, 'Mondial'),
    compra('Kindle', '1', 'Desejos', false, 'Amazon'),
  );

  const fotos: [number, string, string, string, string][] = [
    [-2, '#f5a3c0', '#b85c7d', '🏖️', 'Praia no feriado'],
    [-1, '#a3c4f5', '#5c6fb8', '🎂', 'Aniversário do Beto'],
    [0, '#b7e0a5', '#5e9a4b', '🐶', 'Thor no parque'],
  ];
  for (const [deslocamento, c1, c2, emoji, legenda] of fotos) {
    const assetId = novoId('foto');
    estado.imagens[assetId] = fotoDeExemplo(c1, c2, emoji, legenda);
    estado.fotos.push({
      id: novoId('feed'), mesKey: diaDoMes(hoje, deslocamento, 1).slice(0, 7), assetId, legenda,
      usuario: ana.email, criadoEm: `${diaDoMes(hoje, deslocamento, 10)}T18:00:00Z`,
    });
  }

  estado.movimentos.push(
    { id: novoId('moeda'), data: diaDoMes(hoje, -1, 28), valor: 50, motivo: 'Meta do mês cumprida', usuario: ana.email },
    { id: novoId('moeda'), data: diaDoMes(hoje, -1, 30), valor: -40, motivo: 'Resgate: Dia sem louça', usuario: beto.email },
    { id: novoId('moeda'), data: diaDoMes(hoje, 0, 2), valor: 110, motivo: 'Cofrinho do mês', usuario: ana.email },
  );
  estado.moedas = estado.movimentos.reduce((s, m) => s + m.valor, 0);
  estado.resgates.push({
    id: novoId('resgate'), premio: 'Dia sem louça', preco: 40, usuario: beto.email, status: 'RESGATADO',
    data: diaDoMes(hoje, -1, 30), saldo: 10,
  });

  return estado;
}

function round(valor: number): number {
  return Math.round(valor * 100) / 100;
}

function nota(data: string, titulo: string, texto: string, valor: number, categoria: string, usuario: string): Nota {
  return { id: novoId('nota'), titulo, texto, data, valor, categoria, gastoId: null, usuario, criadoEm: `${data}T08:00:00Z` };
}

function lugar(
  nome: string, categoria: string, localizacao: string, nota: number, comentario: string, data: string,
  marcacoes: string[], valor: number, usuario: string,
): Lugar {
  return { id: novoId('lugar'), nome, categoria, localizacao, nota, comentario, data, marcacoes, fotoAssetId: null, temFoto: false, valor, usuario };
}

function filme(titulo: string, ano: string, sinopse: string, assistido: boolean, avaliacoes: Record<string, number>): Filme {
  return { id: novoId('filme'), tmdbId: novoId('tmdb'), titulo, ano, poster: '', nota: 7.8, sinopse, assistido, avaliacoes, usuario: 'ana@demo.piggu.app' };
}

function compra(item: string, quantidade: string, lista: 'Compras' | 'Desejos', comprado: boolean, marca: string): ItemDeCompra {
  return { id: novoId('compra'), item, quantidade, lista, comprado, marca, imagem: '', codigo: '', usuario: 'ana@demo.piggu.app' };
}

/** Catalogo de filmes para a busca da demo (o TMDB nao e chamado). */
export const FILMES_DO_CATALOGO = [
  { tmdbId: 'demo-1', titulo: 'Central do Brasil', ano: '1998', poster: '', nota: 8, sinopse: 'Dora acompanha um menino em busca do pai.' },
  { tmdbId: 'demo-2', titulo: 'Cidade de Deus', ano: '2002', poster: '', nota: 8.4, sinopse: 'A vida na Cidade de Deus entre os anos 60 e 80.' },
  { tmdbId: 'demo-3', titulo: 'O Auto da Compadecida', ano: '2000', poster: '', nota: 8.5, sinopse: 'João Grilo e Chicó no sertão.' },
  { tmdbId: 'demo-4', titulo: 'Toy Story', ano: '1995', poster: '', nota: 8, sinopse: 'Brinquedos ganham vida quando ninguém vê.' },
  { tmdbId: 'demo-5', titulo: 'Interestelar', ano: '2014', poster: '', nota: 8.4, sinopse: 'Uma viagem pelo espaço para salvar a humanidade.' },
];

export const PRODUTOS_DO_CATALOGO = [
  { codigo: '7891000100103', nome: 'Leite condensado', marca: 'Moça', quantidade: '395 g', imagem: '' },
  { codigo: '7896004000015', nome: 'Café torrado e moído', marca: 'Pilão', quantidade: '500 g', imagem: '' },
  { codigo: '7891910000197', nome: 'Açúcar refinado', marca: 'União', quantidade: '1 kg', imagem: '' },
  { codigo: '7896036090244', nome: 'Arroz agulhinha', marca: 'Tio João', quantidade: '5 kg', imagem: '' },
];
