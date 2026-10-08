import { Component, inject, signal } from '@angular/core';
import { FinanceService } from '../../core/api/finance.service';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UsersService } from '../../core/api/users.service';
import { MoedaDisponivel, PigguRole, Usuario } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Perfil e, para o administrador, as contas da instalacao.
 *
 * <p>O cadastro e aberto: quem entra em cada familia e assunto do titular, na tela
 * Familia.</p>
 */
@Component({
  selector: 'app-profile',
  imports: [FormsModule, RouterLink],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly users = inject(UsersService);
  protected readonly auth = inject(AuthService);

  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly contas = signal<Usuario[]>([]);
  protected readonly carregandoAdmin = signal(false);

  protected readonly perfis: PigguRole[] = ['ADMIN', 'TITULAR', 'MEMBRO'];

  private readonly finance = inject(FinanceService);
  protected readonly moedas = signal<MoedaDisponivel[]>([]);
  protected readonly moeda = signal(this.auth.usuario()?.preferencias?.moeda ?? 'EUR');
  protected readonly moedaConversao = signal(
    this.auth.usuario()?.preferencias?.moedaConversao ?? 'BRL',
  );
  protected readonly mostrarCotacao = signal(
    this.auth.usuario()?.preferencias?.mostrarCotacao ?? true,
  );
  protected readonly salvandoPreferencias = signal(false);

  constructor() {
    this.finance.listarMoedas().subscribe({
      next: (moedas) => this.moedas.set(moedas),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    if (this.auth.ehAdmin()) {
      this.carregarAdmin();
    }
  }

  protected salvarPreferencias(): void {
    this.salvandoPreferencias.set(true);
    this.erro.set('');
    this.aviso.set('');
    this.users
      .salvarPreferencias({
        moeda: this.moeda(),
        moedaConversao: this.moedaConversao(),
        mostrarCotacao: this.mostrarCotacao(),
      })
      .subscribe({
        next: (usuario) => {
          this.auth.atualizarUsuario(usuario);
          this.aviso.set('Preferências salvas.');
          this.salvandoPreferencias.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvandoPreferencias.set(false);
        },
      });
  }

  protected sair(): void {
    void this.auth.sair();
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
  }
}
