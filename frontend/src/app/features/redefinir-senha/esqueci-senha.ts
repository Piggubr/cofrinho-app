import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Pedido do link de senha nova. A resposta e a mesma com ou sem conta, entao a tela
 * tambem nao diz se o e-mail existe: so que, se existir, o link foi.
 */
@Component({
  selector: 'app-esqueci-senha',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './esqueci-senha.html',
  styleUrl: '../login/login.scss',
})
export class EsqueciSenha {
  private readonly auth = inject(AuthService);

  protected readonly email = signal('');
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly erro = signal('');

  protected async pedir(): Promise<void> {
    const email = this.email().trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.erro.set($localize`Confira o e-mail.`);
      return;
    }
    this.erro.set('');
    this.enviando.set(true);
    try {
      await this.auth.esqueciSenha(email);
      this.enviado.set(true);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Nao foi possivel pedir o link.`));
    } finally {
      this.enviando.set(false);
    }
  }
}
