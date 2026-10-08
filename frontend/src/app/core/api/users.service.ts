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

  /** Copia de tudo o que o Piggu guarda da pessoa (LGPD art. 18), em um JSON. */
  exportarMeusDados(): Observable<Blob> {
    return this.http.get(this.url('/me/export'), { responseType: 'blob' });
  }

  /** Exclui a conta e os dados; se a pessoa era a ultima da familia, a familia sai junto. */
  excluirConta(): Observable<void> {
    return this.http.delete<void>(this.url('/me'));
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
