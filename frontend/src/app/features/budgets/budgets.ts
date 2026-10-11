import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { Orcamento } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { mesKey, mesPorExtenso } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { Icone } from '../../core/ui/icone';
import { Goals } from '../goals/goals';

/**
 * Orcamento: o limite total do mes (gratis) e, abaixo dele, o limite por categoria
 * (Premium) com alerta em 80% e 100%. Ver e apagar o que ja existe segue livre no
 * gratuito. Antes eram duas telas, "Metas" e "Orcamentos", que se confundiam.
 */
@Component({
  selector: 'app-budgets',
  imports: [Icone, FormsModule, MoedaPipe, RouterLink, Goals],
  templateUrl: './budgets.html',
  styles: `
    .progresso.atencao span {
      background: var(--button-bg);
    }
  `,
})
export class Budgets {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);

  protected readonly mes = mesKey(new Date());
  protected readonly rotuloDoMes = mesPorExtenso(this.mes);
  protected readonly orcamentos = signal<Orcamento[]>([]);
  protected readonly categorias = signal<string[]>([]);
  protected readonly erro = signal('');
  protected readonly ocupado = signal(false);

  protected readonly categoria = signal('');
  protected readonly limite = signal<number | null>(null);

  protected readonly semOrcamento = computed(() => {
    const usadas = new Set(this.orcamentos().map((o) => o.categoria));
    return this.categorias().filter((c) => !usadas.has(c));
  });

  constructor() {
    this.finance.listarCategorias().subscribe({
      next: ({ categorias }) => this.categorias.set(categorias),
    });
    this.carregar();
  }

  protected largura(orcamento: Orcamento): number {
    return Math.min(100, orcamento.percentual);
  }

  protected editar(orcamento: Orcamento): void {
    this.categoria.set(orcamento.categoria);
    this.limite.set(orcamento.limite);
  }

  protected salvar(): void {
    const limite = this.limite();
    if (!this.categoria() || limite === null || limite <= 0) {
      this.erro.set($localize`Escolha a categoria e um limite maior que zero.`);
      return;
    }
    this.ocupado.set(true);
    this.erro.set('');
    this.finance.definirOrcamento(this.categoria(), limite).subscribe({
      next: () => {
        this.limite.set(null);
        this.categoria.set('');
        this.ocupado.set(false);
        this.carregar();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.ocupado.set(false);
      },
    });
  }

  protected excluir(orcamento: Orcamento): void {
    this.finance.excluirOrcamento(orcamento.id).subscribe({
      next: () => this.carregar(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.finance.orcamentos(this.mes).subscribe({
      next: (lista) => this.orcamentos.set(lista),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
