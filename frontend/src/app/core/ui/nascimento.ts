/** Idade minima para ter conta (LGPD art. 14); o backend confere de novo. */
export const IDADE_MINIMA = 13;

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
