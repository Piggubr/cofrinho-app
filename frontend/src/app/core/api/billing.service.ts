import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { InfoDoPlano, Periodo } from './models';

/** Piggu Premium. O pagamento acontece na pagina do provedor; aqui so pegamos o endereco. */
@Injectable({ providedIn: 'root' })
export class BillingService extends ApiBase {
  /** @param campanha codigo de promocao do link (/plano?promo=), que pode liberar o teste de novo */
  plano(campanha?: string | null): Observable<InfoDoPlano> {
    return this.http.get<InfoDoPlano>(this.url('/billing/plan'), { params: campanha ? { campanha } : {} });
  }

  checkout(periodo: Periodo, campanha?: string | null): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(this.url('/billing/checkout'), campanha ? { periodo, campanha } : { periodo });
  }

  /**
   * Desistir em ate 7 dias da primeira cobranca: cancela na hora e devolve o valor.
   * reembolsado falso: cancelou ainda no teste, sem nada a devolver.
   */
  reembolso(): Observable<{ reembolsado: boolean }> {
    return this.http.post<{ reembolsado: boolean }>(this.url('/billing/refund'), {});
  }

  /** Trocar cartao, ver recibos e cancelar. */
  portal(): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(this.url('/billing/portal'), {});
  }
}
