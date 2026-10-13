import { cpfValido, digitosDoCpf, formatarCpf } from './cpf';

/**
 * Paises e o documento que o Piggu pede em cada um para manter uma conta por pessoa.
 *
 * <p>Espelho de {@code DocumentosDeIdentidade} no identity: mudou la, muda aqui. No Brasil
 * e o CPF; fora, so os paises com numero nacional de identidade de formato conhecido e que
 * nao e sigiloso. Nos demais a conta segue sem documento. O backend confere de novo.</p>
 */

/** ISO 3166 (o mesmo do backend), sem os territorios desabitados. */
const CODIGOS =
  'AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS ' +
  'BT BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE ' +
  'EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GT GU GW GY HK HN ' +
  'HR HT HU ID IE IL IM IN IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI ' +
  'LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA ' +
  'NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU ' +
  'RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TG TH TJ TK TL TM TN ' +
  'TO TR TT TV TW TZ UA UG US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW';

export const BRASIL = 'BR';

export interface Pais {
  codigo: string;
  nome: string;
}

/** Todos os paises pelo nome em portugues, com o Brasil primeiro. */
export function listaDePaises(): Pais[] {
  const nomes = new Intl.DisplayNames(['pt-BR'], { type: 'region' });
  const paises = CODIGOS.split(' ').map((codigo) => ({ codigo, nome: nomes.of(codigo) ?? codigo }));
  const outros = paises
    .filter((pais) => pais.codigo !== BRASIL)
    .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
  return [paises.find((pais) => pais.codigo === BRASIL)!, ...outros];
}

export interface DocumentoDoPais {
  /** Como o pais chama o documento ("NIF", "DNI/NIE"...). */
  nome: string;
  exemplo: string;
  tamanhoMaximo: number;
  numerico: boolean;
  /** Mascara enquanto digita; so o CPF tem. */
  formatar: (texto: string) => string;
  /** O numero limpo, como vai para o backend. */
  limpar: (texto: string) => string;
  valido: (texto: string) => boolean;
}

const limpar = (texto: string) => texto.replace(/[.\-/\s]/g, '').toUpperCase();
const digitos = (n: string, de: number, ate: number) => new RegExp(`^\\d{${de},${ate}}$`).test(n);

function documento(
  nome: string,
  exemplo: string,
  formato: RegExp,
  conta: (n: string) => boolean = () => true,
  numerico = true,
): DocumentoDoPais {
  return {
    nome,
    exemplo,
    tamanhoMaximo: 24,
    numerico,
    formatar: (texto) => texto,
    limpar,
    valido: (texto) => {
      const n = limpar(texto);
      return formato.test(n) && conta(n);
    },
  };
}

const CPF: DocumentoDoPais = {
  nome: 'CPF',
  exemplo: '000.000.000-00',
  tamanhoMaximo: 14,
  numerico: true,
  formatar: formatarCpf,
  limpar: digitosDoCpf,
  valido: cpfValido,
};

const n = (texto: string, i: number) => texto.charCodeAt(i) - 48;

function nifValido(t: string): boolean {
  let soma = 0;
  for (let i = 0; i < 8; i++) soma += n(t, i) * (9 - i);
  const dv = 11 - (soma % 11);
  return (dv >= 10 ? 0 : dv) === n(t, 8);
}

function rutValido(t: string): boolean {
  const corpo = t.slice(0, -1);
  let soma = 0;
  let peso = 2;
  for (let i = corpo.length - 1; i >= 0; i--) {
    soma += n(corpo, i) * peso;
    peso = peso === 7 ? 2 : peso + 1;
  }
  const resto = 11 - (soma % 11);
  const dv = resto === 11 ? '0' : resto === 10 ? 'K' : String(resto);
  return dv === t.at(-1);
}

function cedulaUruguaiaValida(t: string): boolean {
  const cheio = t.padStart(8, '0');
  const pesos = [2, 9, 8, 7, 6, 3, 4];
  const soma = pesos.reduce((total, peso, i) => total + n(cheio, i) * peso, 0);
  return (10 - (soma % 10)) % 10 === n(cheio, 7);
}

function cedulaEquatorianaValida(t: string): boolean {
  const provincia = Number(t.slice(0, 2));
  if (((provincia < 1 || provincia > 24) && provincia !== 30) || n(t, 2) >= 6) return false;
  let soma = 0;
  for (let i = 0; i < 9; i++) {
    const produto = n(t, i) * (i % 2 === 0 ? 2 : 1);
    soma += produto > 9 ? produto - 9 : produto;
  }
  return (10 - (soma % 10)) % 10 === n(t, 9);
}

function dniValido(t: string): boolean {
  const numero = t.slice(0, -1).replace('X', '0').replace('Y', '1').replace('Z', '2');
  return 'TRWAGMYFPDXBNJZSQVHLCKE'[Number(numero) % 23] === t.at(-1);
}

function curpValida(t: string): boolean {
  const dicionario = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';
  let soma = 0;
  for (let i = 0; i < 17; i++) soma += dicionario.indexOf(t[i]) * (18 - i);
  return (10 - (soma % 10)) % 10 === n(t, 17);
}

function codiceFiscaleValido(t: string): boolean {
  const impares = [
    1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23,
  ];
  let soma = 0;
  for (let i = 0; i < 15; i++) {
    const c = t[i];
    const valor = /\d/.test(c) ? n(t, i) : c.charCodeAt(0) - 65;
    soma += i % 2 === 0 ? impares[valor] : valor;
  }
  return String.fromCharCode(65 + (soma % 26)) === t[15];
}

const CURP =
  /^[A-Z][AEIOUX][A-Z]{2}\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[HMX](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\d$/;
const CODICE_FISCALE =
  /^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/;

const DOCUMENTOS: Record<string, DocumentoDoPais> = {
  BR: CPF,
  AR: documento('DNI', '30.123.456', /^\d{7,8}$/),
  BO: documento('Cédula de Identidad', '1234567', /^\d{5,9}$/),
  CL: documento('RUN/RUT', '12.345.678-5', /^\d{7,8}[0-9K]$/, rutValido, false),
  CO: documento('Cédula de Ciudadanía', '1.020.304.050', /^\d{6,10}$/),
  EC: documento('Cédula de Identidad', '1710034065', /^\d{10}$/, cedulaEquatorianaValida),
  ES: documento('DNI/NIE', '12345678Z', /^(\d{8}|[XYZ]\d{7})[A-Z]$/, dniValido, false),
  IT: documento('Codice Fiscale', 'RSSMRA85T10A562S', CODICE_FISCALE, codiceFiscaleValido, false),
  MX: documento('CURP', 'GOMC800101HDFRRR00', CURP, curpValida, false),
  PE: documento('DNI', '12345678', /^\d{8}$/),
  PT: documento('NIF', '123456789', /^[1235689]\d{8}$/, nifValido),
  PY: documento('Cédula de Identidad', '1234567', /^\d{5,8}$/),
  UY: documento('Cédula de Identidad', '1.234.567-2', /^\d{7,8}$/, cedulaUruguaiaValida),
  VE: {
    ...documento('Cédula de Identidad', 'V-12.345.678', /^\d{6,9}$/),
    numerico: false,
    limpar: (texto) => limpar(texto).replace(/^[VE]/, ''),
    valido: (texto) => digitos(limpar(texto).replace(/^[VE]/, ''), 6, 9),
  },
};

/** O documento pedido no pais; nulo quando la o Piggu nao pede nenhum. */
export function documentoDoPais(pais: string | null | undefined): DocumentoDoPais | null {
  return (pais && DOCUMENTOS[pais]) || null;
}
