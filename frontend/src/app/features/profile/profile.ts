import { Component, inject, signal } from '@angular/core';
import { FinanceService } from '../../core/api/finance.service';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UsersService } from '../../core/api/users.service';
import { ModuloDeEstiloDeVida, MoedaDisponivel, PigguRole, Usuario } from '../../core/api/models';
import { baixar } from '../../core/ui/arquivo';
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

  protected readonly perfis: PigguRole[] = ['ADMIN', 'TITULAR', 'PARCEIRO', 'MEMBRO'];

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
  protected readonly salvandoModulos = signal(false);

  protected readonly modulos: { id: ModuloDeEstiloDeVida; nome: string }[] = [
    { id: 'compras', nome: $localize`Compras` },
    { id: 'lugares', nome: $localize`Lugares` },
    { id: 'filmes', nome: $localize`Filmes` },
    { id: 'fotos', nome: $localize`Fotos` },
    { id: 'premios', nome: $localize`Prêmios` },
  ];
  protected readonly excluindo = signal(false);

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
          this.aviso.set($localize`Preferências salvas.`);
          this.salvandoPreferencias.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvandoPreferencias.set(false);
        },
      });
  }

  /** Sem a lista (conta antiga, demo), todos estao ligados. */
  protected ligado(modulo: ModuloDeEstiloDeVida): boolean {
    const ligados = this.auth.usuario()?.preferencias?.modulos;
    return !ligados || ligados.includes(modulo);
  }

  /** Cada toque ja salva: o menu muda na hora. */
  protected alternarModulo(modulo: ModuloDeEstiloDeVida): void {
    const ligados = this.modulos.map((m) => m.id).filter((id) => this.ligado(id));
    const novos = ligados.includes(modulo)
      ? ligados.filter((id) => id !== modulo)
      : [...ligados, modulo];
    this.salvandoModulos.set(true);
    this.erro.set('');
    this.users.salvarModulos(novos).subscribe({
      next: (usuario) => {
        this.auth.atualizarUsuario(usuario);
        this.salvandoModulos.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvandoModulos.set(false);
      },
    });
  }

  protected baixarMeusDados(): void {
    this.erro.set('');
    this.users.exportarMeusDados().subscribe({
      next: (arquivo) => baixar(arquivo, 'piggu-meus-dados.json'),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluirConta(): void {
    const texto =
      $localize`Excluir sua conta apaga o que é só seu. Se você for a última pessoa da família, ` +
      $localize`tudo da família é apagado. Se outras pessoas ficarem, o que você lançou fica com elas, ` +
      $localize`sem o seu nome. Uma assinatura Premium feita por você é cancelada. Isso não tem volta. ` +
      $localize`Digite EXCLUIR para confirmar.`;
    if (prompt(texto)?.trim().toUpperCase() !== 'EXCLUIR') {
      return;
    }
    this.excluindo.set(true);
    this.users.excluirConta().subscribe({
      next: () => void this.auth.encerrarLocalmente(),
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.excluindo.set(false);
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
