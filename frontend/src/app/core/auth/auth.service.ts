import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, shareReplay, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { ParDeTokens, PigguRole, Usuario } from '../api/models';
import { GoogleIdentityService } from './google-identity.service';
import { TokenStorage } from './token-storage';

/**
 * Entrada e saida da conta.
 *
 * <p>O backend trabalha com dois tokens: um curto, enviado em cada chamada e guardado
 * so em memoria, e um longo, num cookie HttpOnly que so viaja para /api/auth. Este
 * servico esconde essa mecanica do resto do app, que so precisa saber se ha alguem
 * logado e qual o perfil.</p>
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(APP_CONFIG);
  private readonly storage = inject(TokenStorage);
  private readonly google = inject(GoogleIdentityService);
  private readonly router = inject(Router);

  private readonly usuarioAtual = signal<Usuario | null>(null);
  private renovacaoEmCurso?: Observable<ParDeTokens>;

  readonly usuario = this.usuarioAtual.asReadonly();
  readonly autenticado = computed(() => this.usuarioAtual() !== null);
  readonly ehAdmin = computed(() => this.usuarioAtual()?.role === 'ADMIN');
  /** Espelha o backend: o ADMIN opera a instalacao e usa tudo. Quem barra e o backend. */
  readonly ehPremium = computed(
    () => this.usuarioAtual()?.role === 'ADMIN' || this.usuarioAtual()?.plano === 'PREMIUM',
  );
  /** O membro da familia so alcanca o painel e o cofrinho. */
  readonly ehMembro = computed(() => this.usuarioAtual()?.role === 'MEMBRO');
  /** Titular da familia; o ADMIN vale como titular dentro da propria familia. */
  readonly ehTitular = computed(() => this.usuarioAtual()?.role === 'TITULAR' || this.ehAdmin());

  temPerfil(...perfis: PigguRole[]): boolean {
    const atual = this.usuarioAtual()?.role;
    return atual != null && perfis.includes(atual);
  }

  /** Troca o ID token do Google por um par de tokens do Piggu. */
  /** @param versaoDosTermos so para conta nova: o backend pede quando a conta ainda nao existe */
  async entrarComGoogle(idToken: string, lembrar: boolean, versaoDosTermos?: string): Promise<void> {
    // withCredentials: e assim que o navegador aceita o cookie do refresh vindo da API.
    const tokens = await firstValueFrom(
      this.http.post<ParDeTokens>(
        `${this.config.apiUrl}/auth/google`,
        { idToken, versaoDosTermos, lembrar },
        { withCredentials: true },
      ),
    );
    this.storage.guardar(tokens);
    this.usuarioAtual.set(tokens.usuario);
  }

  /**
   * Tenta recuperar a sessao ao abrir o app.
   *
   * @returns verdadeiro quando havia sessao valida guardada
   */
  async restaurarSessao(): Promise<boolean> {
    // Sem como saber se o cookie existe (HttpOnly): pergunta ao identity.
    try {
      await firstValueFrom(this.renovar());
      return true;
    } catch {
      this.storage.limpar();
      return false;
    }
  }

  /**
   * Renova o acesso.
   *
   * <p>Varias chamadas podem esbarrar no token vencido ao mesmo tempo. O
   * shareReplay faz todas esperarem a mesma renovacao, em vez de dispararem uma
   * cada e invalidarem o refresh umas das outras, ja que o backend rotaciona o
   * refresh a cada uso.</p>
   */
  renovar(): Observable<ParDeTokens> {
    if (this.renovacaoEmCurso) {
      return this.renovacaoEmCurso;
    }

    this.renovacaoEmCurso = this.http
      .post<ParDeTokens>(`${this.config.apiUrl}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap({
          next: (tokens) => {
            this.storage.guardar(tokens);
            this.usuarioAtual.set(tokens.usuario);
            this.renovacaoEmCurso = undefined;
          },
          error: () => {
            this.renovacaoEmCurso = undefined;
          },
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );

    return this.renovacaoEmCurso;
  }

  /** Reflete na sessao uma mudanca no proprio perfil, como as preferencias de moeda. */
  atualizarUsuario(usuario: Usuario): void {
    this.usuarioAtual.set(usuario);
  }

  async sair(): Promise<void> {
    // Avisar o servidor e o que encerra a sessao e apaga o cookie; falhar aqui nao
    // pode impedir o usuario de sair desta maquina.
    await firstValueFrom(
      this.http.post(`${this.config.apiUrl}/auth/logout`, {}, { withCredentials: true }),
    ).catch(() => null);
    await this.encerrarLocalmente();
  }

  /** Descarta a sessao local e volta para o login, sem falar com o servidor. */
  async encerrarLocalmente(): Promise<void> {
    this.storage.limpar();
    this.usuarioAtual.set(null);
    await this.google.esquecerConta();
    await this.router.navigate(['/entrar']);
  }
}
