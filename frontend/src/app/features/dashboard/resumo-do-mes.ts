import { Component, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ResumoDoMes } from '../../core/api/models';
import { Icone } from '../../core/ui/icone';
import { MoedaPipe } from '../../core/ui/moeda';

/** Card do mes no painel: receitas - gastos = sobra, e quanto disso foi poupado. */
@Component({
  selector: 'app-resumo-do-mes',
  imports: [MoedaPipe, RouterLink, DecimalPipe, Icone],
  template: `
    @if (resumo(); as r) {
      <section class="cartao resumo" aria-labelledby="titulo-resumo">
        <div class="resumo-topo">
          <h2 i18n id="titulo-resumo">Sobra do mês</h2>
          @if (r.taxaDePoupanca !== null) {
            <span i18n class="poupado">{{ r.taxaDePoupanca | number: '1.0-1' }}% poupado</span>
          }
        </div>
        <p class="sobra" [class.negativo]="r.sobra < 0">{{ r.sobra | moeda }}</p>
        <div class="resumo-do-mes">
          <div class="parcela">
            <span class="parcela-icone entrada"
              ><app-icone nome="entrada" [tamanho]="16" [traco]="2"
            /></span>
            <div>
              <p i18n class="rotulo">Receitas</p>
              <p class="valor">{{ r.receitas | moeda }}</p>
            </div>
          </div>
          <div class="parcela">
            <span class="parcela-icone saida"
              ><app-icone nome="carteira" [tamanho]="16" [traco]="2"
            /></span>
            <div>
              <p i18n class="rotulo">Gastos</p>
              <p class="valor">{{ r.gastos | moeda }}</p>
            </div>
          </div>
        </div>
        @if (r.receitas === 0) {
          <p i18n class="dica">
            Lance as <a routerLink="/receitas">receitas do mês</a> para ver quanto sobrou e quanto
            você poupou.
          </p>
        }
      </section>
    }
  `,
  styles: `
    .resumo {
      position: relative;
      overflow: hidden;
      border: none;
      color: #fff;
      background:
        radial-gradient(120% 140% at 100% 0%, var(--destaque-brilho) 0%, transparent 55%),
        linear-gradient(135deg, var(--destaque-1) 0%, var(--destaque-2) 55%, var(--destaque-3) 100%);
      box-shadow: 0 14px 34px color-mix(in srgb, var(--destaque-2) 30%, transparent);
    }
    .resumo-topo {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }
    h2 {
      margin: 0;
      font-size: 0.85rem;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.78);
      letter-spacing: 0;
    }
    .poupado {
      padding: 0.22rem 0.65rem;
      border-radius: 999px;
      background: rgba(255, 255, 255, 0.16);
      font-size: 0.72rem;
      font-weight: 600;
    }
    .sobra {
      margin: 0.35rem 0 1.1rem;
      font-size: 2.1rem;
      font-weight: 700;
      letter-spacing: -0.03em;
      line-height: 1.1;
    }
    .negativo {
      color: #ffc2dd;
    }
    .resumo-do-mes {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.6rem;
    }
    .parcela {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0.7rem 0.8rem;
      border-radius: 14px;
      background: rgba(255, 255, 255, 0.1);
    }
    .parcela-icone {
      display: grid;
      place-items: center;
      width: 30px;
      height: 30px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.16);
    }
    .rotulo {
      margin: 0;
      font-size: 0.72rem;
      color: rgba(255, 255, 255, 0.72);
    }
    .valor {
      margin: 0;
      font-size: 0.92rem;
      font-weight: 600;
    }
    .dica {
      margin: 0.9rem 0 0;
      font-size: 0.8rem;
      color: rgba(255, 255, 255, 0.8);
    }
    .dica a {
      color: #fff;
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
