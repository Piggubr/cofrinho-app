import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { RewardsService } from '../../core/api/rewards.service';
import { AuthService } from '../../core/auth/auth.service';
import { Premio, Resgate, SaldoDeMoedas } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Fofocoins e premios.
 *
 * <p>Ajustar saldo e administrar premios e exclusivo do administrador; resgatar e
 * de quem tem as moedas. O backend recusa de qualquer forma, mas esconder o que a
 * pessoa nao pode fazer evita oferecer um botao que sempre daria erro.</p>
 */
@Component({
  selector: 'app-rewards',
  imports: [FormsModule],
  templateUrl: './rewards.html',
  styleUrl: './rewards.scss',
})
export class Rewards {
  private readonly rewards = inject(RewardsService);
  protected readonly auth = inject(AuthService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');

  protected readonly saldo = signal<SaldoDeMoedas | null>(null);
  protected readonly premios = signal<Premio[]>([]);
  protected readonly resgates = signal<Resgate[]>([]);
  protected readonly mostrarHistorico = signal(false);

  protected readonly ajusteValor = signal<number | null>(null);
  protected readonly ajusteMotivo = signal('');

  protected readonly novoNome = signal('');
  protected readonly novaDescricao = signal('');
  protected readonly novoPreco = signal<number | null>(null);

  constructor() {
    this.carregar();
  }

  protected ajustar(): void {
    const valor = this.ajusteValor();
    const motivo = this.ajusteMotivo().trim();

    if (!valor) {
      this.erro.set('Digite uma quantidade válida de Fofocoins.');
      return;
    }
    if (!motivo) {
      this.erro.set('Explique o motivo do ajuste.');
      return;
    }

    this.erro.set('');
    this.rewards.ajustarMoedas(valor, motivo).subscribe({
      next: (saldo) => {
        this.saldo.set(saldo);
        this.ajusteValor.set(null);
        this.ajusteMotivo.set('');
        this.aviso.set('Saldo atualizado.');
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected criarPremio(): void {
    const nome = this.novoNome().trim();
    const preco = this.novoPreco();

    if (!nome) {
      this.erro.set('Digite o nome do prêmio.');
      return;
    }
    if (!preco || preco <= 0) {
      this.erro.set('Digite um preço válido.');
      return;
    }

    this.erro.set('');
    this.rewards
      .criarPremio({ nome, descricao: this.novaDescricao().trim(), preco, ativo: true })
      .subscribe({
        next: () => {
          this.novoNome.set('');
          this.novaDescricao.set('');
          this.novoPreco.set(null);
          this.recarregarPremios();
        },
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
  }

  protected resgatar(premio: Premio): void {
    if (!confirm(`Resgatar "${premio.nome}" por ${premio.preco} Fofocoins?`)) {
      return;
    }

    this.erro.set('');
    this.rewards.resgatar(premio.id).subscribe({
      next: (resgate) => {
        this.aviso.set(`Resgatado. Saldo agora: ${resgate.saldo} Fofocoins.`);
        this.carregar();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected desativar(premio: Premio): void {
    if (!confirm(`Tirar "${premio.nome}" da lista?`)) {
      return;
    }
    this.rewards.excluirPremio(premio.id).subscribe({
      next: () => this.recarregarPremios(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternarHistorico(): void {
    this.mostrarHistorico.update((aberto) => !aberto);
  }

  private recarregarPremios(): void {
    this.rewards.listarPremios(this.auth.ehAdmin()).subscribe({
      next: (premios) => this.premios.set(premios),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    forkJoin({
      saldo: this.rewards.consultarSaldo(),
      premios: this.rewards.listarPremios(this.auth.ehAdmin()),
      resgates: this.rewards.listarResgates(),
    }).subscribe({
      next: ({ saldo, premios, resgates }) => {
        this.saldo.set(saldo);
        this.premios.set(premios);
        this.resgates.set(resgates);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
