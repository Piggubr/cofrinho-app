import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, shareReplay, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { VERSAO_DO_AVISO } from '../privacidade/aviso';
import { ParDeTokens, PigguRole, Usuario } from '../api/models';
import { esquecerListasGuardadas } from '../offline/listas-guardadas';
import { GoogleIdentityService } from './google-identity.service';
import { TokenStorage } from './token-storage';

/** O CPF so vai (e e obrigatorio) para quem mora no Brasil. */
export interface DadosDoCadastro {
  nome: string;
  email: string;
  senha: string;
  /** ISO 3166, duas letras. */
  pais: string;
  /** Opcional: CPF no Brasil, o documento de la nos paises que pedem um. */
  documento?: string;
  /** Celular; no Brasil pode vir so com DDD, fora comeca com + e o codigo do pais. */
  telefone: string;
  /** aaaa-mm-dd; menos de 13 anos nao cria conta. */
  nascimento: string;
}


/** Perfil guardado para abrir o app sem sinal; so com "manter conectado". */
const CHAVE_DO_PERFIL_OFFLINE = 'piggu_perfil_offline';

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
  /** Titular da familia; o ADMIN vale como titular dentro da propria familia. */
  readonly ehTitular = computed(() => this.usuarioAtual()?.role === 'TITULAR' || this.ehAdmin());
  /** Cria e edita os premios da familia: titular (ou ADMIN) e parceiro. */
  readonly cuidaDosPremios = computed(
    () => this.ehTitular() || this.usuarioAtual()?.role === 'PARCEIRO',
  );

  /** Conta ainda sem CPF: o app so abre a tela de informar. */

  /** Os Termos mudaram desde o ultimo aceite: o app so abre a tela de aceitar de novo. */
  readonly termosPendentes = computed(() => {
    const aceitos = this.usuarioAtual()?.termosAceitos;
    return aceitos !== undefined && aceitos !== VERSAO_DO_AVISO;
  });

  /**
   * Falta como chamar ou a data de nascimento: o app pede os dois antes do resto. Perfil
   * sem o campo (demo, resposta antiga) nao trava.
   */
  readonly sobreVocePendente = computed(() => {
    const usuario = this.usuarioAtual();
    return usuario?.nascimento !== undefined && (!usuario.apelido?.trim() || !usuario.nascimento);
  });


  temPerfil(...perfis: PigguRole[]): boolean {
    const atual = this.usuarioAtual()?.role;
    return atual != null && perfis.includes(atual);
  }

  /** Troca o ID token do Google por um par de tokens do Piggu. */
  /** @param versaoDosTermos so para conta nova: o backend pede quando a conta ainda nao existe */
  async entrarComGoogle(
    idToken: string,
    lembrar: boolean,
    versaoDosTermos?: string,
  ): Promise<void> {
    await this.abrirSessao('/auth/google', { idToken, versaoDosTermos, lembrar }, lembrar);
  }

  /** Entrada por e-mail e senha, sem passar pelo Google. */
  async entrarComSenha(email: string, senha: string, lembrar: boolean): Promise<void> {
    await this.abrirSessao('/auth/entrar', { email, senha, lembrar }, lembrar);
  }

  /** Conta nova por e-mail e senha; o aceite dos termos (e o CPF, no Brasil) vai junto. */
  /** Cria a conta sem abrir sessao: a pessoa confirma o e-mail e entra com a senha. */
  async cadastrar(dados: DadosDoCadastro): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${this.config.apiUrl}/auth/cadastro`, {
        ...dados,
        versaoDosTermos: VERSAO_DO_AVISO,
      }),
    );
  }

  /**
   * Cria a senha de quem so entrava pelo Google, ou troca a atual (que entao e pedida).
   * O cookie vai junto para o backend manter a sessao deste aparelho.
   */
  async definirSenha(novaSenha: string, senhaAtual?: string): Promise<void> {
    const usuario = await firstValueFrom(
      this.http.put<Usuario>(
        `${this.config.apiUrl}/auth/me/senha`,
        { senhaAtual, novaSenha },
        { withCredentials: true },
      ),
    );
    this.atualizarUsuario(usuario);
  }

  /** Confirma o e-mail pelo link; com sessao aberta, o perfil ja sai atualizado. */
  async confirmarEmail(token: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${this.config.apiUrl}/auth/confirmar-email`, { token }),
    );
    // A resposta nao traz o perfil; com sessao aberta, busca o atualizado.
    if (this.autenticado()) {
      this.atualizarUsuario(
        await firstValueFrom(this.http.get<Usuario>(`${this.config.apiUrl}/auth/me`)),
      );
    }
  }

  /** Manda outro link de confirmacao; o anterior deixa de valer. */
  async reenviarConfirmacao(): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${this.config.apiUrl}/auth/me/email/reenviar`, {}));
  }

  private async abrirSessao(rota: string, corpo: object, lembrar: boolean): Promise<void> {
    // withCredentials: e assim que o navegador aceita o cookie do refresh vindo da API.
    const tokens = await firstValueFrom(
      this.http.post<ParDeTokens>(`${this.config.apiUrl}${rota}`, corpo, { withCredentials: true }),
    );
    this.storage.guardar(tokens);
    this.usuarioAtual.set(tokens.usuario);
    if (lembrar) {
      guardarPerfilOffline(tokens.usuario);
    } else {
      esquecerPerfilOffline();
    }
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
    } catch (erro) {
      this.storage.limpar();
      // Sem sinal (status 0) nao e sessao vencida: abre com o perfil guardado, e o
      // primeiro pedido com rede renova o token pelo cookie.
      const perfil =
        erro instanceof HttpErrorResponse && erro.status === 0 ? lerPerfilOffline() : null;
      if (perfil) {
        this.usuarioAtual.set(perfil);
        return true;
      }
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
            if (lerPerfilOffline()) {
              guardarPerfilOffline(tokens.usuario);
            }
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
    if (lerPerfilOffline()) {
      guardarPerfilOffline(usuario);
    }
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
    esquecerPerfilOffline();
    this.usuarioAtual.set(null);
    await esquecerListasGuardadas();
    await this.google.esquecerConta();
    await this.router.navigate(['/entrar']);
  }
}

function lerPerfilOffline(): Usuario | null {
  try {
    const guardado = localStorage.getItem(CHAVE_DO_PERFIL_OFFLINE);
    return guardado ? (JSON.parse(guardado) as Usuario) : null;
  } catch {
    return null;
  }
}

function guardarPerfilOffline(usuario: Usuario): void {
  try {
    localStorage.setItem(CHAVE_DO_PERFIL_OFFLINE, JSON.stringify(usuario));
  } catch {
    // Sem armazenamento: o app so abre com sinal.
  }
}

function esquecerPerfilOffline(): void {
  try {
    localStorage.removeItem(CHAVE_DO_PERFIL_OFFLINE);
  } catch {
    // Nada guardado.
  }
}
