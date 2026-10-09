import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { AuthService } from '../../core/auth/auth.service';
import { BankingService } from '../../core/api/banking.service';
import { PluggyConnectService } from '../../core/banking/pluggy-connect.service';
import { Cofrinho, ContaBancaria, Gasto } from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { AvisosDoMes } from './avisos-do-mes';
import { ResumoDoMesCard } from './resumo-do-mes';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';

interface TotalPorCategoria {
  readonly categoria: string;
  readonly total: number;
  readonly fatia: number;
}

/**
 * Painel inicial.
 *
 * <p>E a unica tela que o membro da familia alcanca, e para ele o backend devolve
 * apenas os proprios depositos, com o restante zerado. Por isso os blocos de gastos
 * e meta ficam escondidos nesse caso, em vez de mostrarem zeros sem sentido.</p>
 */
@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, RouterLink, MoedaPipe, DataBrPipe, ResumoDoMesCard, AvisosDoMes],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly moeda = inject(MoedaService);
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);
  private readonly banking = inject(BankingService);
  private readonly pluggy = inject(PluggyConnectService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly cofrinho = signal<Cofrinho | null>(null);
  protected readonly gastos = signal<Gasto[]>([]);
  protected readonly metas = signal<Record<string, number>>({});
  protected readonly mesAtual = signal(mesKey(new Date()));

  protected readonly novoDeposito = signal<number | null>(null);
  protected readonly salvandoDeposito = signal(false);

  protected readonly openFinance = signal(false);
  protected readonly contas = signal<ContaBancaria[]>([]);
  protected readonly ocupadoComBancos = signal(false);
  protected readonly erroBancos = signal('');

  /** Soma por moeda: somar real com euro daria um numero sem sentido. */
  protected readonly saldoPorMoeda = computed(() => {
    const totais = new Map<string, number>();
    for (const conta of this.contas()) {
      totais.set(conta.moeda, (totais.get(conta.moeda) ?? 0) + conta.saldo);
    }
    return [...totais.entries()].map(([moeda, total]) => ({ moeda, total }));
  });

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly totalGastoNoMes = computed(() =>
    this.gastos().reduce((soma, gasto) => soma + gasto.valor, 0),
  );
  protected readonly metaDoMes = computed(() => this.metas()[this.mesAtual()] ?? 0);

  protected readonly progressoDaMeta = computed(() => {
    const meta = this.metaDoMes();
    if (meta <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((this.totalGastoNoMes() / meta) * 100));
  });

  protected readonly estourouAMeta = computed(
    () => this.metaDoMes() > 0 && this.totalGastoNoMes() > this.metaDoMes(),
  );

  protected readonly porCategoria = computed<TotalPorCategoria[]>(() => {
    const total = this.totalGastoNoMes();
    const acumulado = new Map<string, number>();
    for (const gasto of this.gastos()) {
      acumulado.set(gasto.categoria, (acumulado.get(gasto.categoria) ?? 0) + gasto.valor);
    }
    return [...acumulado.entries()]
      .map(([categoria, valor]) => ({
        categoria,
        total: valor,
        fatia: total > 0 ? Math.round((valor / total) * 100) : 0,
      }))
      .sort((a, b) => b.total - a.total);
  });

  constructor() {
    this.carregar();
    if (!this.auth.ehMembro()) {
      this.carregarContas();
    }
  }

  /** Cada banco e um compartilhamento novo: a autorizacao e pedida toda vez. */
  protected readonly pedindoAutorizacaoBanco = signal(false);

  protected conectarBanco(): void {
    this.erroBancos.set('');
    this.pedindoAutorizacaoBanco.set(true);
  }

  protected async autorizarBanco(): Promise<void> {
    this.pedindoAutorizacaoBanco.set(false);
    this.ocupadoComBancos.set(true);
    this.erroBancos.set('');
    try {
      const contas = await this.pluggy.conectar(VERSAO_DO_AVISO);
      if (contas) {
        this.contas.set(contas);
      }
    } catch (falha) {
      // Erro do widget chega como Error com texto proprio; erro da API, como resposta HTTP.
      this.erroBancos.set(
        mensagemDeErro(falha, falha instanceof Error ? falha.message : undefined),
      );
    } finally {
      this.ocupadoComBancos.set(false);
    }
  }

  /** Um banco por conexao, para o botao de desconectar nao repetir por conta. */
  protected readonly bancos = computed(() => {
    const unicos = new Map<string, string>();
    for (const conta of this.contas()) {
      unicos.set(conta.conexaoId, conta.instituicao);
    }
    return [...unicos].map(([conexaoId, instituicao]) => ({ conexaoId, instituicao }));
  });

  protected desconectarBanco(conexaoId: string, instituicao: string): void {
    if (
      !confirm(
        `Desconectar ${instituicao}? O Piggu para de ler este banco e apaga os saldos guardados.`,
      )
    ) {
      return;
    }
    this.ocupadoComBancos.set(true);
    this.erroBancos.set('');
    this.banking.desconectar(conexaoId).subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.ocupadoComBancos.set(false);
      },
      error: (falha) => {
        this.erroBancos.set(mensagemDeErro(falha));
        this.ocupadoComBancos.set(false);
      },
    });
  }

  protected sincronizarBancos(): void {
    this.ocupadoComBancos.set(true);
    this.erroBancos.set('');
    this.banking.sincronizar().subscribe({
      next: (contas) => {
        this.contas.set(contas);
        this.ocupadoComBancos.set(false);
      },
      error: (falha) => {
        this.erroBancos.set(mensagemDeErro(falha));
        this.ocupadoComBancos.set(false);
      },
    });
  }

  // Fora do forkJoin de proposito: Open Finance e opcional, e desligado ou fora do
  // ar nao pode derrubar o painel inteiro. Qualquer falha no status esconde o card.
  private carregarContas(): void {
    this.banking.status().subscribe({
      next: ({ habilitado }) => {
        this.openFinance.set(habilitado);
        if (!habilitado) {
          return;
        }
        this.banking.listarContas().subscribe({
          next: (contas) => this.contas.set(contas),
          error: (falha) => this.erroBancos.set(mensagemDeErro(falha)),
        });
      },
      error: () => this.openFinance.set(false),
    });
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar();
  }

  protected depositar(): void {
    const valor = this.novoDeposito();
    if (!valor || valor <= 0) {
      this.erro.set($localize`Digite um valor de depósito válido.`);
      return;
    }

    this.salvandoDeposito.set(true);
    this.erro.set('');
    this.finance.depositar(valor, hojeIso()).subscribe({
      next: () => {
        this.novoDeposito.set(null);
        this.salvandoDeposito.set(false);
        this.recarregarCofrinho();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvandoDeposito.set(false);
      },
    });
  }

  protected excluirDeposito(id: string): void {
    if (!confirm($localize`Apagar este depósito?`)) {
      return;
    }
    this.finance.excluirDeposito(id).subscribe({
      next: () => this.recarregarCofrinho(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private recarregarCofrinho(): void {
    this.finance.consultarCofrinho().subscribe({
      next: (cofrinho) => this.cofrinho.set(cofrinho),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    if (this.auth.ehMembro()) {
      this.finance.consultarCofrinho().subscribe({
        next: (cofrinho) => {
          this.cofrinho.set(cofrinho);
          this.carregando.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.carregando.set(false);
        },
      });
      return;
    }

    // Tres chamadas independentes em paralelo. O backend antigo devolvia tudo de
    // uma vez em getData; agora cada recurso e um endpoint proprio e o navegador
    // dispara os tres ao mesmo tempo.
    forkJoin({
      cofrinho: this.finance.consultarCofrinho(),
      gastos: this.finance.listarGastos(this.mesAtual()),
      metas: this.finance.listarMetas(),
    }).subscribe({
      next: ({ cofrinho, gastos, metas }) => {
        this.cofrinho.set(cofrinho);
        this.gastos.set(gastos);
        this.metas.set(metas);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
