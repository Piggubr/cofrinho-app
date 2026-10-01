/**
 * Tipos espelhando os DTOs do backend.
 *
 * <p>Os nomes seguem o que a API devolve, em portugues. Traduzir aqui criaria um
 * dicionario a mais para manter sincronizado toda vez que um campo mudasse.</p>
 */

export type PigguRole = 'ADMIN' | 'BEATRIZ' | 'FAMILIAR';

export interface Usuario {
  id: string;
  email: string;
  nome: string;
  primeiroNome: string;
  apelido: string | null;
  foto: string | null;
  role: PigguRole;
  ativo: boolean;
  permissoes: Record<string, unknown>;
}

export interface ParDeTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  usuario: Usuario;
}

/** Corpo unico de erro devolvido por todos os servicos. */
export interface ApiError {
  erro: string;
  codigo: string;
  campos: { campo: string; mensagem: string }[];
  momento: string;
}

export interface Gasto {
  id: string;
  data: string;
  reciboId: string;
  estabelecimento: string;
  item: string;
  categoria: string;
  valor: number;
  tipo: string;
  origem: string;
  usuario: string;
  registradoEm: string;
}

export interface ItemDeGasto {
  item: string;
  categoria?: string | null;
  valor: number;
  tipo?: string | null;
}

export interface NovoLancamento {
  data: string;
  estabelecimento?: string | null;
  reciboId?: string | null;
  origem?: string | null;
  itens: ItemDeGasto[];
}

export interface ReciboLido {
  reciboId: string;
  estabelecimento: string;
  data: string;
  itens: { item: string; categoria: string; valor: number }[];
}

export interface Deposito {
  id: string;
  data: string;
  valor: number;
  usuario: string;
  registradoEm: string;
}

export interface Cofrinho {
  depositos: Deposito[];
  totalDepositos: number;
  totalGastos: number;
  saldo: number;
}

export interface Nota {
  id: string;
  titulo: string;
  texto: string;
  data: string | null;
  valor: number;
  categoria: string;
  gastoId: string | null;
  usuario: string;
  criadoEm: string;
}

export interface Produto {
  chave: string;
  nome: string;
  categoria: string;
  ultimo: number;
  menor: number;
  maior: number;
  media: number;
  compras: number;
  data: string | null;
  variacao: number;
}

export interface Cotacao {
  taxa: number;
  data: string;
  fonte: string;
  estimativa: boolean;
  desatualizada: boolean;
}

export interface SaldoDeMoedas {
  saldo: number;
  historico: MovimentoDeMoedas[];
}

export interface MovimentoDeMoedas {
  id: string;
  data: string;
  valor: number;
  motivo: string;
  usuario: string;
}

export interface Premio {
  id: string;
  nome: string;
  descricao: string;
  preco: number;
  ativo: boolean;
}

export interface Resgate {
  id: string;
  premio: string;
  preco: number;
  usuario: string;
  status: string;
  data: string;
  saldo: number;
}

export interface Lugar {
  id: string;
  nome: string;
  categoria: string;
  localizacao: string;
  nota: number;
  comentario: string;
  data: string;
  marcacoes: string[];
  fotoAssetId: string | null;
  temFoto: boolean;
  valor: number;
  usuario: string;
}

export interface NovoLugar {
  nome: string;
  categoria?: string | null;
  localizacao?: string | null;
  nota: number;
  comentario?: string | null;
  data: string;
  marcacoes?: string[];
  valor?: number | null;
  imageBase64?: string | null;
  mimeType?: string | null;
}

export interface Filme {
  id: string;
  tmdbId: string;
  titulo: string;
  ano: string;
  poster: string;
  nota: number;
  sinopse: string;
  assistido: boolean;
  avaliacoes: Record<string, number>;
  usuario: string;
}

/** Filme vindo do TMDB, ainda fora da lista. */
export interface FilmeDoCatalogo {
  tmdbId: string;
  titulo: string;
  ano: string;
  poster: string;
  nota: number;
  sinopse: string;
}

export interface ItemDeCompra {
  id: string;
  item: string;
  quantidade: string;
  lista: 'Compras' | 'Desejos';
  comprado: boolean;
  marca: string;
  imagem: string;
  codigo: string;
  usuario: string;
}

export interface NovoItemDeCompra {
  item: string;
  quantidade?: string | null;
  lista?: string | null;
  marca?: string | null;
  imagem?: string | null;
  codigo?: string | null;
}

export interface ProdutoDoCatalogo {
  codigo: string;
  nome: string;
  marca: string;
  quantidade: string;
  imagem: string;
}

export interface FotoDoFeed {
  id: string;
  mesKey: string;
  assetId: string;
  legenda: string;
  usuario: string;
  criadoEm: string;
}

export interface Arquivo {
  id: string;
  contentType: string;
  tamanho: number;
  contexto: string;
  criadoEm: string;
}

/** Conta de um banco conectado por Open Finance, com o saldo da ultima sincronizacao. */
export interface ContaBancaria {
  id: string;
  instituicao: string;
  nome: string;
  tipo: string;
  numero: string;
  saldo: number;
  moeda: string;
  status: string;
  atualizadoEm: string | null;
}

export interface ConnectToken {
  accessToken: string;
  sandbox: boolean;
}
