import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { ConviteDaFamilia, Familia } from './models';

/** A familia de quem esta logado. O backend so deixa o titular convidar e remover. */
@Injectable({ providedIn: 'root' })
export class FamilyService extends ApiBase {
  ver(): Observable<Familia> {
    return this.http.get<Familia>(this.url('/family'));
  }

  renomear(nome: string): Observable<Familia> {
    return this.http.put<Familia>(this.url('/family'), { nome });
  }

  convidar(email: string): Observable<Familia> {
    return this.http.post<Familia>(this.url('/family/invites'), { email });
  }

  cancelarConvite(id: string): Observable<Familia> {
    return this.http.delete<Familia>(this.url(`/family/invites/${id}`));
  }

  removerMembro(id: string): Observable<Familia> {
    return this.http.delete<Familia>(this.url(`/family/members/${id}`));
  }

  /** Convites de outras familias para o meu e-mail. */
  convitesParaMim(): Observable<ConviteDaFamilia[]> {
    return this.http.get<ConviteDaFamilia[]>(this.url('/family/invites/mine'));
  }

  aceitarConvite(id: string): Observable<void> {
    return this.http.post<void>(this.url(`/family/invites/${id}/accept`), {});
  }

  sair(): Observable<void> {
    return this.http.post<void>(this.url('/family/leave'), {});
  }
}
