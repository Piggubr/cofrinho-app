/** Espelha a regra do backend (Senhas.java): o servidor confere de novo. */
export const MINIMO_DA_SENHA = 8;
const MAXIMO_EM_BYTES = 72;

/** @returns o que falta na senha, ou vazio quando ela serve */
export function problemaDaSenha(senha: string, email = ''): string {
  if (senha.length < MINIMO_DA_SENHA) {
    return $localize`A senha precisa ter pelo menos ${MINIMO_DA_SENHA}:minimo: caracteres.`;
  }
  if (new TextEncoder().encode(senha).length > MAXIMO_EM_BYTES) {
    return $localize`A senha pode ter no maximo ${MAXIMO_EM_BYTES}:maximo: caracteres.`;
  }
  if (!/\p{L}/u.test(senha) || !/\p{Nd}/u.test(senha)) {
    return $localize`Use letras e numeros na senha.`;
  }
  if (email && senha.trim().toLowerCase() === email.trim().toLowerCase()) {
    return $localize`A senha nao pode ser o seu e-mail.`;
  }
  return '';
}
