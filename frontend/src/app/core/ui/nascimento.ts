/** Idade minima para ter conta (LGPD art. 14); o backend confere de novo. */
export const IDADE_MINIMA = 13;

/** Hoje no formato do input type="date", para o atributo max. */
export function hojeIso(): string {
  return dataIso(new Date());
}

/** aaaa-mm-dd no fuso do aparelho (o toISOString usaria UTC e erraria o dia a noite). */
export function dataIso(data: Date): string {
  const doisDigitos = (n: number) => String(n).padStart(2, '0');
  return `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}`;
}

/** Mensagem para a data digitada (aaaa-mm-dd), ou vazio quando esta boa. */
export function problemaDoNascimento(data: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) {
    return $localize`Informe sua data de nascimento.`;
  }
  const nascimento = new Date(`${data}T00:00:00`);
  const hoje = new Date();
  if (Number.isNaN(nascimento.getTime()) || nascimento > hoje || hoje.getFullYear() - nascimento.getFullYear() > 120) {
    return $localize`Confira a data de nascimento.`;
  }
  const limite = new Date(hoje.getFullYear() - IDADE_MINIMA, hoje.getMonth(), hoje.getDate());
  if (nascimento > limite) {
    return $localize`O Piggu é para quem tem ${IDADE_MINIMA}:idade: anos ou mais.`;
  }
  return '';
}
