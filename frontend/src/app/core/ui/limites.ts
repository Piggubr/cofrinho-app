import { dataCurta, hojeIso } from './datas';

/**
 * Limites de valor e de data iguais no backend e nos apps (D-189, D-194).
 *
 * <p>O app confere antes de enviar para mostrar um texto proprio, com acento, em vez do
 * erro cru do servidor; quem decide de verdade continua sendo o backend.</p>
 */
export const TETO_DE_GASTO = 1_000_000;
/** Receita, valor a receber e deposito no cofrinho. */
export const TETO_DE_ENTRADA = 10_000_000;
/** Orcamento por categoria, limite do mes e meta do mes. */
export const TETO_DE_LIMITE = 1_000_000;

/** Texto do erro de valor, ou nulo quando o valor serve. */
export function erroDeValor(valor: number | null | undefined, teto: number): string | null {
  if (valor === null || valor === undefined || !Number.isFinite(valor) || valor < 0.01) {
    return $localize`Digite um valor a partir de R$ 0,01.`;
  }
  if (valor > teto) {
    return teto === TETO_DE_ENTRADA
      ? $localize`O valor máximo é R$ 10.000.000,00.`
      : $localize`O valor máximo é R$ 1.000.000,00.`;
  }
  return null;
}

/** Hoje deslocado em anos, no formato AAAA-MM-DD (29/02 vira 28/02 fora do bissexto). */
function somarAnos(hoje: string, anos: number): string {
  const [ano, mes, dia] = hoje.split('-').map(Number);
  const ultimo = new Date(ano + anos, mes, 0).getDate();
  return `${ano + anos}-${String(mes).padStart(2, '0')}-${String(Math.min(dia, ultimo)).padStart(2, '0')}`;
}

/** Fora de 01/01/2000 a hoje + 2 anos o backend recusa: o app avisa antes, com o mesmo texto. */
export function erroDeData(data: string, hoje = hojeIso()): string | null {
  const limite = somarAnos(hoje, 2);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(data ?? '') || data < '2000-01-01' || data > limite) {
    return $localize`Confira a data: use uma data entre 2000 e ${limite.slice(0, 4)}:ano:.`;
  }
  return null;
}

/**
 * Data futura, ou de mais de 1 ano atras: pode ser engano de digitacao. No "a receber" a
 * data combinada costuma ser futura, entao la so pergunta passando de 1 ano para frente.
 */
export function dataPedeConfirmacao(data: string, hoje = hojeIso(), aceitaFuturo = false): boolean {
  return data > (aceitaFuturo ? somarAnos(hoje, 1) : hoje) || data < somarAnos(hoje, -1);
}

/**
 * Pergunta antes de lancar numa data fora do comum. Verdadeiro quando pode seguir.
 *
 * <p>shortcut: usa o confirm() do navegador, como o resto do site, entao os botoes sao
 * OK e Cancelar e nao "Lançar" e "Corrigir a data"; trocar quando o site tiver um
 * dialogo proprio.</p>
 */
export function confirmarData(data: string, hoje = hojeIso(), aceitaFuturo = false): boolean {
  return !dataPedeConfirmacao(data, hoje, aceitaFuturo) || confirm($localize`Essa data é ${dataCurta(data)}:data:. Lançar mesmo assim?`);
}
