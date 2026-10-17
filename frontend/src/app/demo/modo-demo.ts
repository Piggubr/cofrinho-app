import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Build normal: sem demonstracao.
 *
 * <p>O build "demo" (angular.json, fileReplacements) troca este arquivo por
 * modo-demo.demo.ts, que responde a API com dados de exemplo. Nada daquele arquivo
 * entra no build de producao; o CI confere procurando o nome da familia de exemplo
 * no bundle.</p>
 */
export const MODO_DEMO = false;

export const INTERCEPTORES_DA_DEMO: HttpInterceptorFn[] = [];

/** Na demo, a foto vem da memoria; fora dela, sempre da API. */
export function imagemDaDemo(_assetId: string): string | null {
  return null;
}
