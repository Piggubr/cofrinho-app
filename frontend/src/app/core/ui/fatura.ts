import { mesKey } from './datas';

/** Fechamento padrao: 7 dias antes do vencimento, voltando ao mes anterior quando preciso. */
export function fechamentoPadrao(vencimento: number): number {
  return vencimento > 7 ? vencimento - 7 : vencimento + 24;
}

/**
 * Em que fatura cai uma compra (AAAA-MM-DD), com as mesmas regras do servidor: antes do
 * dia do fechamento, na fatura do mes; do fechamento em diante, na do mes seguinte.
 *
 * @returns o mes da fatura (AAAA-MM) e o vencimento dela (AAAA-MM-DD)
 */
export function faturaDaCompra(
  fechamento: number,
  vencimento: number,
  compra: string,
): { mes: string; vencimento: string } {
  const [ano, mes, dia] = compra.split('-').map(Number);
  const fechaNoMes = Math.min(fechamento, diasNoMes(ano, mes - 1));
  const daFatura = new Date(ano, mes - 1 + (dia < fechaNoMes ? 0 : 1), 1);
  const doVencimento = new Date(daFatura.getFullYear(), daFatura.getMonth() + (vencimento > fechamento ? 0 : 1), 1);
  const diaDoVencimento = Math.min(vencimento, diasNoMes(doVencimento.getFullYear(), doVencimento.getMonth()));
  return {
    mes: mesKey(daFatura),
    vencimento: `${mesKey(doVencimento)}-${String(diaDoVencimento).padStart(2, '0')}`,
  };
}

function diasNoMes(ano: number, mesBaseZero: number): number {
  return new Date(ano, mesBaseZero + 1, 0).getDate();
}
