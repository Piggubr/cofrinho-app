import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Lancamentos } from '../../core/ui/lancamentos';
import { Focar } from '../../core/ui/focar';
import { FinanceService } from '../../core/api/finance.service';
import { Receita } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { ResumoDoMesCard } from '../dashboard/resumo-do-mes';
import { Icone } from '../../core/ui/icone';
import { Vazio } from '../../core/ui/vazio';

/** Receitas do mes: com elas o painel mostra a sobra e a taxa de poupanca. */
@Component({
  selector: 'app-incomes',
  imports: [Focar, Icone, FormsModule, MoedaPipe, DataBrPipe, ResumoDoMesCard, Vazio],
  templateUrl: './incomes.html',
})
export class Incomes {
  private readonly finance = inject(FinanceService);

  protected readonly mesAtual = signal(mesKey(new Date()));
  protected readonly receitas = signal<Receita[]>([]);
  protected readonly categorias = signal<string[]>([]);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly salvando = signal(false);

  protected readonly data = signal(hojeIso());
  protected readonly descricao = signal('');
  protected readonly categoria = signal('Salário');
  protected readonly valor = signal<number | null>(null);

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly total = computed(() => this.receitas().reduce((soma, r) => soma + r.valor, 0));

  constructor() {
    // Lancado pelo "+" com esta tela aberta: recarrega (a primeira leitura e so a inicial).
    const lancamentos = inject(Lancamentos);
    let primeira = true;
    effect(() => {
      lancamentos.versao();
      if (primeira) {
        primeira = false;
        return;
      }
      untracked(() => this.carregar());
    });
    this.finance.categoriasDeReceita().subscribe({ next: (lista) => this.categorias.set(lista) });
    this.carregar();
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar();
  }

  protected lancar(): void {
    const descricao = this.descricao().trim();
    const valor = this.valor();
    if (!descricao) {
      this.erro.set($localize`Digite a descrição da receita.`);
      return;
    }
    if (valor === null || valor <= 0) {
      this.erro.set($localize`Digite um valor válido.`);
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .lancarReceita({ data: this.data(), descricao, categoria: this.categoria(), valor })
      .subscribe({
        next: () => {
          this.descricao.set('');
          this.valor.set(null);
          this.aviso.set($localize`Receita lançada.`);
          this.salvando.set(false);
          this.mesAtual.set(this.data().slice(0, 7));
          this.carregar();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }

  protected excluir(receita: Receita): void {
    if (!confirm($localize`Apagar a receita "${receita.descricao}"?`)) {
      return;
    }
    this.finance.excluirReceita(receita.id).subscribe({
      next: () => this.carregar(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.finance.listarReceitas(this.mesAtual()).subscribe({
      next: (lista) => this.receitas.set(lista),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
