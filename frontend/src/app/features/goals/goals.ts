import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { Gasto } from '../../core/api/models';
import { EuroPipe } from '../../core/ui/moeda.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { mesKey, mesPorExtenso } from '../../core/ui/datas';

interface MetaNaTela {
  readonly mes: string;
  readonly rotulo: string;
  readonly limite: number;
}

/**
 * Metas de gasto por mes.
 *
 * <p>Definir a meta do mes que ja esta em curso mostra de imediato quanto dele ja
 * foi consumido, que e a pergunta que a tela realmente responde.</p>
 */
@Component({
  selector: 'app-goals',
  imports: [FormsModule, EuroPipe],
  templateUrl: './goals.html',
  styleUrl: './goals.scss',
})
export class Goals {
  private readonly finance = inject(FinanceService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected readonly metas = signal<Record<string, number>>({});
  protected readonly gastosDoMes = signal<Gasto[]>([]);
  protected readonly mesEscolhido = signal(mesKey(new Date()));
  protected readonly limite = signal<number | null>(null);

  protected readonly lista = computed<MetaNaTela[]>(() =>
    Object.entries(this.metas())
      .map(([mes, limite]) => ({ mes, rotulo: mesPorExtenso(mes), limite }))
      .sort((a, b) => b.mes.localeCompare(a.mes)),
  );

  protected readonly totalDoMesAtual = computed(() =>
    this.gastosDoMes().reduce((soma, gasto) => soma + gasto.valor, 0),
  );

  protected readonly metaDoMesAtual = computed(() => this.metas()[mesKey(new Date())] ?? 0);

  protected readonly progresso = computed(() => {
    const meta = this.metaDoMesAtual();
    return meta > 0 ? Math.min(100, Math.round((this.totalDoMesAtual() / meta) * 100)) : 0;
  });

  protected readonly estourou = computed(
    () => this.metaDoMesAtual() > 0 && this.totalDoMesAtual() > this.metaDoMesAtual(),
  );

  constructor() {
    forkJoin({
      metas: this.finance.listarMetas(),
      gastos: this.finance.listarGastos(mesKey(new Date())),
    }).subscribe({
      next: ({ metas, gastos }) => {
        this.metas.set(metas);
        this.gastosDoMes.set(gastos);
        this.limite.set(metas[this.mesEscolhido()] ?? null);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  protected aoTrocarMes(mes: string): void {
    this.mesEscolhido.set(mes);
    this.limite.set(this.metas()[mes] ?? null);
  }

  protected salvar(): void {
    const limite = this.limite();
    if (!limite || limite <= 0) {
      this.erro.set('Digite um valor válido para a meta.');
      return;
    }

    this.salvando.set(true);
    this.erro.set('');
    this.finance.definirMeta(this.mesEscolhido(), limite).subscribe({
      next: (metas) => {
        this.metas.set(metas);
        this.salvando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  protected editar(meta: MetaNaTela): void {
    this.mesEscolhido.set(meta.mes);
    this.limite.set(meta.limite);
  }
}
