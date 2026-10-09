import { Component, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ResumoDoMes } from '../../core/api/models';
import { MoedaPipe } from '../../core/ui/moeda';

/** Card do mes no painel: receitas - gastos = sobra, e quanto disso foi poupado. */
@Component({
  selector: 'app-resumo-do-mes',
  imports: [MoedaPipe, RouterLink, DecimalPipe],
  template: `
    @if (resumo(); as r) {
      <section class="cartao" aria-labelledby="titulo-resumo">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-resumo">Este mês</h2>
          @if (r.taxaDePoupanca !== null) {
            <span i18n class="etiqueta">{{ r.taxaDePoupanca | number: '1.0-1' }}% poupado</span>
          }
        </div>
        <div class="resumo-do-mes">
          <div>
            <p i18n class="linha-detalhe">Receitas</p>
            <p class="numero">{{ r.receitas | moeda }}</p>
          </div>
          <div>
            <p i18n class="linha-detalhe">Gastos</p>
            <p class="numero">{{ r.gastos | moeda }}</p>
          </div>
          <div>
            <p i18n class="linha-detalhe">Sobra</p>
            <p class="numero" [class.negativo]="r.sobra < 0">{{ r.sobra | moeda }}</p>
          </div>
        </div>
        @if (r.receitas === 0) {
          <p i18n class="linha-detalhe">
            Lance as <a routerLink="/receitas">receitas do mês</a> para ver quanto sobrou e quanto
            você poupou.
          </p>
        }
      </section>
    }
  `,
  styles: `
    .resumo-do-mes {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 0.75rem;
    }
    .negativo {
      color: var(--over);
    }
  `,
})
export class ResumoDoMesCard {
  private readonly finance = inject(FinanceService);

  /** Mes no formato AAAA-MM. */
  readonly mes = input.required<string>();
  protected readonly resumo = signal<ResumoDoMes | null>(null);

  constructor() {
    effect((onCleanup) => {
      const pedido = this.finance.resumoDoMes(this.mes()).subscribe({
        next: (resumo) => this.resumo.set(resumo),
        // O card e informativo: sem ele o painel segue.
        error: () => this.resumo.set(null),
      });
      onCleanup(() => pedido.unsubscribe());
    });
  }
}
