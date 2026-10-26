import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso } from '../../core/ui/datas';
import { problemaDoNascimento } from '../../core/ui/nascimento';

/**
 * Como a pessoa quer ser chamada e quando nasceu. Aparece uma vez para quem entrou pelo
 * Google ou tem conta de antes desses campos; o cadastro por senha ja pede os dois.
 * A data decide se o titular ve os dados da pessoa (13 a 17 anos) e nao muda depois.
 */
@Component({
  selector: 'app-sobre-voce',
  imports: [FormsModule, Icone],
  templateUrl: './sobre-voce.html',
  styleUrls: ['../login/login.scss', '../aceite/aceite.scss'],
})
export class SobreVoce {
  private readonly usuarios = inject(UsersService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly apelido = signal(
    this.auth.usuario()?.apelido ?? this.auth.usuario()?.primeiroNome ?? '',
  );
  protected readonly nascimento = signal('');
  protected readonly pedeNascimento = !this.auth.usuario()?.nascimento;
  protected readonly hoje = hojeIso();
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected async salvar(): Promise<void> {
    const problema = !this.apelido().trim()
      ? $localize`Conte como podemos te chamar.`
      : this.pedeNascimento
        ? problemaDoNascimento(this.nascimento())
        : '';
    if (problema) {
      this.erro.set(problema);
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    try {
      const usuario = await firstValueFrom(
        this.usuarios.sobreVoce(this.apelido().trim(), this.pedeNascimento ? this.nascimento() : undefined),
      );
      this.auth.atualizarUsuario(usuario);
      await this.router.navigateByUrl('/painel');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
      this.salvando.set(false);
    }
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
  }
}
