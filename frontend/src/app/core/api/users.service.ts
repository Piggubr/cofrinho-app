import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { PigguRole, Preferencias, Usuario } from './models';

/** Conta propria e, para o ADMIN, as contas da instalacao. */
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
}
