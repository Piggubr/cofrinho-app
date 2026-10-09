import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ContaFixa, ContaOuCartao, Orcamento } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { dataIso } from '../../core/ui/datas';
import { MoedaPipe } from '../../core/ui/moeda';

/** Lembretes do mes no painel: contas fixas vencidas ou para vencer. */
@Component({
  selector: 'app-avisos-do-mes',
  imports: [MoedaPipe, DataBrPipe, RouterLink],
  template: `
    @if (orcamentosEmAlerta().length) {
      <section class="cartao" aria-labelledby="titulo-orcamentos-alerta">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-orcamentos-alerta">Orçamento</h2>
          <a i18n routerLink="/orcamentos" class="botao contorno pequeno">Ver orçamentos</a>
        </div>
        @for (orcamento of orcamentosEmAlerta(); track orcamento.id) {
          <p class="linha-detalhe" [class.atrasada]="orcamento.alerta === 'ESTOUROU'">
            @if (orcamento.alerta === 'ESTOUROU') {
              <span i18n>{{ orcamento.categoria }} passou do limite: {{ orcamento.gasto | moeda }} de {{ orcamento.limite | moeda }}</span>
            } @else {
              <span i18n>{{ orcamento.categoria }} já usou {{ orcamento.percentual }}% do limite</span>
            }
          </p>
        }
      </section>
    }
    @if (faturasAVencer().length) {
      <section class="cartao" aria-labelledby="titulo-faturas">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-faturas">Fatura do cartão</h2>
          <a i18n routerLink="/contas-e-cartoes" class="botao contorno pequeno">Ver faturas</a>
        </div>
        @for (cartao of faturasAVencer(); track cartao.id) {
          <p class="linha-detalhe">
            <span i18n
              >{{ cartao.nome }} vence em {{ cartao.faturaAPagar!.vencimento | dataBr }}:
              {{ cartao.faturaAPagar!.total | moeda }}</span
            >
          </p>
        }
      </section>
    }
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
