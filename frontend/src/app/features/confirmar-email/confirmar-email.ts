import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Destino do link do e-mail de confirmacao: manda o token e mostra o resultado. */
@Component({
  selector: 'app-confirmar-email',
  imports: [RouterLink, Icone],
  templateUrl: './confirmar-email.html',
  styleUrl: '../login/login.scss',
})
export class ConfirmarEmail {
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<'confirmando' | 'ok' | 'erro'>('confirmando');
  protected readonly erro = signal('');

  constructor() {
    void this.confirmar(inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '');
  }

  private async confirmar(token: string): Promise<void> {
    try {
      await this.auth.confirmarEmail(token);
      this.estado.set('ok');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
      this.estado.set('erro');
    }
  }
}
