let contas = [
  { id: 1, nome: 'Água', dia: 10, valor: 18 },
  { id: 2, nome: 'Internet', dia: 5, valor: 25 },
  { id: 3, nome: 'Eletricidade', dia: 20, valor: 32 }
];

let nextContaId = 4;

let eventos = [
  { id: 1, nome: 'Show', data: '2026-08-16', valor: 45, pago: true, categoria: 'Lazer', foto: null, gastoId: null },
  { id: 2, nome: 'Consulta médica', data: '2026-08-28', valor: null, pago: false, categoria: 'Farmácia/Saúde', foto: null, gastoId: null }
];

let nextEventoId = 3;

let pagamentos = new Set(['2026-08-conta1']);

let fotos = [];
let nextFotoId = 1;

let notas = [];
let nextNotaId = 1;

let cofrinhoMovimentos = [];

let metas = {};
let fofocoins = { saldo: 0, historico: [] };
let compras = [];
let filmes = [];
let produtos = [];
let perfilAtual = '';
let usuarioAtual = '';
let nomeAtual = '';
let apelidoAtual = '';
let fotoAtual = '';
let minhasCartinhas = [];
let meusDepositos = [];
let cofrinhoResumo = { saldo: 0 };

let viewDate = new Date(2026, 7, 1);

const CATEGORIAS = {
  'Alimentação': '#FF6B6B',
  'Transporte': '#4ECDC4',
  'Lazer': '#45B7D1',
  'Saúde': '#96CEB4',
  'Educação': '#FFEEAD',
  'Compras': '#D4A5A5',
  'Casa': '#9B59B6',
  'Outros': '#95A5A6'
};
