import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { ConnectToken, ContaBancaria } from './models';

/** Bancos conectados por Open Finance (Pluggy). */
@Injectable({ providedIn: 'root' })
export class BankingService extends ApiBase {
  /** Open Finance e opcional; desligado (ou servico fora do ar) esconde o card. */
  status(): Observable<{ habilitado: boolean }> {
    return this.http.get<{ habilitado: boolean }>(this.url('/banking/status'));
  }

  /**
   * Token de 30 minutos que abre o widget Pluggy Connect. Leva a autorizacao da pessoa,
   * que o backend grava no mesmo pedido.
   */
  gerarConnectToken(versaoDoAviso: string): Observable<ConnectToken> {
    return this.http.post<ConnectToken>(this.url('/banking/connect-token'), {
      autorizo: true,
      versaoDoAviso,
    });
  }

  /** Registra o item que o widget devolveu e ja traz as contas dele. */
  registrarItem(itemId: string): Observable<ContaBancaria[]> {
    return this.http.post<ContaBancaria[]>(this.url('/banking/items'), { itemId });
  }

  listarContas(): Observable<ContaBancaria[]> {
    return this.http.get<ContaBancaria[]>(this.url('/banking/accounts'));
  }

  /** Apaga a conexao na Pluggy e as contas dela. Sempre gratis. */
  desconectar(conexaoId: string): Observable<ContaBancaria[]> {
    return this.http.delete<ContaBancaria[]>(this.url(`/banking/connections/${conexaoId}`));
  }

  sincronizar(): Observable<ContaBancaria[]> {
    return this.http.post<ContaBancaria[]>(this.url('/banking/sync'), {});
  }
}
