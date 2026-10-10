import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ContaFixa, ContaOuCartao, Orcamento } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { Icone } from '../../core/ui/icone';
import { dataIso } from '../../core/ui/datas';
import { MoedaPipe } from '../../core/ui/moeda';

/** Lembretes do mes no painel: contas fixas vencidas ou para vencer. */
@Component({
  selector: 'app-avisos-do-mes',
  imports: [MoedaPipe, DataBrPipe, RouterLink, Icone],
  template: `
    @if (orcamentosEmAlerta().length || faturasAVencer().length || contasEmAberto().length) {
      <section class="cartao" aria-labelledby="titulo-avisos">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-avisos">Para ficar de olho</h2>
        </div>

        @for (orcamento of orcamentosEmAlerta(); track orcamento.id) {
          <a
            routerLink="/orcamentos"
            class="aviso-linha"
            [class.atrasada]="orcamento.alerta === 'ESTOUROU'"
          >
            <span class="aviso-icone"><app-icone nome="pizza" [tamanho]="18" /></span>
            <span class="aviso-texto">
              <strong i18n>Orçamento</strong>
              @if (orcamento.alerta === 'ESTOUROU') {
                <span i18n
                  >{{ orcamento.categoria }} passou do limite: {{ orcamento.gasto | moeda }} de
                  {{ orcamento.limite | moeda }}</span
                >
              } @else {
                <span i18n
                  >{{ orcamento.categoria }} já usou {{ orcamento.percentual }}% do limite</span
                >
              }
            </span>
            <app-icone nome="direita" [tamanho]="18" class="aviso-seta" />
          </a>
        }

        @for (cartao of faturasAVencer(); track cartao.id) {
          <a routerLink="/contas-e-cartoes" class="aviso-linha">
            <span class="aviso-icone"><app-icone nome="cartao" [tamanho]="18" /></span>
            <span class="aviso-texto">
              <strong i18n>Fatura do cartão</strong>
              <span i18n
                >{{ cartao.nome }} vence em {{ cartao.faturaAPagar!.vencimento | dataBr }}:
                {{ cartao.faturaAPagar!.total | moeda }}</span
              >
            </span>
            <app-icone nome="direita" [tamanho]="18" class="aviso-seta" />
          </a>
        }

        @for (conta of contasEmAberto(); track conta.id) {
          <a
            routerLink="/contas-fixas"
            class="aviso-linha"
            [class.atrasada]="conta.situacao === 'VENCIDA'"
          >
            <span class="aviso-icone"><app-icone nome="recibo" [tamanho]="18" /></span>
            <span class="aviso-texto">
              <strong>{{ conta.descricao }} · {{ conta.valor | moeda }}</strong>
              @if (conta.situacao === 'VENCIDA') {
                <span i18n>Venceu em {{ conta.vencimento | dataBr }}</span>
              } @else {
                <span i18n>Vence em {{ conta.vencimento | dataBr }}</span>
              }
            </span>
            <app-icone nome="direita" [tamanho]="18" class="aviso-seta" />
          </a>
        }
      </section>
    }
  `,
  styles: `
    .aviso-linha {
      display: flex;
      align-items: center;
      gap: 0.8rem;
      padding: 0.7rem 0;
      border-bottom: 1px solid var(--border);
      color: inherit;
      text-decoration: none;
    }
    .aviso-linha:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }
    .aviso-icone {
      display: grid;
      place-items: center;
      width: 38px;
      height: 38px;
      border-radius: 12px;
      background: var(--rose-soft);
      color: var(--rose-deep);
      flex-shrink: 0;
    }
    .aviso-texto {
      display: grid;
      flex: 1;
      min-width: 0;
      font-size: 0.8rem;
      color: var(--muted);
    }
    .aviso-texto strong {
      font-size: 0.9rem;
      font-weight: 500;
      color: var(--ink);
    }
    .aviso-seta {
      color: var(--muted);
    }
    .atrasada .aviso-icone {
      background: var(--danger-soft);
      color: var(--danger);
    }
    .atrasada .aviso-texto span {
      color: var(--danger);
    }
  `,
})
export class AvisosDoMes {
  private readonly finance = inject(FinanceService);

  /** Mes no formato AAAA-MM. */
  readonly mes = input.required<string>();
  private readonly contas = signal<ContaFixa[]>([]);
  private readonly orcamentos = signal<Orcamento[]>([]);
  private readonly cartoes = signal<ContaOuCartao[]>([]);
  /** Fatura fechada, com valor, que vence nos proximos 10 dias. */
  protected readonly faturasAVencer = computed(() => {
    const limite = dataIso(new Date(Date.now() + 10 * 86_400_000));
    return this.cartoes().filter(
      (c) => c.faturaAPagar && c.faturaAPagar.total > 0 && c.faturaAPagar.vencimento <= limite,
    );
  });
  protected readonly orcamentosEmAlerta = computed(() =>
    this.orcamentos().filter((o) => o.alerta !== 'OK'),
  );
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
      const alertas = this.finance.orcamentos(this.mes()).subscribe({
        next: (orcamentos) => this.orcamentos.set(orcamentos),
        error: () => this.orcamentos.set([]),
      });
      onCleanup(() => {
        pedido.unsubscribe();
        alertas.unsubscribe();
      });
    });
    // A fatura nao depende do mes escolhido no painel: e sempre a proxima a vencer.
    this.finance.contas().subscribe({
      next: (contas) => this.cartoes.set(contas),
      error: () => this.cartoes.set([]),
    });
  }
}
