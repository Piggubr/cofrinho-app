/**
 * Tipos espelhando os DTOs do backend.
 *
 * <p>Os nomes seguem o que a API devolve, em portugues. Traduzir aqui criaria um
 * dicionario a mais para manter sincronizado toda vez que um campo mudasse.</p>
 */

/** ADMIN opera a instalacao; TITULAR e o dono da familia; MEMBRO foi convidado. */
export type PigguRole = 'ADMIN' | 'TITULAR' | 'MEMBRO';

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
  preferencias: Preferencias;
  plano: Plano;
  /** Ate quando o Premium (da familia) vale; nulo no gratuito. */
  premiumAte: string | null;
  /** Familia a que a conta pertence. */
  familia: string;
}

/** Familia de quem esta logado; convites so chegam para o titular. */
export interface Familia {
  id: string;
  nome: string;
  plano: Plano;
  membros: MembroDaFamilia[];
  convites: ConviteDaFamilia[];
}

export interface MembroDaFamilia {
  id: string;
  nome: string;
  email: string;
  foto: string | null;
  papel: PigguRole;
}

export interface ConviteDaFamilia {
  id: string;
  email: string;
  venceEm: string;
  /** Nome da familia que convidou. */
  familia: string;
}

export type Plano = 'GRATUITO' | 'PREMIUM';
export type Periodo = 'MENSAL' | 'ANUAL';

/** Precos em reais, ja formatados ("19,90"). */
export interface Precos {
  mensal: string;
  anual: string;
}

/** Tela de planos: o que vale agora e o que cada plano inclui. */
export interface InfoDoPlano {
  plano: Plano;
  premiumAte: string | null;
  origem: 'WEB' | 'APP_STORE' | 'PLAY_STORE' | null;
  /** Ate quando da para desistir com o dinheiro de volta (7 dias); nulo fora do prazo. */
  reembolsoAte: string | null;
  /** Dias gratis ao assinar (uma vez por conta); 0 quando ja usou. */
  diasDeTeste: number;
  assinaturaDisponivel: boolean;
  site: Precos;
  app: Precos;
  gratuito: string[];
  premium: string[];
}

/** Moeda em que os valores aparecem e a cotacao que fica no topo do app. */
export interface Preferencias {
  moeda: string;
  moedaConversao: string;
  mostrarCotacao: boolean;
  /** Fuso IANA da pessoa; conta nova nasce em America/Sao_Paulo. */
  fuso?: string;
  /** Idioma da interface; hoje so pt-BR. */
  idioma?: string;
}

export interface MoedaDisponivel {
  codigo: string;
  nome: string;
}

/** O refresh nao vem no corpo: fica num cookie HttpOnly que o JavaScript nao le. */
export interface ParDeTokens {
  accessToken: string;
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
  /** OCR: leitor proprio, a foto nao saiu do servidor. GEMINI: leitura por IA. */
  origem: 'OCR' | 'GEMINI';
  /** O que conferir com mais cuidado; nulo quando a soma bateu com o total do cupom. */
  aviso: string | null;
  /** Leituras gratis que sobram no mes; nulo no Premium (sem limite). */
  leiturasRestantes: number | null;
}

/** Leituras de nota pela foto no mes da familia. */
export interface UsoDeLeituras {
  usadas: number;
  limite: number;
  /** Nulo no Premium: sem limite. */
  restantes: number | null;
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
  de: string;
  para: string;
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
  /** Banco conectado ao qual a conta pertence; e ele que se desconecta. */
  conexaoId: string;
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
