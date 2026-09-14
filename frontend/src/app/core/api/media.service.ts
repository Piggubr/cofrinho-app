import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { ApiBase } from './api-base';
import { Arquivo, FotoDoFeed } from './models';

/** Feed de fotos e arquivos de imagem. */
@Injectable({ providedIn: 'root' })
export class MediaService extends ApiBase {
  private readonly configuracao = inject(APP_CONFIG);

  /** @param mes filtro opcional no formato AAAA-MM */
  listarFeed(mes?: string): Observable<FotoDoFeed[]> {
    return this.http.get<FotoDoFeed[]>(this.url('/feed'), { params: this.params({ mes }) });
  }

  publicarNoFeed(mesKey: string, imageBase64: string, mimeType: string): Observable<FotoDoFeed> {
    return this.http.post<FotoDoFeed>(this.url('/feed'), { mesKey, imageBase64, mimeType });
  }

  legendar(id: string, legenda: string): Observable<FotoDoFeed> {
    return this.http.patch<FotoDoFeed>(this.url(`/feed/${id}/caption`), { legenda });
  }

  excluirDoFeed(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/feed/${id}`));
  }

  enviarArquivo(imageBase64: string, mimeType: string, contexto: string): Observable<Arquivo> {
    return this.http.post<Arquivo>(this.url('/assets'), { imageBase64, mimeType, contexto });
  }

  /**
   * Endereco dos bytes de uma imagem.
   *
   * <p>O backend entrega a imagem com tipo e cache de trinta dias, entao vale usar
   * esta URL direto em uma tag img em vez de baixar e converter para base64.</p>
   */
  urlDaImagem(assetId: string): string {
    return `${this.configuracao.apiUrl}/assets/${assetId}/content`;
  }

  baixarArquivo(assetId: string): Observable<Blob> {
    return this.http.get(this.url(`/assets/${assetId}/content`), { responseType: 'blob' });
  }

  excluirArquivo(assetId: string): Observable<void> {
    return this.http.delete<void>(this.url(`/assets/${assetId}`));
  }
}
