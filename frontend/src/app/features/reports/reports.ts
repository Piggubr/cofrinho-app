import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { RelatorioDoAno, ResumoDoMes } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { Icone } from '../../core/ui/icone';

const MESES_CURTOS = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

/** Area do grafico anual, em unidades do viewBox. */
const LARGURA = 600;
const ALTURA = 200;
const MARGEM_ESQUERDA = 8;
const BASE = ALTURA - 20;
const LARGURA_DA_BARRA = 14;

/** Barra com as pontas de cima arredondadas e o pe reto na linha de base. */
function barra(x: number, altura: number): string {
  if (altura <= 0) {
    return '';
  }
  const r = Math.min(4, altura, LARGURA_DA_BARRA / 2);
  const topo = BASE - altura;
  const direita = x + LARGURA_DA_BARRA;
  return `M${x},${BASE}V${topo + r}Q${x},${topo} ${x + r},${topo}H${direita - r}`
    + `Q${direita},${topo} ${direita},${topo + r}V${BASE}Z`;
}

/**
 * Relatorios: o mes por categoria contra o anterior, com projecao (gratis),
 * e o ano mes a mes, receitas contra gastos (Premium).
 */
@Component({
  selector: 'app-reports',
  imports: [Icone, MoedaPipe, RouterLink, DecimalPipe],
  templateUrl: './reports.html',
  styleUrl: './reports.css',
})
export class Reports {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);

  protected readonly mesAtual = signal(mesKey(new Date()));
  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly ano = computed(() => Number(this.mesAtual().slice(0, 4)));
  protected readonly resumo = signal<ResumoDoMes | null>(null);
  protected readonly doAno = signal<RelatorioDoAno | null>(null);
  protected readonly erro = signal('');
  protected readonly mesEmFoco = signal<number | null>(null);

  /** Maior valor da categoria no mes, contando o anterior, para a escala das barras. */
  private readonly maiorDaCategoria = computed(() =>
    Math.max(1, ...(this.resumo()?.porCategoria ?? []).flatMap((c) => [c.total, c.anterior])),
  );

  protected readonly colunas = computed(() => {
    const relatorio = this.doAno();
    if (!relatorio) {
      return [];
    }
    const maior = Math.max(1, ...relatorio.meses.flatMap((m) => [m.receitas, m.gastos]));
    const passo = (LARGURA - MARGEM_ESQUERDA) / 12;
    return relatorio.meses.map((m, i) => {
      const x = MARGEM_ESQUERDA + i * passo;
      const meio = x + passo / 2;
      return {
        ...m,
        rotulo: MESES_CURTOS[i],
        x,
        passo,
        meio,
        // A dica fica ao lado da coluna, sem cobrir as barras: a direita no 1o semestre, a esquerda no 2o.
        posicao: ((i < 6 ? x + passo : x) / LARGURA) * 100,
        aEsquerda: i >= 6,
        receita: barra(meio - LARGURA_DA_BARRA - 1, (m.receitas / maior) * (BASE - 10)),
        gasto: barra(meio + 1, (m.gastos / maior) * (BASE - 10)),
      };
    });
  });

  protected readonly emFoco = computed(() => {
    const indice = this.mesEmFoco();
    return indice === null ? null : (this.colunas()[indice] ?? null);
  });

  protected readonly largura = LARGURA;
  protected readonly altura = ALTURA;
  protected readonly base = BASE;

  constructor() {
    this.carregar();
  }

  protected percentual(valor: number): number {
    return Math.round((valor / this.maiorDaCategoria()) * 100);
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    const anoAntes = this.ano();
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar(this.ano() !== anoAntes);
  }

  private carregar(comAno = true): void {
    this.erro.set('');
    this.finance.resumoDoMes(this.mesAtual()).subscribe({
      next: (resumo) => this.resumo.set(resumo),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    if (comAno && this.auth.ehPremium()) {
      this.mesEmFoco.set(null);
      this.finance.relatorioDoAno(this.ano()).subscribe({
        next: (relatorio) => this.doAno.set(relatorio),
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
    }
  }
}
