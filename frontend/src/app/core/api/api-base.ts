import { HttpClient, HttpParams } from '@angular/common/http';
import { inject } from '@angular/core';
import { APP_CONFIG } from '../config/app-config';

/**
 * Base dos servicos de API.
 *
 * <p>Guarda o HttpClient e a montagem da URL, para que cada servico de dominio
 * fique apenas com os seus endpoints.</p>
 */
export abstract class ApiBase {
  protected readonly http = inject(HttpClient);
  private readonly config = inject(APP_CONFIG);

  protected url(caminho: string): string {
    return `${this.config.apiUrl}${caminho}`;
  }

  /** Monta query string ignorando valores nulos, vazios ou indefinidos. */
  protected params(valores: Record<string, string | number | boolean | null | undefined>): HttpParams {
    let params = new HttpParams();
    for (const [chave, valor] of Object.entries(valores)) {
      if (valor !== null && valor !== undefined && valor !== '') {
        params = params.set(chave, String(valor));
      }
    }
    return params;
  }
}
