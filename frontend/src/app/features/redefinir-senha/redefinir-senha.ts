import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { problemaDaSenha } from '../../core/auth/regras-da-senha';
import { codigoDoErro } from '../../core/privacidade/aviso';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Destino do link de senha nova. Aberta, sem sessao: quem esqueceu a senha nao esta
 * logado. Depois de trocar, todas as sessoes da conta caem e a pessoa entra de novo.
 */
@Component({
  selector: 'app-redefinir-senha',
  imports: [FormsModule, RouterLink, Icone],
  templateUrl: './redefinir-senha.html',
  styleUrl: '../login/login.scss',
})
export class RedefinirSenha {
  private readonly auth = inject(AuthService);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  protected readonly senha = signal('');
  protected readonly confirmacao = signal('');
  protected readonly salvando = signal(false);
  protected readonly estado = signal<'formulario' | 'ok' | 'link-ruim'>(this.token ? 'formulario' : 'link-ruim');
  protected readonly erro = signal('');

  protected async salvar(): Promise<void> {
    const problema = problemaDaSenha(this.senha());
    if (problema) {
      this.erro.set(problema);
      return;
    }
    if (this.senha() !== this.confirmacao()) {
      this.erro.set($localize`As duas senhas nao sao iguais.`);
      return;
    }
    this.erro.set('');
    this.salvando.set(true);
    try {
      await this.auth.redefinirSenha(this.token, this.senha());
      this.estado.set('ok');
    } catch (falha) {
      if (codigoDoErro(falha) === 'LINK_INVALIDO') {
        this.estado.set('link-ruim');
      } else {
        this.erro.set(mensagemDeErro(falha, $localize`Nao foi possivel salvar a senha nova.`));
      }
    } finally {
      this.salvando.set(false);
    }
  }
}
