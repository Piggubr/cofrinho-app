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

/** DDI de cada pais de {@link CODIGOS}; os do plano norte-americano dividem o +1. */
const DDIS = new Map(
  (
    'AD376 AE971 AF93 AG1 AI1 AL355 AM374 AO244 AR54 AS1 AT43 AU61 AW297 AX358 AZ994 BA387 BB1 ' +
    'BD880 BE32 BF226 BG359 BH973 BI257 BJ229 BL590 BM1 BN673 BO591 BQ599 BR55 BS1 BT975 BW267 ' +
    'BY375 BZ501 CA1 CC61 CD243 CF236 CG242 CH41 CI225 CK682 CL56 CM237 CN86 CO57 CR506 CU53 ' +
    'CV238 CW599 CX61 CY357 CZ420 DE49 DJ253 DK45 DM1 DO1 DZ213 EC593 EE372 EG20 EH212 ER291 ' +
    'ES34 ET251 FI358 FJ679 FK500 FM691 FO298 FR33 GA241 GB44 GD1 GE995 GF594 GG44 GH233 GI350 ' +
    'GL299 GM220 GN224 GP590 GQ240 GR30 GT502 GU1 GW245 GY592 HK852 HN504 HR385 HT509 HU36 ID62 ' +
    'IE353 IL972 IM44 IN91 IQ964 IR98 IS354 IT39 JE44 JM1 JO962 JP81 KE254 KG996 KH855 KI686 ' +
    'KM269 KN1 KP850 KR82 KW965 KY1 KZ7 LA856 LB961 LC1 LI423 LK94 LR231 LS266 LT370 LU352 LV371 ' +
    'LY218 MA212 MC377 MD373 ME382 MF590 MG261 MH692 MK389 ML223 MM95 MN976 MO853 MP1 MQ596 ' +
    'MR222 MS1 MT356 MU230 MV960 MW265 MX52 MY60 MZ258 NA264 NC687 NE227 NF672 NG234 NI505 NL31 ' +
    'NO47 NP977 NR674 NU683 NZ64 OM968 PA507 PE51 PF689 PG675 PH63 PK92 PL48 PM508 PN64 PR1 ' +
    'PS970 PT351 PW680 PY595 QA974 RE262 RO40 RS381 RU7 RW250 SA966 SB677 SC248 SD249 SE46 SG65 ' +
    'SH290 SI386 SJ47 SK421 SL232 SM378 SN221 SO252 SR597 SS211 ST239 SV503 SX1 SY963 SZ268 TC1 ' +
    'TD235 TG228 TH66 TJ992 TK690 TL670 TM993 TN216 TO676 TR90 TT1 TV688 TW886 TZ255 UA380 ' +
    'UG256 US1 UY598 UZ998 VA39 VC1 VE58 VG1 VI1 VN84 VU678 WF681 WS685 YE967 YT262 ZA27 ZM260 ZW263'
  )
    .split(' ')
    .map((par) => [par.slice(0, 2), par.slice(2)] as const),
);

/** Codigo de discagem do pais, sem o "+" ("55" no Brasil). */
export function ddiDoPais(codigo: string): string {
  return DDIS.get(codigo) ?? '';
}

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
