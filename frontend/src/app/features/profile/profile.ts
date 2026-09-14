import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { UsersService } from '../../core/api/users.service';
import { PigguRole, Usuario } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Perfil e, para o administrador, a gestao de contas.
 *
 * <p>A lista de e-mails liberados era constante no codigo do Apps Script e exigia
 * reimplantar o script para mudar. Agora e uma tela.</p>
 */
@Component({
  selector: 'app-profile',
  imports: [FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly users = inject(UsersService);
  protected readonly auth = inject(AuthService);

  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly contas = signal<Usuario[]>([]);
  protected readonly autorizados = signal<{ email: string; role: PigguRole }[]>([]);
  protected readonly carregandoAdmin = signal(false);

  protected readonly novoEmail = signal('');
  protected readonly novoPerfil = signal<PigguRole>('FAMILIAR');

  protected readonly perfis: PigguRole[] = ['ADMIN', 'BEATRIZ', 'FAMILIAR'];

  constructor() {
    if (this.auth.ehAdmin()) {
      this.carregarAdmin();
    }
  }

  protected sair(): void {
    void this.auth.sair();
  }

  protected autorizar(): void {
    const email = this.novoEmail().trim();
    if (!email) {
      this.erro.set('Digite um e-mail válido.');
      return;
    }

    this.users.autorizar(email, this.novoPerfil()).subscribe({
      next: () => {
        this.novoEmail.set('');
        this.aviso.set('E-mail autorizado.');
        this.carregarAdmin();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected revogar(email: string): void {
    if (!confirm(`Remover o acesso de ${email}?`)) {
      return;
    }
    this.users.revogar(email).subscribe({
      next: () => this.carregarAdmin(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alterarPerfil(conta: Usuario, role: PigguRole): void {
    this.users.alterar(conta.id, { role }).subscribe({
      next: () => this.carregarAdmin(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternarAtivo(conta: Usuario): void {
    this.users.alterar(conta.id, { ativo: !conta.ativo }).subscribe({
      next: () => this.carregarAdmin(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregarAdmin(): void {
    this.carregandoAdmin.set(true);
    this.users.listar().subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.carregandoAdmin.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregandoAdmin.set(false);
      },
    });
    this.users.listarAutorizados().subscribe({
      next: (lista) => this.autorizados.set(lista),
      error: () => this.autorizados.set([]),
    });
  }
}
