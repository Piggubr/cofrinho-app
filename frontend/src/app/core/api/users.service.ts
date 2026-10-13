import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import { ModuloDeEstiloDeVida, PigguRole, Preferencias, Usuario } from './models';

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

  /** Avisos do dia (fatura, orcamento, conta do mes) por e-mail e no celular. */
  salvarAvisos(email: boolean, push: boolean): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/auth/me/alerts'), { email, push });
  }

  /** Token do APNs deste iPhone, para o push dos avisos. */
  registrarAparelho(token: string): Observable<void> {
    return this.http.post<void>(this.url('/auth/me/devices'), { token, plataforma: 'IOS' });
  }

  /** Liga e desliga as telas de estilo de vida no menu da propria pessoa. */
  salvarModulos(modulos: ModuloDeEstiloDeVida[]): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/auth/me/modules'), { modulos });
  }

  /** Pais onde mora e, quando la se pede um, o documento (o CPF no Brasil). */
  informarPais(pais: string, documento?: string): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/me/pais'), { pais, documento });
  }

  /** Aceite da versao nova dos Termos e do Aviso por quem ja tem conta. */
  aceitarTermos(versao: string): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/me/termos'), { versao });
  }

  /** Foto de perfil propria, ja recortada e reduzida no aparelho (ver prepararFotoDePerfil). */
  trocarFoto(imagemBase64: string): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/me/foto'), { imagemBase64, mimeType: 'image/jpeg' });
  }

  /** Sem foto: ficam as iniciais, nem a do Google aparece. */
  removerFoto(): Observable<Usuario> {
    return this.http.delete<Usuario>(this.url('/me/foto'));
  }

  /** Apaga a foto propria e volta a da conta Google. */
  usarFotoDoGoogle(): Observable<Usuario> {
    return this.http.put<Usuario>(this.url('/me/foto/google'), {});
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
