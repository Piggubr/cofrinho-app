/**
 * CPF no front: mascara enquanto digita e confere os digitos verificadores antes de
 * mandar. Quem decide de verdade e o backend (mesma conta, e CPF unico entre contas).
 */

/** So os digitos, no maximo 11. */
export function digitosDoCpf(texto: string): string {
  return texto.replace(/\D/g, '').slice(0, 11);
}

/** "52998224725" -> "529.982.247-25", ja no meio da digitacao. */
export function formatarCpf(texto: string): string {
  const d = digitosDoCpf(texto);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`;
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Digitos verificadores da Receita; numeros todos iguais nao valem. */
export function cpfValido(texto: string): boolean {
  const d = digitosDoCpf(texto);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) {
    return false;
  }
  const digito = (posicao: number): number => {
    let soma = 0;
    for (let i = 0; i < posicao; i++) soma += Number(d[i]) * (posicao + 1 - i);
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  return digito(9) === Number(d[9]) && digito(10) === Number(d[10]);
}
