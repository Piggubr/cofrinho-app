import { Injectable } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { ApiBase } from './api-base';
import { EventoDeAuditoria } from './models';

/**
 * Historico da familia: o financeiro guarda o que mudou no dinheiro e o identity, quem
 * entrou, saiu ou mudou de papel. Junta os dois, do mais novo ao mais velho. Se um dos
 * servicos nao responder, mostra o que o outro tem.
 */
@Injectable({ providedIn: 'root' })
export class HistoryService extends ApiBase {
  daFamilia(limite = 50): Observable<EventoDeAuditoria[]> {
    const pedir = (rota: string) =>
      this.http
        .get<EventoDeAuditoria[]>(this.url(rota), { params: { limite } })
        .pipe(catchError(() => of([] as EventoDeAuditoria[])));
    return forkJoin([pedir('/history'), pedir('/family/history')]).pipe(
      map(([dinheiro, pessoas]) =>
        [...dinheiro, ...pessoas].sort((a, b) => b.quando.localeCompare(a.quando)).slice(0, limite),
      ),
    );
  }
}
