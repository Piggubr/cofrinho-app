import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { Premio, Resgate, SaldoDeMoedas } from './models';

/** Fofocoins, premios e resgates. */
@Injectable({ providedIn: 'root' })
export class RewardsService extends ApiBase {
  consultarSaldo(): Observable<SaldoDeMoedas> {
    return this.http.get<SaldoDeMoedas>(this.url('/coins'));
  }

  /** Credita valor positivo, debita negativo. Exclusivo do administrador. */
  ajustarMoedas(valor: number, motivo: string): Observable<SaldoDeMoedas> {
    return this.http.post<SaldoDeMoedas>(this.url('/coins/adjustments'), { valor, motivo });
  }

  /** @param todos inclui os premios desativados, para a tela de gestao */
  listarPremios(todos = false): Observable<Premio[]> {
    return this.http.get<Premio[]>(this.url('/prizes'), { params: this.params({ todos }) });
  }

  criarPremio(premio: {
    nome: string;
    descricao?: string | null;
    preco: number;
    ativo?: boolean | null;
  }): Observable<Premio> {
    return this.http.post<Premio>(this.url('/prizes'), premio);
  }

  atualizarPremio(
    id: string,
    premio: { nome: string; descricao?: string | null; preco: number; ativo?: boolean | null },
  ): Observable<Premio> {
    return this.http.put<Premio>(this.url(`/prizes/${id}`), premio);
  }

  excluirPremio(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/prizes/${id}`));
  }

  resgatar(premioId: string): Observable<Resgate> {
    return this.http.post<Resgate>(this.url(`/prizes/${premioId}/redemptions`), {});
  }

  listarResgates(): Observable<Resgate[]> {
    return this.http.get<Resgate[]>(this.url('/prizes/redemptions'));
  }
}
