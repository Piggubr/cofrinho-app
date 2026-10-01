import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { PigguRole, Preferencias, Usuario } from './models';

/** Administracao de contas e da lista de e-mails liberados. */
@Injectable({ providedIn: 'root' })
export class UsersService extends ApiBase {
  meuPerfil(): Observable<Usuario> {
    return this.http.get<Usuario>(this.url('/auth/me'));
  }

  /** Qualquer perfil grava as proprias preferencias de moeda. */
  salvarPreferencias(preferencias: Preferencias): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/auth/me/preferences'), preferencias);
  }

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.url('/users'));
  }

  alterar(
    id: string,
    mudancas: { apelido?: string | null; role?: PigguRole | null; ativo?: boolean | null },
  ): Observable<Usuario> {
    return this.http.patch<Usuario>(this.url(`/users/${id}`), mudancas);
  }

  listarAutorizados(): Observable<{ email: string; role: PigguRole }[]> {
    return this.http.get<{ email: string; role: PigguRole }[]>(
      this.url('/users/authorized-emails'),
    );
  }

  autorizar(email: string, role: PigguRole): Observable<void> {
    return this.http.post<void>(this.url('/users/authorized-emails'), { email, role });
  }

  revogar(email: string): Observable<void> {
    return this.http.delete<void>(
      this.url(`/users/authorized-emails/${encodeURIComponent(email)}`),
    );
  }
}
