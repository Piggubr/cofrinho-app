import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { ConnectToken, ContaBancaria } from './models';

/** Bancos conectados por Open Finance (Pluggy). */
@Injectable({ providedIn: 'root' })
export class BankingService extends ApiBase {
  /** Token de 30 minutos que abre o widget Pluggy Connect. */
  gerarConnectToken(): Observable<ConnectToken> {
    return this.http.post<ConnectToken>(this.url('/banking/connect-token'), {});
  }

  /** Registra o item que o widget devolveu e ja traz as contas dele. */
  registrarItem(itemId: string): Observable<ContaBancaria[]> {
    return this.http.post<ContaBancaria[]>(this.url('/banking/items'), { itemId });
  }

  listarContas(): Observable<ContaBancaria[]> {
    return this.http.get<ContaBancaria[]>(this.url('/banking/accounts'));
  }

  sincronizar(): Observable<ContaBancaria[]> {
    return this.http.post<ContaBancaria[]>(this.url('/banking/sync'), {});
  }
}
