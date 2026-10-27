import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso } from '../../core/ui/datas';
import { BRASIL, CPF, ddiDoPais, listaDePaises } from '../../core/ui/documentos';
import { problemaDoNascimento } from '../../core/ui/nascimento';

/**
 * Completa o cadastro: como a pessoa quer ser chamada, quando nasceu, o CPF e o celular,
 * o mesmo que o cadastro por senha pede (D-088). Aparece uma vez para quem entrou pelo
 * Google ou tem conta de antes desses campos, e so com o que falta. A data decide se o
 * titular ve os dados da pessoa (13 a 17 anos) e, como o CPF, nao muda depois.
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
  protected readonly pedeCpf = this.auth.usuario()?.cpfInformado === false;
  protected readonly pedeCelular = this.auth.usuario()?.celularInformado === false;
  protected readonly doc = CPF;
  protected readonly cpf = signal('');
  protected readonly paises = listaDePaises();
  protected readonly paisDoDdi = signal(this.auth.usuario()?.pais ?? BRASIL);
  protected readonly telefone = signal('');
  protected readonly hoje = hojeIso();
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected async salvar(): Promise<void> {
    const problema = this.problema();
    if (problema) {
      this.erro.set(problema);
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    try {
      const usuario = await firstValueFrom(
        this.usuarios.sobreVoce(
          this.apelido().trim(),
          this.pedeNascimento ? this.nascimento() : undefined,
          this.pedeCpf ? CPF.limpar(this.cpf()) : undefined,
          this.pedeCelular
            ? `+${ddiDoPais(this.paisDoDdi())} ${this.telefone().trim()}`
            : undefined,
        ),
      );
      this.auth.atualizarUsuario(usuario);
      // O token de antes diz "cadastro incompleto" e os outros servicos recusam: troca por um novo.
      await firstValueFrom(this.auth.renovar());
      await this.router.navigateByUrl('/painel');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
      this.salvando.set(false);
    }
  }

  protected ddi(codigo: string): string {
    return ddiDoPais(codigo);
  }

  /** Mascara do CPF enquanto digita, como no cadastro. */
  protected digitarCpf(texto: string, campo: HTMLInputElement): void {
    const formatado = CPF.formatar(texto);
    this.cpf.set(formatado);
    campo.value = formatado;
  }

  private problema(): string {
    if (!this.apelido().trim()) {
      return $localize`Conte como podemos te chamar.`;
    }
    const doNascimento = this.pedeNascimento ? problemaDoNascimento(this.nascimento()) : '';
    if (doNascimento) {
      return doNascimento;
    }
    if (this.pedeCpf && !CPF.valido(this.cpf())) {
      return this.cpf().trim()
        ? $localize`CPF inválido. Confira o número.`
        : $localize`Informe seu CPF.`;
    }
    if (this.pedeCelular && this.telefone().replace(/\D/g, '').length < 6) {
      return $localize`Informe seu celular.`;
    }
    return '';
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
  }
}
