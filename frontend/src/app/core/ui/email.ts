/**
 * Conferencia rapida de e-mail no front, antes de mandar: um "@", nada de espaco, e um
 * ponto no meio do dominio. Quem decide de verdade e o backend.
 *
 * Sem regex de proposito: "^[^\s@]+@[^\s@]+\.[^\s@]+$" volta atras demais num texto
 * comprido cheio de pontos (o Sonar aponta como lentidao).
 */
export function pareceEmail(texto: string): boolean {
  const arroba = texto.indexOf('@');
  if (arroba < 1 || arroba !== texto.lastIndexOf('@') || /\s/.test(texto)) {
    return false;
  }
  // Ponto com pelo menos um caractere antes e um depois, dentro do dominio.
  return texto.slice(arroba + 2, -1).includes('.');
}
