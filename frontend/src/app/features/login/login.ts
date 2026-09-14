import { Component, ElementRef, inject, signal, viewChild, afterNextRender } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { GoogleIdentityService } from '../../core/auth/google-identity.service';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Entrada no app.
 *
 * <p>O acesso e por conta Google, restrito a uma lista de e-mails que o backend
 * controla. Quem nao esta na lista recebe recusa clara em vez de uma tela vazia.</p>
 */
@Component({
  selector: 'app-login',
  imports: [FormsModule],
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
      this.erro.set('Nao consegui carregar o login do Google. Recarregue a pagina.');
    }
  }

  private async entrar(idToken: string): Promise<void> {
    this.erro.set('');
    this.entrando.set(true);
    try {
      await this.auth.entrarComGoogle(idToken, this.lembrar());
      await this.router.navigate(['/painel']);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, 'Nao foi possivel entrar.'));
    } finally {
      this.entrando.set(false);
    }
  }
}
