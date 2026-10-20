import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Gasto, Receita } from '../../core/api/models';
import { hojeIso } from '../../core/ui/datas';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';

/** Altura do grafico em pixels; a largura acompanha o cartao (viewBox 1:1 com a tela). */
const ALTURA = 190;
const TOPO = 18;
const BASE = ALTURA - 22;
const MARGEM = 10;
/** Dias com rotulo no eixo: o comeco de cada semana e o ultimo dia. */
const DIAS_NO_EIXO = [1, 8, 15, 22];

/** Um dia do mes: o que entrou e saiu nele e o acumulado ate ele. */
export interface DiaDaSerie {
  readonly dia: number;
  readonly iso: string;
  readonly receitas: number;
  readonly gastos: number;
  readonly receitasAteAqui: number;
  readonly gastosAteAqui: number;
}

/** Uma linha da tabela por semana do mes (1 a 7, 8 a 14...). */
export interface SemanaDoMes {
  readonly de: number;
  readonly ate: number;
  readonly receitas: number;
  readonly gastos: number;
}

/**
 * Receitas e gastos do mes, dia a dia e acumulados.
 *
 * <p>No mes corrente a serie para em hoje (o resto do mes ainda nao aconteceu); num mes
 * futuro fica vazia. Valores fora do mes sao ignorados.</p>
 */
export function serieDoMes(
  mes: string,
  gastos: Gasto[],
  receitas: Receita[],
  hoje = hojeIso(),
): DiaDaSerie[] {
  const [ano, numero] = mes.split('-').map(Number);
  const diasNoMes = new Date(ano, numero, 0).getDate();
  const mesDeHoje = hoje.slice(0, 7);
  const ultimo = mes < mesDeHoje ? diasNoMes : mes === mesDeHoje ? Number(hoje.slice(8, 10)) : 0;

  const somarPorDia = (lancamentos: { data: string; valor: number }[]) => {
    const porDia = Array<number>(diasNoMes + 1).fill(0);
    for (const l of lancamentos) {
      if (l.data?.startsWith(mes)) {
        porDia[Number(l.data.slice(8, 10))] += l.valor;
      }
    }
    return porDia;
  };
  const gastoPorDia = somarPorDia(gastos);
  const receitaPorDia = somarPorDia(receitas);

  const serie: DiaDaSerie[] = [];
  let receitasAteAqui = 0;
  let gastosAteAqui = 0;
  for (let dia = 1; dia <= ultimo; dia++) {
    receitasAteAqui += receitaPorDia[dia];
    gastosAteAqui += gastoPorDia[dia];
    serie.push({
      dia,
      iso: `${mes}-${String(dia).padStart(2, '0')}`,
      receitas: receitaPorDia[dia],
      gastos: gastoPorDia[dia],
      receitasAteAqui,
      gastosAteAqui,
    });
  }
  return serie;
}

/** Topo redondo da escala (1; 1,2; 1,5; 2... vezes uma potencia de 10) logo acima do maior valor. */
export function topoDaEscala(maior: number): number {
  if (maior <= 0) {
    return 1;
  }
  const potencia = 10 ** Math.floor(Math.log10(maior));
  const passo = [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((p) => p * potencia >= maior)!;
  return passo * potencia;
}

/**
 * Grafico do calendario: quanto entrou e quanto saiu no mes, acumulado dia a dia.
 *
 * <p>A distancia entre as duas linhas e a sobra; quando a de gastos passa a de receitas, o
 * mes esta no vermelho. Passar o dedo (ou o mouse) mostra o dia, e o dia aberto no calendario
 * fica marcado. As setas do teclado andam pelos dias e a tabela por semana e a versao em texto.</p>
 */
@Component({
  selector: 'app-grafico-do-mes',
  imports: [MoedaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grafico-do-mes.html',
  styleUrl: './grafico-do-mes.css',
})
export class GraficoDoMes {
  private readonly moeda = inject(MoedaService);

  readonly mes = input.required<string>();
  readonly gastos = input.required<Gasto[]>();
  readonly receitas = input.required<Receita[]>();
  /** Dia aberto no calendario, marcado no grafico. */
  readonly diaSelecionado = input<string | null>(null);

  private readonly area = viewChild<ElementRef<HTMLElement>>('area');
  protected readonly largura = signal(600);
  protected readonly altura = ALTURA;
  protected readonly base = BASE;
  protected readonly diaEmFoco = signal<number | null>(null);

  protected readonly serie = computed(() => serieDoMes(this.mes(), this.gastos(), this.receitas()));

  private readonly diasNoMes = computed(() => {
    const [ano, numero] = this.mes().split('-').map(Number);
    return new Date(ano, numero, 0).getDate();
  });

  protected readonly totais = computed(() => {
    const ultimo = this.serie().at(-1);
    const receitas = ultimo?.receitasAteAqui ?? 0;
    const gastos = ultimo?.gastosAteAqui ?? 0;
    return { receitas, gastos, sobra: receitas - gastos };
  });

  protected readonly vazio = computed(
    () => this.totais().receitas === 0 && this.totais().gastos === 0,
  );

  private readonly topo = computed(() =>
    topoDaEscala(Math.max(this.totais().receitas, this.totais().gastos)),
  );

  protected x(dia: number): number {
    return MARGEM + ((dia - 1) / (this.diasNoMes() - 1)) * (this.largura() - 2 * MARGEM);
  }

  protected y(valor: number): number {
    return BASE - (valor / this.topo()) * (BASE - TOPO);
  }

  private caminho(valor: (d: DiaDaSerie) => number): string {
    return this.serie()
      .map((d, i) => `${i ? 'L' : 'M'}${this.x(d.dia).toFixed(1)},${this.y(valor(d)).toFixed(1)}`)
      .join('');
  }

  protected readonly linhaDeReceitas = computed(() => this.caminho((d) => d.receitasAteAqui));
  protected readonly linhaDeGastos = computed(() => this.caminho((d) => d.gastosAteAqui));

  /** Linhas de grade na metade e no topo da escala, com o valor compacto ("R$ 2,5 mil"). */
  protected readonly grade = computed(() => {
    const formato = new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: this.moeda.codigo(),
      notation: 'compact',
      maximumFractionDigits: 1,
    });
    return [this.topo() / 2, this.topo()].map((valor) => ({
      y: this.y(valor),
      rotulo: formato.format(valor),
    }));
  });

  protected readonly eixo = computed(() =>
    [...DIAS_NO_EIXO, this.diasNoMes()].map((dia) => ({ dia, x: this.x(dia) })),
  );

  /** Dia destacado: o do dedo/mouse ou, sem ele, o aberto no calendario. */
  protected readonly destaque = computed(() => {
    const emFoco = this.diaEmFoco();
    const selecionado = this.diaSelecionado();
    const dia =
      emFoco ?? (selecionado?.startsWith(this.mes()) ? Number(selecionado.slice(8, 10)) : null);
    const ponto = dia ? this.serie()[dia - 1] : undefined;
    if (!ponto) {
      return null;
    }
    const x = this.x(ponto.dia);
    return {
      ...ponto,
      x,
      yReceitas: this.y(ponto.receitasAteAqui),
      yGastos: this.y(ponto.gastosAteAqui),
      sobra: ponto.receitasAteAqui - ponto.gastosAteAqui,
      // A dica fica ao lado do dia, sem cobrir as linhas que ja passaram.
      posicao: (x / this.largura()) * 100,
      aEsquerda: x > this.largura() / 2,
      dica: emFoco !== null,
    };
  });

  protected readonly semanas = computed<SemanaDoMes[]>(() => {
    const semanas: SemanaDoMes[] = [];
    for (let de = 1; de <= this.diasNoMes(); de += 7) {
      const ate = Math.min(de + 6, this.diasNoMes());
      const dias = this.serie().slice(de - 1, ate);
      semanas.push({
        de,
        ate,
        receitas: dias.reduce((soma, d) => soma + d.receitas, 0),
        gastos: dias.reduce((soma, d) => soma + d.gastos, 0),
      });
    }
    return semanas;
  });

  constructor() {
    // O viewBox acompanha a largura real: texto e traco ficam do mesmo tamanho no celular e no computador.
    effect((aoLimpar) => {
      const elemento = this.area()?.nativeElement;
      if (!elemento || typeof ResizeObserver === 'undefined') {
        return;
      }
      const observador = new ResizeObserver(([entrada]) => {
        const largura = Math.round(entrada.contentRect.width);
        if (largura > 0) {
          this.largura.set(largura);
        }
      });
      observador.observe(elemento);
      aoLimpar(() => observador.disconnect());
    });
  }

  /** O dia mais perto do dedo ou do mouse, entre os que ja aconteceram. */
  protected apontar(evento: PointerEvent): void {
    const total = this.serie().length;
    if (!total) {
      return;
    }
    const caixa = (evento.currentTarget as HTMLElement).getBoundingClientRect();
    const fracao = (evento.clientX - caixa.left - MARGEM) / (this.largura() - 2 * MARGEM);
    const dia = Math.round(fracao * (this.diasNoMes() - 1)) + 1;
    this.diaEmFoco.set(Math.min(total, Math.max(1, dia)));
  }

  /** O mouse saindo apaga a dica; no toque ela fica ate o proximo toque. */
  protected sair(evento: PointerEvent): void {
    if (evento.pointerType !== 'touch') {
      this.diaEmFoco.set(null);
    }
  }

  /** Setas andam pelos dias. */
  protected teclar(evento: KeyboardEvent): void {
    const total = this.serie().length;
    if (!total) {
      return;
    }
    const atual = this.diaEmFoco() ?? total;
    const passos: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      Home: -total,
      End: total,
    };
    if (evento.key in passos) {
      evento.preventDefault();
      this.diaEmFoco.set(Math.min(total, Math.max(1, atual + passos[evento.key])));
    }
  }
}
