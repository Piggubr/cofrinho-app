import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ContaFixa } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { MoedaPipe } from '../../core/ui/moeda';

/** Lembretes do mes no painel: contas fixas vencidas ou para vencer. */
@Component({
  selector: 'app-avisos-do-mes',
  imports: [MoedaPipe, DataBrPipe, RouterLink],
  template: `
    @if (contasEmAberto().length) {
      <section class="cartao" aria-labelledby="titulo-avisos">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-avisos">Para pagar</h2>
          <a i18n routerLink="/contas-fixas" class="botao contorno pequeno">Ver contas</a>
        </div>
        @for (conta of contasEmAberto(); track conta.id) {
          <p class="linha-detalhe" [class.atrasada]="conta.situacao === 'VENCIDA'">
            @if (conta.situacao === 'VENCIDA') {
              <span i18n
                >Venceu em {{ conta.vencimento | dataBr }}: {{ conta.descricao }},
                {{ conta.valor | moeda }}</span
              >
            } @else {
              <span i18n
                >Vence em {{ conta.vencimento | dataBr }}: {{ conta.descricao }},
                {{ conta.valor | moeda }}</span
              >
            }
          </p>
        }
      </section>
    }
  `,
  styles: `
    .atrasada {
      color: var(--over);
    }
  `,
})
export class AvisosDoMes {
  private readonly finance = inject(FinanceService);

  /** Mes no formato AAAA-MM. */
  readonly mes = input.required<string>();
  private readonly contas = signal<ContaFixa[]>([]);
  protected readonly contasEmAberto = computed(() =>
    this.contas().filter((c) => c.situacao !== 'PAGA'),
  );

  constructor() {
    effect((onCleanup) => {
      const pedido = this.finance.contasFixas(this.mes()).subscribe({
        next: (contas) => this.contas.set(contas),
        // Lembrete e informativo: sem ele o painel segue.
        error: () => this.contas.set([]),
      });
      onCleanup(() => pedido.unsubscribe());
    });
  }
}
