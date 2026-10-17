import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Os Termos mudaram desde o ultimo aceite (ou a conta e da lista fechada e nunca aceitou):
 * o app so abre depois daqui. Quem nao concorda pode sair ou excluir a conta.
 */
@Component({
  selector: 'app-aceite',
  imports: [RouterLink, Icone],
  templateUrl: './aceite.html',
  styleUrls: ['../login/login.scss', './aceite.scss'],
})
export class Aceite {
  private readonly usuarios = inject(UsersService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly versao = VERSAO_DO_AVISO;
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected async aceitar(): Promise<void> {
    this.salvando.set(true);
    this.erro.set('');
    try {
      this.auth.atualizarUsuario(await firstValueFrom(this.usuarios.aceitarTermos(VERSAO_DO_AVISO)));
      await this.router.navigateByUrl('/painel');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
      this.salvando.set(false);
    }
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
  }

  protected excluirConta(): void {
    const texto =
      $localize`Excluir sua conta apaga tudo o que você lançou. Se você for a última pessoa da família, ` +
      $localize`tudo da família é apagado. Se outras pessoas ficarem, as categorias que você criou ficam com ` +
      $localize`elas, sem o seu nome. Uma assinatura Premium feita por você é cancelada. Isso não tem volta. ` +
      $localize`Digite EXCLUIR para confirmar.`;
    if (prompt(texto)?.trim().toUpperCase() !== 'EXCLUIR') {
      return;
    }
    this.salvando.set(true);
    this.usuarios.excluirConta().subscribe({
      next: () => void this.auth.encerrarLocalmente(),
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }
}
