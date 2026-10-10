import { Component, ElementRef, inject, signal, viewChild, afterNextRender } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { VERSAO_DO_AVISO, codigoDoErro } from '../../core/privacidade/aviso';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { Icone } from '../../core/ui/icone';

/**
 * Entrada no app.
 *
 * <p>O acesso e por conta Google e o cadastro e aberto. Na primeira vez, o backend so
 * cria a conta com o aceite dos termos, pedido aqui depois que o Google confirma.</p>
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

  protected readonly lembrar = signal(true);
  protected readonly erro = signal('');
  protected readonly entrando = signal(false);
  /** Conta nova: o Google ja confirmou, falta o aceite dos termos para criar. */
  protected readonly aguardandoAceite = signal(false);
  protected readonly aceitou = signal(false);
  private tokenDoGoogle = '';

  constructor() {
    afterNextRender(() => void this.prepararBotao());
    void this.tentarSessaoGuardada();
  }

  protected alternarLembrar(valor: boolean): void {
    this.lembrar.set(valor);
  }

  private async tentarSessaoGuardada(): Promise<void> {
    if (await this.auth.restaurarSessao()) {
      await this.router.navigate(['/painel']);
    }
  }

  private async prepararBotao(): Promise<void> {
    try {
      await this.google.renderizarBotao(this.areaDoBotao().nativeElement, (idToken) =>
        this.entrar(idToken),
      );
    } catch {
      this.erro.set($localize`Nao consegui carregar o login do Google. Recarregue a pagina.`);
    }
  }

  /** Cria a conta com o aceite, reaproveitando a confirmacao do Google que ja chegou. */
  protected criarConta(): void {
    if (this.aceitou()) {
      void this.entrar(this.tokenDoGoogle, VERSAO_DO_AVISO);
    }
  }

  private async entrar(idToken: string, versaoDosTermos?: string): Promise<void> {
    this.erro.set('');
    this.entrando.set(true);
    try {
      await this.auth.entrarComGoogle(idToken, this.lembrar(), versaoDosTermos);
      await this.router.navigate(['/painel']);
    } catch (falha) {
      if (codigoDoErro(falha) === 'TERMOS_NECESSARIOS') {
        this.tokenDoGoogle = idToken;
        this.aguardandoAceite.set(true);
      } else {
        this.erro.set(mensagemDeErro(falha, $localize`Nao foi possivel entrar.`));
      }
    } finally {
      this.entrando.set(false);
    }
  }
}
