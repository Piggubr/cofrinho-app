import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { Gasto, Nota } from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { dataIso, gradeDoMes, hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';

/** Um dia da grade, com o que acontece nele. */
interface DiaDoMes {
  readonly dia: number | null;
  readonly iso: string;
  readonly totalGasto: number;
  readonly notas: number;
  readonly hoje: boolean;
}

/**
 * Calendario com notas e lembretes.
 *
 * <p>Uma nota com valor vira tambem um gasto: o backend cria os dois juntos e
 * apagar a nota apaga o gasto. Por isso a tela avisa quando um lembrete vai virar
 * lancamento, em vez de deixar a pessoa descobrir depois.</p>
 */
@Component({
  selector: 'app-calendar',
  imports: [FormsModule, MoedaPipe, DataBrPipe],
  templateUrl: './calendar.html',
  styleUrl: './calendar.scss',
})
export class Calendar {
  protected readonly moeda = inject(MoedaService);
  private readonly finance = inject(FinanceService);

  protected readonly diasDaSemana = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);

  protected readonly notas = signal<Nota[]>([]);
  protected readonly gastos = signal<Gasto[]>([]);
  protected readonly categorias = signal<string[]>([]);
  protected readonly mesAtual = signal(mesKey(new Date()));
  protected readonly diaSelecionado = signal<string | null>(null);

  protected readonly titulo = signal('');
  protected readonly texto = signal('');
  protected readonly dataDaNota = signal(hojeIso());
  protected readonly valorDaNota = signal<number | null>(null);
  protected readonly categoriaDaNota = signal('');

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));

  protected readonly viraGasto = computed(() => (this.valorDaNota() ?? 0) > 0);

  protected readonly grade = computed<DiaDoMes[]>(() => {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    const referencia = new Date(ano, mes - 1, 1);
    const hoje = hojeIso();

    return gradeDoMes(referencia).map((dia) => {
      if (dia === null) {
        return { dia: null, iso: '', totalGasto: 0, notas: 0, hoje: false };
      }
      const iso = dataIso(new Date(ano, mes - 1, dia));
      return {
        dia,
        iso,
        totalGasto: this.gastos()
          .filter((gasto) => gasto.data === iso)
          .reduce((soma, gasto) => soma + gasto.valor, 0),
        notas: this.notas().filter((nota) => nota.data === iso).length,
        hoje: iso === hoje,
      };
    });
  });

  protected readonly notasDoDia = computed(() => {
    const dia = this.diaSelecionado();
    return dia ? this.notas().filter((nota) => nota.data === dia) : [];
  });

  protected readonly gastosDoDia = computed(() => {
    const dia = this.diaSelecionado();
    return dia ? this.gastos().filter((gasto) => gasto.data === dia) : [];
  });

  protected readonly notasSemData = computed(() => this.notas().filter((nota) => !nota.data));

  constructor() {
    this.carregar();
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.diaSelecionado.set(null);
    this.carregar();
  }

  protected selecionarDia(dia: DiaDoMes): void {
    if (!dia.dia) {
      return;
    }
    this.diaSelecionado.set(dia.iso === this.diaSelecionado() ? null : dia.iso);
    this.dataDaNota.set(dia.iso);
  }

  protected criarNota(): void {
    const titulo = this.titulo().trim();
    if (!titulo) {
      this.erro.set('Digite o título da nota.');
      return;
    }
    if (this.viraGasto() && !this.dataDaNota()) {
      this.erro.set('Escolha a data do evento pago.');
      return;
    }

    this.erro.set('');
    this.salvando.set(true);
    this.finance
      .criarNota({
        titulo,
        texto: this.texto().trim(),
        data: this.dataDaNota() || null,
        valor: this.valorDaNota() ?? 0,
        categoria: this.categoriaDaNota() || null,
      })
      .subscribe({
        next: () => {
          this.titulo.set('');
          this.texto.set('');
          this.valorDaNota.set(null);
          this.salvando.set(false);
          this.carregar();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }

  protected excluirNota(nota: Nota): void {
    const aviso = nota.gastoId
      ? `Apagar "${nota.titulo}"? O gasto lançado junto também será apagado.`
      : `Apagar "${nota.titulo}"?`;
    if (!confirm(aviso)) {
      return;
    }

    this.finance.excluirNota(nota.id).subscribe({
      next: () => this.carregar(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    forkJoin({
      notas: this.finance.listarNotas(),
      gastos: this.finance.listarGastos(this.mesAtual()),
      categorias: this.finance.listarCategorias(),
    }).subscribe({
      next: ({ notas, gastos, categorias }) => {
        this.notas.set(notas);
        this.gastos.set(gastos);
        this.categorias.set(categorias.categorias);
        if (!this.categoriaDaNota()) {
          this.categoriaDaNota.set(categorias.categorias[0] ?? '');
        }
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
