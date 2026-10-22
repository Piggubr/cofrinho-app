import {
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Lancamentos } from '../../core/ui/lancamentos';
import { Focar } from '../../core/ui/focar';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FamilyService } from '../../core/api/family.service';
import { nomeNaFamilia } from '../../core/api/pessoas-da-familia';
import { FinanceService } from '../../core/api/finance.service';
import {
  AcertoDeDivisao,
  ContaOuCartao,
  Gasto,
  MembroDaFamilia,
  RegraDeCategoria,
  TransferenciaDoAcerto,
} from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { ImportarExtrato } from './importar-extrato';
import { Icone } from '../../core/ui/icone';
import { FilaDeGastos } from '../../core/offline/fila-de-gastos';
import { AuthService } from '../../core/auth/auth.service';
import { FormularioDeGasto, GastoLancado } from './formulario-de-gasto';
import { Carregando } from '../../core/ui/carregando';
import { Vazio } from '../../core/ui/vazio';

/** Gastos: lancar (o mesmo formulario do "+") e revisar o que ja foi lancado. */
@Component({
  selector: 'app-expenses',
  imports: [
    Focar,
    Icone,
    FormsModule,
    MoedaPipe,
    DataBrPipe,
    ImportarExtrato,
    Carregando,
    Vazio,
    FormularioDeGasto,
  ],
  templateUrl: './expenses.html',
  styleUrl: './expenses.scss',
})
export class Expenses {
  protected readonly moeda = inject(MoedaService);
  private readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);
  protected readonly fila = inject(FilaDeGastos);
  private readonly familia = inject(FamilyService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');

  protected readonly gastos = signal<Gasto[]>([]);
  protected readonly categorias = signal<string[]>([]);
  /** A busca global abre os gastos no mes do resultado (?mes=AAAA-MM). */
  private readonly rota = inject(ActivatedRoute).snapshot;
  private readonly injetor = inject(Injector);
  private readonly mesPedido = this.rota.queryParamMap.get('mes') ?? '';
  protected readonly mesAtual = signal(
    /^\d{4}-\d{2}$/.test(this.mesPedido) ? this.mesPedido : mesKey(new Date()),
  );

  protected readonly emEdicao = signal<string | null>(null);
  protected readonly itemEditado = signal('');
  protected readonly categoriaEditada = signal('');
  protected readonly regras = signal<RegraDeCategoria[]>([]);
  protected readonly contas = signal<ContaOuCartao[]>([]);
  protected readonly membros = signal<MembroDaFamilia[]>([]);
  protected readonly compartilhadoEditado = signal(false);
  protected readonly transferencias = signal<TransferenciaDoAcerto[]>([]);
  protected readonly acerto = signal<AcertoDeDivisao[]>([]);
  protected readonly termoDaRegra = signal('');
  protected readonly categoriaDaRegra = signal('');
  /** Depois de corrigir a categoria de um gasto: oferece virar regra. */
  protected readonly sugestaoDeRegra = signal<{ termo: string; categoria: string } | null>(null);
  protected readonly valorEditado = signal<number | null>(null);

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly totalDoMes = computed(() =>
    this.gastos().reduce((soma, gasto) => soma + gasto.valor, 0),
  );
  private readonly formulario = viewChild.required(FormularioDeGasto);

  constructor() {
    // Lancado pelo "+" com esta tela aberta: recarrega (a primeira leitura e so a inicial).
    const lancamentos = inject(Lancamentos);
    let primeira = true;
    effect(() => {
      lancamentos.versao();
      if (primeira) {
        primeira = false;
        return;
      }
      untracked(() => this.carregarGastos());
    });
    // O que ficou guardado sem sinal vai agora; quando chega, a lista recarrega.
    void this.fila.enviar();
    effect(() => {
      const enviados = this.fila.enviados();
      if (enviados > 0) {
        this.aviso.set($localize`Enviados ${enviados} gastos guardados sem conexão.`);
        this.carregarGastos();
      }
    });
    // Atalho "Lançar gasto" (icone, Siri, link piggu://): cursor no valor, ja preenchido se veio.
    // Pela assinatura, e nao pelo snapshot: o atalho pode chegar com esta tela ja aberta.
    inject(ActivatedRoute)
      .queryParamMap.pipe(takeUntilDestroyed())
      .subscribe((parametros) => {
        if (parametros.get('rapido') !== '1') {
          return;
        }
        const valor = Number(parametros.get('valor'));
        afterNextRender(() => this.formulario().preencherValor(valor > 0 ? valor : null), {
          injector: this.injetor,
        });
      });
    // Vindo do guia de primeiro uso por "Importar extrato".
    if (this.rota.fragment === 'importar') {
      afterNextRender(() =>
        document.getElementById('titulo-extrato')?.scrollIntoView?.({ block: 'start' }),
      );
    }
    this.carregar();
    this.carregarRegras();
    this.finance.contas().subscribe({ next: (lista) => this.contas.set(lista) });
    this.familia.ver().subscribe({ next: (familia) => this.membros.set(familia.membros) });
  }

  /** Lancou pelo formulario: recarrega a lista, menos quando ficou na fila sem conexao. */
  protected aoLancar(lancado: GastoLancado): void {
    this.erro.set('');
    this.aviso.set(lancado.mensagem);
    if (!lancado.offline) {
      this.carregarGastos();
    }
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregarGastos();
  }

  protected enviarFila(): void {
    void this.fila.enviar();
  }

  protected abrirEdicao(gasto: Gasto): void {
    this.emEdicao.set(gasto.id);
    this.itemEditado.set(gasto.item);
    this.categoriaEditada.set(gasto.categoria);
    this.valorEditado.set(gasto.valor);
    this.compartilhadoEditado.set(gasto.compartilhado ?? false);
  }

  /** Do compartilhado, so quem lancou apaga ou volta para "só meu". */
  protected ehMeu(gasto: Gasto): boolean {
    return gasto.usuario === this.auth.usuario()?.id;
  }

  protected cancelarEdicao(): void {
    this.emEdicao.set(null);
  }

  protected salvarEdicao(id: string): void {
    const valor = this.valorEditado();
    if (!this.itemEditado().trim() || valor === null || valor < 0) {
      this.erro.set($localize`Confira o nome e o valor do gasto.`);
      return;
    }

    const antes = this.gastos().find((g) => g.id === id);
    this.finance
      .editarGasto(
        id,
        {
          item: this.itemEditado().trim(),
          categoria: this.categoriaEditada(),
          valor,
        },
        antes && this.ehMeu(antes) ? this.compartilhadoEditado() : undefined,
      )
      .subscribe({
        next: (salvo) => {
          if (antes && antes.categoria !== salvo.categoria) {
            this.sugestaoDeRegra.set({ termo: salvo.item, categoria: salvo.categoria });
          }
          this.emEdicao.set(null);
          this.carregarGastos();
        },
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
  }

  protected nomeDe(pessoa: string | null): string {
    return nomeNaFamilia(this.membros(), pessoa);
  }

  protected criarRegra(termo: string, categoria: string): void {
    if (!termo.trim() || !categoria) {
      this.erro.set($localize`Digite o termo e escolha a categoria.`);
      return;
    }
    this.finance.definirRegra(termo.trim(), categoria).subscribe({
      next: (regra) => {
        this.sugestaoDeRegra.set(null);
        this.termoDaRegra.set('');
        this.aviso.set($localize`Regra salva: "${regra.termo}" vai para ${regra.categoria}.`);
        this.carregarRegras();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluirRegra(regra: RegraDeCategoria): void {
    this.finance.excluirRegra(regra.id).subscribe({
      next: () => this.carregarRegras(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregarRegras(): void {
    this.finance.regrasDeCategoria().subscribe({ next: (lista) => this.regras.set(lista) });
  }

  protected excluir(gasto: Gasto): void {
    if (!confirm($localize`Apagar "${gasto.item}"?`)) {
      return;
    }
    const seguintes = gasto.parcelas && gasto.parcela ? gasto.parcelas - gasto.parcela : 0;
    const eAsProximas =
      seguintes > 0 &&
      confirm(
        $localize`Esta é a parcela ${gasto.parcela}:parcela: de ${gasto.parcelas}:parcelas:. Apagar também as ${seguintes}:seguintes: próximas? OK apaga todas; Cancelar apaga só esta.`,
      );
    this.finance.excluirGasto(gasto.id, eAsProximas).subscribe({
      next: () => this.carregarGastos(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected carregarGastos(): void {
    this.finance.listarGastos(this.mesAtual()).subscribe({
      next: (gastos) => this.gastos.set(gastos),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    this.carregarAcerto();
  }

  private carregarAcerto(): void {
    const mes = this.mesAtual();
    this.finance.acertoDoMes(mes).subscribe({
      next: (lista) => {
        this.acerto.set(lista);
        // Sem gasto dividido no mes nao ha acerto: poupa o pedido de quem paga quem.
        if (!lista.length) {
          this.transferencias.set([]);
          return;
        }
        this.finance
          .quemDeveAQuem(mes)
          .subscribe({ next: (transferencias) => this.transferencias.set(transferencias) });
      },
    });
  }

  private carregar(): void {
    forkJoin({
      gastos: this.finance.listarGastos(this.mesAtual()),
      categorias: this.finance.listarCategorias(),
    }).subscribe({
      next: ({ gastos, categorias }) => {
        this.gastos.set(gastos);
        this.carregarAcerto();
        this.categorias.set(categorias.categorias);
        this.categoriaDaRegra.set(categorias.categorias[0] ?? '');
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
