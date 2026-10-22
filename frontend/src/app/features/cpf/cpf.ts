import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { CONTATO_PRIVACIDADE } from '../../core/privacidade/aviso';
import { BRASIL, CPF, listaDePaises } from '../../core/ui/documentos';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Informar o CPF, opcional; o Perfil oferece esta tela a quem ainda nao informou.
 *
 * <p>O CPF prende a conta a uma pessoa. A pessoa diz o pais onde mora e informa o CPF,
 * pedido aqui e fora. Depois de gravar, renova o token para os outros servicos verem a
 * conta como completa.</p>
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
  protected readonly paises = listaDePaises();
  protected readonly pais = signal(this.auth.usuario()?.pais || BRASIL);
  protected readonly doc = CPF;
  protected readonly documento = signal('');
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected trocarPais(pais: string): void {
    this.pais.set(pais);
    this.erro.set('');
  }

  protected digitar(texto: string, campo: HTMLInputElement): void {
    const formatado = CPF.formatar(texto);
    this.documento.set(formatado);
    // Mantem o campo igual ao sinal mesmo quando a mascara nao muda o valor.
    campo.value = formatado;
    this.erro.set('');
  }

  protected async salvar(): Promise<void> {
    if (!CPF.valido(this.documento())) {
      this.erro.set(
        this.documento().trim()
          ? $localize`${CPF.nome}:nome: inválido. Confira o número.`
          : $localize`Informe o número do seu documento (${CPF.nome}:nome:).`,
      );
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    try {
      const usuario = await firstValueFrom(
        this.usuarios.informarPais(this.pais(), CPF.limpar(this.documento())),
      );
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
