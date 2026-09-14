/** Chave de mes no formato AAAA-MM, que a API usa em metas e no feed. */
export function mesKey(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

/** Data no formato AAAA-MM-DD, sem fuso: a API trabalha com dia civil. */
export function dataIso(data: Date): string {
  return `${mesKey(data)}-${String(data.getDate()).padStart(2, '0')}`;
}

export function hojeIso(): string {
  return dataIso(new Date());
}

const NOMES_DE_MES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
];

/** "2026-09" vira "Setembro de 2026". */
export function mesPorExtenso(chave: string): string {
  const [ano, mes] = chave.split('-');
  const indice = Number(mes) - 1;
  return indice >= 0 && indice < 12 ? `${NOMES_DE_MES[indice]} de ${ano}` : chave;
}

/** "2026-09-10" vira "10/09/2026", sem passar por Date para nao deslocar o fuso. */
export function dataCurta(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const [ano, mes, dia] = iso.slice(0, 10).split('-');
  return dia ? `${dia}/${mes}/${ano}` : iso;
}

export function primeiroDiaDoMes(data: Date): Date {
  return new Date(data.getFullYear(), data.getMonth(), 1);
}

export function somarMeses(data: Date, meses: number): Date {
  return new Date(data.getFullYear(), data.getMonth() + meses, 1);
}

/** Dias do mes, com os vazios do inicio para alinhar na grade da semana. */
export function gradeDoMes(referencia: Date): (number | null)[] {
  const ano = referencia.getFullYear();
  const mes = referencia.getMonth();
  const primeiroDiaDaSemana = new Date(ano, mes, 1).getDay();
  const totalDeDias = new Date(ano, mes + 1, 0).getDate();

  const celulas: (number | null)[] = Array(primeiroDiaDaSemana).fill(null);
  for (let dia = 1; dia <= totalDeDias; dia++) {
    celulas.push(dia);
  }
  return celulas;
}
