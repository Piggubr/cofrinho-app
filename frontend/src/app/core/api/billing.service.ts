import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { InfoDoPlano, Periodo } from './models';

/** Piggu Premium. O pagamento acontece na pagina do provedor; aqui so pegamos o endereco. */
@Injectable({ providedIn: 'root' })
export class BillingService extends ApiBase {
  plano(): Observable<InfoDoPlano> {
    return this.http.get<InfoDoPlano>(this.url('/billing/plan'));
  }

  checkout(periodo: Periodo): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(this.url('/billing/checkout'), { periodo });
  }

  /** Desistir nos 7 primeiros dias: cancela na hora e devolve o valor. */
  reembolso(): Observable<void> {
    return this.http.post<void>(this.url('/billing/refund'), {});
  }

  /** Trocar cartao, ver recibos e cancelar. */
  portal(): Observable<{ url: string }> {
    return this.http.post<{ url: string }>(this.url('/billing/portal'), {});
  }
}
