import { Component, computed, inject, signal, effect, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Lancamentos } from '../../core/ui/lancamentos';
import { Focar } from '../../core/ui/focar';
import { FinanceService } from '../../core/api/finance.service';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { AReceber, ContaFixa, Receita } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { ResumoDoMesCard } from '../dashboard/resumo-do-mes';
import { FormularioDeReceita, ReceitaLancada } from './formulario-de-receita';
import { Icone } from '../../core/ui/icone';
import { Vazio } from '../../core/ui/vazio';

/** Receitas do mes: com elas o painel mostra a sobra e a taxa de poupanca. */
@Component({
  selector: 'app-incomes',
  imports: [Focar, Icone, FormsModule, MoedaPipe, DataBrPipe, ResumoDoMesCard, Vazio, FormularioDeReceita],
  templateUrl: './incomes.html',
  styles: `
    .atrasada .atraso {
      color: var(--danger);
    }
  `,
})
export class Incomes {
  private readonly finance = inject(FinanceService);

  protected readonly mesAtual = signal(mesKey(new Date()));
  protected readonly receitas = signal<Receita[]>([]);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly salvando = signal(false);

  protected readonly pessoas = inject(PessoasDaFamilia);
  private readonly auth = inject(AuthService);
  protected readonly eu = computed(() => this.auth.usuario()?.id ?? null);
  protected readonly recorrentes = signal<ContaFixa[]>([]);

  protected readonly hoje = hojeIso();
  protected readonly aReceber = signal<AReceber[]>([]);
  protected readonly quem = signal('');
  protected readonly motivo = signal('');
  protected readonly valorAReceber = signal<number | null>(null);
  protected readonly dataAReceber = signal(hojeIso());
  protected readonly faltouNoAReceber = signal<'quem' | 'valor' | null>(null);

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
    this.carregar();
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar();
  }

  /** Lancou pelo formulario: mostra o mes em que a receita entrou. */
  protected aoLancar(lancada: ReceitaLancada): void {
    this.erro.set('');
    this.aviso.set(lancada.mensagem);
    this.mesAtual.set(lancada.mes);
    this.carregar();
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

  protected pararDeRepetir(fixa: ContaFixa): void {
    if (!confirm($localize`Parar de lançar "${fixa.descricao}" todo mês? As receitas já lançadas continuam.`)) {
      return;
    }
    this.finance.excluirContaFixa(fixa.id).subscribe({
      next: () => this.carregar(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected criarAReceber(): void {
    const quem = this.quem().trim();
    const valor = this.valorAReceber();
    this.faltouNoAReceber.set(null);
    if (!quem) {
      this.faltouNoAReceber.set('quem');
      this.erro.set($localize`Diga quem está te devendo.`);
      return;
    }
    if (valor === null || valor <= 0) {
      this.faltouNoAReceber.set('valor');
      this.erro.set($localize`Digite quanto vão te pagar.`);
      return;
    }
    if (!this.dataAReceber()) {
      this.erro.set($localize`Escolha a data combinada para receber.`);
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .criarAReceber({ quem, descricao: this.motivo().trim(), valor, data: this.dataAReceber() })
      .subscribe({
        next: () => {
          this.quem.set('');
          this.motivo.set('');
          this.valorAReceber.set(null);
          this.aviso.set($localize`Guardado. Quando receber, toque em Recebi.`);
          this.salvando.set(false);
          this.carregarAReceber();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }

  /** "Recebi": vira receita de hoje, no mes em que o dinheiro entrou. */
  protected receber(conta: AReceber): void {
    this.finance.receber(conta.id).subscribe({
      next: () => {
        this.aviso.set($localize`${conta.quem}:quem: pagou. Entrou como receita de hoje.`);
        this.mesAtual.set(mesKey(new Date()));
        this.carregar();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluirAReceber(conta: AReceber): void {
    if (!confirm($localize`Apagar o que ${conta.quem}:quem: te deve? Nada entra como receita.`)) {
      return;
    }
    this.finance.excluirAReceber(conta.id).subscribe({
      next: () => this.carregarAReceber(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.finance.listarReceitas(this.mesAtual()).subscribe({
      next: (lista) => this.receitas.set(lista),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    this.finance.receitasRecorrentes(this.mesAtual()).subscribe({
      next: (lista) => this.recorrentes.set(lista),
      error: () => this.recorrentes.set([]),
    });
    this.carregarAReceber();
  }

  private carregarAReceber(): void {
    this.finance.aReceber().subscribe({
      next: (lista) => this.aReceber.set(lista),
      error: () => this.aReceber.set([]),
    });
  }
}
