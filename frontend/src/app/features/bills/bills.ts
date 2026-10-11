import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { ContaFixa } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { Icone } from '../../core/ui/icone';

/**
 * Contas do mes (as contas fixas): o que se repete todo mes. "Marcar como paga" lanca o gasto do mes;
 * com o automatico ligado, o Piggu lanca sozinho no dia do vencimento.
 */
@Component({
  selector: 'app-bills',
  imports: [Icone, FormsModule, MoedaPipe, DataBrPipe],
  templateUrl: './bills.html',
  styleUrl: './bills.scss',
})
export class Bills {
  private readonly finance = inject(FinanceService);

  protected readonly mesAtual = signal(mesKey(new Date()));
  protected readonly contas = signal<ContaFixa[]>([]);
  protected readonly categorias = signal<string[]>([]);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly ocupado = signal(false);

  protected readonly descricao = signal('');
  protected readonly categoria = signal('');
  protected readonly valor = signal<number | null>(null);
  protected readonly dia = signal<number | null>(10);
  protected readonly automatico = signal(false);

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly totalEmAberto = computed(() =>
    this.contas()
      .filter((c) => c.situacao !== 'PAGA')
      .reduce((soma, c) => soma + c.valor, 0),
  );

  constructor() {
    this.finance.listarCategorias().subscribe({
      next: ({ categorias }) => {
        this.categorias.set(categorias);
        this.categoria.set(categorias[0] ?? '');
      },
    });
    this.carregar();
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar();
  }

  protected criar(): void {
    const descricao = this.descricao().trim();
    const valor = this.valor();
    const dia = this.dia();
    if (!descricao || valor === null || valor <= 0 || dia === null || dia < 1 || dia > 31) {
      this.erro.set($localize`Preencha descrição, valor e um dia de vencimento entre 1 e 31.`);
      return;
    }
    this.executar(
      this.finance.criarContaFixa({
        descricao,
        categoria: this.categoria(),
        valor,
        dia,
        automatico: this.automatico(),
      }),
      $localize`Conta fixa criada.`,
      () => {
        this.descricao.set('');
        this.valor.set(null);
      },
    );
  }

  protected pagar(conta: ContaFixa): void {
    this.executar(
      this.finance.pagarContaFixa(conta.id, this.mesAtual()),
      $localize`Conta lançada nos gastos.`,
    );
  }

  protected excluir(conta: ContaFixa): void {
    if (
      !confirm(
        $localize`Apagar a conta fixa "${conta.descricao}"? Os gastos já lançados continuam.`,
      )
    ) {
      return;
    }
    this.executar(this.finance.excluirContaFixa(conta.id), $localize`Conta fixa apagada.`);
  }

  private executar(pedido: Observable<unknown>, mensagem: string, depois?: () => void): void {
    this.ocupado.set(true);
    this.erro.set('');
    this.aviso.set('');
    pedido.subscribe({
      next: () => {
        depois?.();
        this.aviso.set(mensagem);
        this.ocupado.set(false);
        this.carregar();
      },
      error: (falha: unknown) => {
        this.erro.set(mensagemDeErro(falha));
        this.ocupado.set(false);
      },
    });
  }

  private carregar(): void {
    this.finance.contasFixas(this.mesAtual()).subscribe({
      next: (contas) => this.contas.set(contas),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
