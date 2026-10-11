import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { CONTATO_PRIVACIDADE } from '../../core/privacidade/aviso';
import { cpfValido, digitosDoCpf, formatarCpf } from '../../core/ui/cpf';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Primeira entrada (ou conta antiga) sem CPF: o app so abre depois daqui.
 *
 * <p>O CPF prende a conta a uma pessoa: o mesmo CPF nao entra em outra conta. Depois de
 * gravar, renova o token para os outros servicos verem a conta como completa.</p>
 */
@Component({
  selector: 'app-cpf',
  imports: [RouterLink, Icone],
  templateUrl: './cpf.html',
  styleUrls: ['../login/login.scss', './cpf.scss'],
})
export class Cpf {
  private readonly usuarios = inject(UsersService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly contato = CONTATO_PRIVACIDADE;
  protected readonly cpf = signal('');
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected digitar(texto: string, campo: HTMLInputElement): void {
    const formatado = formatarCpf(texto);
    this.cpf.set(formatado);
    // Mantem o campo igual ao sinal mesmo quando a mascara nao muda o valor.
    campo.value = formatado;
    this.erro.set('');
  }

  protected async salvar(): Promise<void> {
    if (!cpfValido(this.cpf())) {
      this.erro.set(
        digitosDoCpf(this.cpf()).length === 11
          ? $localize`CPF inválido. Confira os números.`
          : $localize`O CPF tem 11 números.`,
      );
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    try {
      const usuario = await firstValueFrom(this.usuarios.informarCpf(digitosDoCpf(this.cpf())));
      this.auth.atualizarUsuario(usuario);
      // Token novo, ja com a conta completa: sem isso os outros servicos recusam ate renovar.
      await firstValueFrom(this.auth.renovar());
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
