import { Component, ElementRef, inject, signal, viewChild, afterNextRender } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { VERSAO_DO_AVISO, codigoDoErro } from '../../core/privacidade/aviso';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { Icone } from '../../core/ui/icone';
import { problemaDaSenha } from '../../core/auth/regras-da-senha';

type Modo = 'entrar' | 'cadastro';

/**
 * Entrada no app.
 *
 * <p>Duas portas para a mesma conta: e-mail e senha, ou o Google. O cadastro por e-mail
 * pede nome, e-mail, senha (duas vezes) e o aceite dos termos. Pelo Google, na primeira
 * vez o backend so cria a conta com o aceite, pedido aqui depois que o Google confirma.</p>
 */
@Component({
  selector: 'app-login',
  imports: [Icone, FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly google = inject(GoogleIdentityService);
  private readonly router = inject(Router);

  private readonly areaDoBotao = viewChild.required<ElementRef<HTMLDivElement>>('botaoGoogle');

  protected readonly modo = signal<Modo>('entrar');
  protected readonly lembrar = signal(true);
  protected readonly erro = signal('');
  protected readonly entrando = signal(false);
  /** Conta nova pelo Google: ele ja confirmou, falta o aceite dos termos para criar. */
  protected readonly aguardandoAceite = signal(false);
  protected readonly aceitou = signal(false);
  private tokenDoGoogle = '';

  protected readonly nome = signal('');
  protected readonly email = signal('');
  protected readonly senha = signal('');
  protected readonly confirmacao = signal('');

  constructor() {
    afterNextRender(() => void this.prepararBotao());
    void this.tentarSessaoGuardada();
  }

  protected alternarLembrar(valor: boolean): void {
    this.lembrar.set(valor);
  }

  /** Troca entre entrar e criar conta; o e-mail digitado continua la. */
  protected trocarModo(modo: Modo): void {
    this.modo.set(modo);
    this.erro.set('');
    this.senha.set('');
    this.confirmacao.set('');
    this.aceitou.set(false);
  }

  private async tentarSessaoGuardada(): Promise<void> {
    if (await this.auth.restaurarSessao()) {
      await this.router.navigate(['/painel']);
    }
  }

  private async prepararBotao(): Promise<void> {
    try {
      await this.google.renderizarBotao(this.areaDoBotao().nativeElement, (idToken) =>
        this.entrarPeloGoogle(idToken),
      );
    } catch {
      this.erro.set(
        $localize`Nao consegui carregar o login do Google. Entre com e-mail e senha ou recarregue a pagina.`,
      );
    }
  }

  protected async entrarComSenha(): Promise<void> {
    if (!this.email().trim() || !this.senha()) {
      this.erro.set($localize`Informe seu e-mail e sua senha.`);
      return;
    }
    await this.tentar(
      () => this.auth.entrarComSenha(this.email().trim(), this.senha(), this.lembrar()),
      $localize`Nao foi possivel entrar.`,
    );
  }

  protected async cadastrar(): Promise<void> {
    const problema = this.problemaDoCadastro();
    if (problema) {
      this.erro.set(problema);
      return;
    }
    await this.tentar(
      () =>
        this.auth.cadastrar(
          { nome: this.nome().trim(), email: this.email().trim(), senha: this.senha() },
          this.lembrar(),
        ),
      $localize`Nao foi possivel criar sua conta.`,
    );
  }

  /** Confere aqui o que da para conferir sem o servidor; o backend confere de novo. */
  private problemaDoCadastro(): string {
    if (!this.nome().trim()) {
      return $localize`Conte como podemos te chamar.`;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email().trim())) {
      return $localize`Confira o e-mail.`;
    }
    const senhaRuim = problemaDaSenha(this.senha(), this.email());
    if (senhaRuim) {
      return senhaRuim;
    }
    if (this.senha() !== this.confirmacao()) {
      return $localize`As duas senhas nao sao iguais.`;
    }
    if (!this.aceitou()) {
      return $localize`Para criar a conta, aceite os Termos de Uso e o Aviso de Privacidade.`;
    }
    return '';
  }

  /** Cria a conta Google com o aceite, reaproveitando a confirmacao que ja chegou. */
  protected criarContaGoogle(): void {
    if (this.aceitou()) {
      void this.entrarPeloGoogle(this.tokenDoGoogle, VERSAO_DO_AVISO);
    }
  }

  protected voltarDoAceite(): void {
    this.aguardandoAceite.set(false);
    this.aceitou.set(false);
    this.tokenDoGoogle = '';
  }

  private async entrarPeloGoogle(idToken: string, versaoDosTermos?: string): Promise<void> {
    await this.tentar(
      () => this.auth.entrarComGoogle(idToken, this.lembrar(), versaoDosTermos),
      $localize`Nao foi possivel entrar.`,
      (falha) => {
        if (codigoDoErro(falha) !== 'TERMOS_NECESSARIOS') {
          return false;
        }
        this.tokenDoGoogle = idToken;
        this.aceitou.set(false);
        this.aguardandoAceite.set(true);
        return true;
      },
    );
  }

  /** @param tratar devolve verdadeiro quando ja cuidou da falha (sem mensagem de erro) */
  private async tentar(
    acao: () => Promise<void>,
    padrao: string,
    tratar: (falha: unknown) => boolean = () => false,
  ): Promise<void> {
    this.erro.set('');
    this.entrando.set(true);
    try {
      await acao();
      await this.router.navigate(['/painel']);
    } catch (falha) {
      if (!tratar(falha)) {
        this.erro.set(mensagemDeErro(falha, padrao));
      }
    } finally {
      this.entrando.set(false);
    }
  }
}
