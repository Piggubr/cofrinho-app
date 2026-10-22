import {
  Component,
  Injector,
  afterNextRender,
  inject,
  input,
  linkedSignal,
  output,
  viewChild,
} from '@angular/core';
import { FormularioDeGasto, GastoLancado } from '../../features/expenses/formulario-de-gasto';
import { FormularioDeReceita, ReceitaLancada } from '../../features/incomes/formulario-de-receita';
import { toqueNaTela } from '../../core/ui/focar';
import { Icone } from '../../core/ui/icone';
import { Lancamentos } from '../../core/ui/lancamentos';

type Tipo = 'gasto' | 'receita';

/**
 * Lancar gasto ou receita sem sair da tela: o "+" abre este painel no canto (no
 * celular, como uma folha acima da barra de abas).
 *
 * <p>Usa os mesmos formularios das telas de Gastos e de Receitas, com todos os campos
 * (parcelas, recorrencia, outra moeda, divisao e a foto do recibo).</p>
 */
@Component({
  selector: 'app-lancamento-rapido',
  imports: [Icone, FormularioDeGasto, FormularioDeReceita],
  templateUrl: './lancamento-rapido.html',
  styleUrl: './lancamento-rapido.scss',
  host: {
    role: 'dialog',
    'aria-modal': 'false',
    'aria-labelledby': 'titulo-lancamento-rapido',
    '(keydown.escape)': 'fechar.emit()',
  },
})
export class LancamentoRapido {
  private readonly lancamentos = inject(Lancamentos);
  private readonly injetor = inject(Injector);

  /** Foto escolhida no balao do "+": o painel ja abre lendo o recibo. */
  readonly foto = input<File | null>(null);

  /** Aba com que o painel abre: "Lançar receita" no balao ja chega em Receita. */
  readonly tipoInicial = input<Tipo>('gasto');

  readonly fechar = output<void>();
  /** Lancamento gravado, com a mensagem para o aviso; quem abriu o painel o fecha. */
  readonly lancado = output<string>();

  protected readonly tipo = linkedSignal<Tipo>(() => this.tipoInicial());
  private readonly gasto = viewChild(FormularioDeGasto);
  private readonly receita = viewChild(FormularioDeReceita);

  constructor() {
    afterNextRender(() => {
      // No celular o teclado so sobe quando a pessoa toca no valor.
      if (!this.foto() && !toqueNaTela()) {
        this.focarValor();
      }
    });
  }

  protected trocarTipo(tipo: Tipo): void {
    this.tipo.set(tipo);
    if (!toqueNaTela()) {
      afterNextRender(() => this.focarValor(), { injector: this.injetor });
    }
  }

  /** Gravou: avisa as telas abertas e entrega a mensagem para o painel fechar. */
  protected concluido(lancado: GastoLancado | ReceitaLancada): void {
    this.lancamentos.avisar();
    this.lancado.emit(lancado.mensagem);
  }

  private focarValor(): void {
    (this.tipo() === 'gasto' ? this.gasto() : this.receita())?.focarValor();
  }
}
