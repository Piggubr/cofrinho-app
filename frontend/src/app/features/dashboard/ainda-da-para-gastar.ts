import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { ContaFixa, Visao } from '../../core/api/models';
import { mesKey, mesPorExtenso } from '../../core/ui/datas';
import { MoedaPipe } from '../../core/ui/moeda';
import { Carregando } from '../../core/ui/carregando';

export interface ContaDoMes {
  readonly teto: number;
  readonly tetoEhOLimite: boolean;
  readonly gasto: number;
  readonly contasAPagar: number;
  readonly resta: number;
  /** So no mes atual e com saldo: o que resta dividido pelos dias que faltam, hoje incluido. */
  readonly porDia: number | null;
}

/**
 * O numero fixo no topo do Painel (D-164), igual nos apps nativos: o limite do mes (ou, sem
 * limite, as receitas) menos o que ja foi gasto e as contas fixas de gasto que ainda vao sair.
 * Sem limite e sem receita nao ha numero.
 */
export function calcularContaDoMes(
  mes: string,
  limite: number,
  receitas: number,
  gasto: number,
  contas: readonly ContaFixa[],
  hoje: Date = new Date(),
): ContaDoMes | null {
  const tetoEhOLimite = limite > 0;
  const teto = tetoEhOLimite ? limite : receitas;
  if (teto <= 0) {
    return null;
  }
  const contasAPagar = contas
    .filter((c) => c.situacao !== 'PAGA' && c.tipo !== 'RECEITA')
    .reduce((soma, c) => soma + c.valor, 0);
  const resta = teto - gasto - contasAPagar;
  let porDia: number | null = null;
  if (mes === mesKey(hoje) && resta > 0) {
    const diasNoMes = new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0).getDate();
    // Para baixo de proposito, como no iOS e no Android: gastando o "por dia" todo dia, a
    // soma nunca passa do que resta. Em centavos inteiros para o ponto flutuante nao tirar 1 centavo.
    porDia = Math.floor(Math.round(resta * 100) / (diasNoMes - hoje.getDate() + 1)) / 100;
  }
  return { teto, tetoEhOLimite, gasto, contasAPagar, resta, porDia };
}

@Component({
  selector: 'app-ainda-da-para-gastar',
  imports: [Carregando, MoedaPipe, RouterLink],
  template: `
    <section class="cartao gastar" aria-labelledby="titulo-gastar">
      <h2 id="titulo-gastar">
        @if (passado()) {
          <ng-container i18n>Sobrou em {{ nomeDoMes() }}</ng-container>
        } @else {
          <ng-container i18n>Ainda dá para gastar em {{ nomeDoMes() }}</ng-container>
        }
      </h2>
      @if (!carregou()) {
        <app-carregando formato="texto" [linhas]="2" />
      } @else if (falhou()) {
        <p i18n>Não consegui carregar as contas do mês, então ainda não dá para fazer a conta.</p>
        <div class="acoes">
          <button i18n type="button" class="botao contorno pequeno" (click)="tentarDeNovo()">
            Tentar de novo
          </button>
        </div>
      } @else if (conta(); as c) {
        <p class="explicacao">
          @if (c.tetoEhOLimite) {
            <ng-container i18n>Limite do mês menos o que já foi gasto e as contas a pagar</ng-container>
          } @else {
            <ng-container i18n>Receitas do mês menos o que já foi gasto e as contas a pagar</ng-container>
          }
        </p>
        <p class="valor" [class.passou]="c.resta < 0">{{ c.resta | moeda }}</p>
        @if (c.resta < 0) {
          <p i18n class="passou">Já passou do que dava para gastar este mês.</p>
        } @else if (c.porDia !== null) {
          <p i18n>Cerca de {{ c.porDia | moeda }} por dia até o fim do mês</p>
        }
        <p class="conta">
          @if (c.tetoEhOLimite) {
            <ng-container i18n
              >Limite de {{ c.teto | moeda }}, menos {{ c.gasto | moeda }} já gastos e
              {{ c.contasAPagar | moeda }} de contas a pagar</ng-container
            >
          } @else {
            <ng-container i18n
              >Receitas de {{ c.teto | moeda }}, menos {{ c.gasto | moeda }} já gastos e
              {{ c.contasAPagar | moeda }} de contas a pagar</ng-container
            >
          }
        </p>
      } @else {
        <p i18n>
          Defina um limite para o mês ou lance suas receitas para ver quanto ainda dá para gastar.
        </p>
        <div class="acoes">
          <a i18n routerLink="/orcamentos" class="botao">Definir limite</a>
          <a i18n routerLink="/receitas" class="botao contorno">Lançar receitas</a>
        </div>
      }
    </section>
  `,
  styles: `
    .gastar {
      margin-bottom: 1rem;
      background: var(--rose-soft);
      border-color: var(--border-strong);
    }
    h2 {
      margin: 0 0 0.35rem;
      font-size: 1.05rem;
    }
    p {
      margin: 0.2rem 0 0;
    }
    .valor {
      font-size: 2.2rem;
      font-weight: 800;
      color: var(--primaria);
      font-variant-numeric: tabular-nums;
    }
    .passou {
      color: var(--danger-ink);
    }
    .valor.passou {
      color: var(--danger);
    }
    .explicacao {
      font-size: 0.85rem;
      opacity: 0.8;
    }
    .conta {
      margin-top: 0.5rem;
      font-size: 0.85rem;
      opacity: 0.8;
    }
    .acoes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
  `,
})
export class AindaDaParaGastar {
  private readonly finance = inject(FinanceService);

  /** Mes no formato AAAA-MM. */
  readonly mes = input.required<string>();
  readonly visao = input<Visao>('tudo');
  /** Limite do mes (0 quando nao ha). */
  readonly limite = input(0);
  /** Gasto do mes, ja somado pelo Painel. */
  readonly gasto = input(0);

  private readonly receitas = signal(0);
  private readonly contas = signal<ContaFixa[]>([]);
  protected readonly carregou = signal(false);
  /** As contas nao vieram: sem elas o numero sairia inflado (BUG-002). */
  protected readonly falhou = signal(false);
  private readonly tentativa = signal(0);

  protected readonly nomeDoMes = computed(() => mesPorExtenso(this.mes()).toLowerCase());
  protected readonly passado = computed(() => this.mes() < mesKey(new Date()));
  protected readonly conta = computed(() =>
    calcularContaDoMes(this.mes(), this.limite(), this.receitas(), this.gasto(), this.contas()),
  );

  constructor() {
    effect((onCleanup) => {
      this.tentativa();
      this.carregou.set(false);
      this.falhou.set(false);
      const pedido = forkJoin({
        resumo: this.finance.resumoDoMes(this.mes(), this.visao()),
        contas: this.finance.contasFixas(this.mes()),
      }).subscribe({
        next: ({ resumo, contas }) => {
          this.receitas.set(resumo?.receitas ?? 0);
          this.contas.set(contas ?? []);
          this.carregou.set(true);
        },
        // Nunca "R$ 0,00 de contas a pagar" por erro: mostra o erro e o "Tentar de novo".
        error: () => {
          this.falhou.set(true);
          this.carregou.set(true);
        },
      });
      onCleanup(() => pedido.unsubscribe());
    });
  }

  protected tentarDeNovo(): void {
    this.tentativa.update((n) => n + 1);
  }
}
