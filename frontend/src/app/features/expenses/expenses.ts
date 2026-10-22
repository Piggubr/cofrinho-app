import {
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Lancamentos } from '../../core/ui/lancamentos';
import { Focar } from '../../core/ui/focar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { forkJoin } from 'rxjs';
import { FamilyService } from '../../core/api/family.service';
import { nomeNaFamilia } from '../../core/api/pessoas-da-familia';
import { FinanceService } from '../../core/api/finance.service';
import {
  AcertoDeDivisao,
  ConferenciaDePreco,
  ContaOuCartao,
  Gasto,
  ItemDeGasto,
  MembroDaFamilia,
  NovoLancamento,
  ReciboLido,
  RegraDeCategoria,
  TransferenciaDoAcerto,
} from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { VERSAO_DO_AVISO, pedeConsentimento } from '../../core/privacidade/aviso';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { dataCurta, hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { faturaDaCompra } from '../../core/ui/fatura';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { ImportarExtrato } from './importar-extrato';
import { Icone } from '../../core/ui/icone';
import { FilaDeGastos, semConexao } from '../../core/offline/fila-de-gastos';
import { Carregando } from '../../core/ui/carregando';
import { Vazio } from '../../core/ui/vazio';

/** Item de recibo em conferencia, antes de virar gasto. */
interface ItemEmConferencia {
  item: string;
  categoria: string;
  valor: number;
}

/**
 * Gastos: lancar, conferir recibo e revisar o que ja foi lancado.
 *
 * <p>A leitura por foto nao grava nada sozinha. O backend devolve os itens que a IA
 * encontrou e eles ficam em conferencia na tela ate a pessoa confirmar, porque a
 * IA erra e um gasto errado contamina a media de precos.</p>
 */
@Component({
  selector: 'app-expenses',
  imports: [Focar, Icone, FormsModule, RouterLink, MoedaPipe, DataBrPipe, ImportarExtrato, Carregando, Vazio],
  templateUrl: './expenses.html',
  styleUrl: './expenses.scss',
})
export class Expenses {
  protected readonly moeda = inject(MoedaService);
  protected readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);
  protected readonly fila = inject(FilaDeGastos);
  private readonly familia = inject(FamilyService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly salvando = signal(false);

  protected readonly gastos = signal<Gasto[]>([]);
  protected readonly categorias = signal<string[]>([]);
  /** A busca global abre os gastos no mes do resultado (?mes=AAAA-MM). */
  private readonly rota = inject(ActivatedRoute).snapshot;
  private readonly injetor = inject(Injector);
  private readonly mesPedido = this.rota.queryParamMap.get('mes') ?? '';
  protected readonly mesAtual = signal(
    /^\d{4}-\d{2}$/.test(this.mesPedido) ? this.mesPedido : mesKey(new Date()),
  );

  protected readonly novoItem = signal('');
  protected readonly novoValor = signal<number | null>(null);
  protected readonly novaCategoria = signal('');
  protected readonly novaData = signal(hojeIso());
  protected readonly novoTipo = signal('Variavel');

  /** Foto que so a IA consegue ler, esperando a pessoa autorizar o envio. */
  protected readonly pedidoDeIa = signal<{
    base64: string;
    mimeType: string;
    mensagem: string;
  } | null>(null);
  protected readonly lendoRecibo = signal(false);
  /** Leituras gratis que sobram no mes; nulo no Premium ou antes de saber. */
  protected readonly leiturasRestantes = signal<number | null>(null);
  protected readonly recibo = signal<ReciboLido | null>(null);
  protected readonly itensEmConferencia = signal<ItemEmConferencia[]>([]);
  protected readonly previaDoRecibo = signal('');

  protected readonly emEdicao = signal<string | null>(null);
  protected readonly itemEditado = signal('');
  protected readonly categoriaEditada = signal('');
  protected readonly regras = signal<RegraDeCategoria[]>([]);
  protected readonly contas = signal<ContaOuCartao[]>([]);
  protected readonly novaConta = signal('');
  /** Lancamento rapido: data, moeda, tipo e divisao ficam recolhidos. */
  protected readonly maisOpcoes = signal(false);
  protected readonly novasParcelas = signal<number | null>(1);
  /** No cartao a compra entra na fatura; no debito/Pix so desconta da conta. */
  protected readonly forma = signal<'CARTAO' | 'DEBITO'>('DEBITO');
  protected readonly modo = signal<'VISTA' | 'PARCELADO' | 'RECORRENTE'>('VISTA');
  /** Campo que faltou no ultimo "Lancar": fica com a borda vermelha. */
  protected readonly faltou = signal<'conta' | 'parcelas' | null>(null);
  protected readonly cartoes = computed(() => this.contas().filter((c) => c.tipo === 'CARTAO'));
  protected readonly bancos = computed(() => this.contas().filter((c) => c.tipo === 'CONTA'));
  /** Fatura em que a compra cai, pela data escolhida e pelo fechamento do cartao. */
  protected readonly faturaDoLancamento = computed(() => {
    const cartao = this.cartoes().find((c) => c.id === this.novaConta());
    if (this.forma() !== 'CARTAO' || !cartao?.fechamento || !cartao.vencimento || !this.novaData()) {
      return null;
    }
    return faturaDaCompra(cartao.fechamento, cartao.vencimento, this.novaData());
  });
  protected readonly valorDaParcela = computed(() => {
    const valor = this.novoValor();
    const parcelas = this.novasParcelas() ?? 0;
    return valor && Number.isInteger(parcelas) && parcelas >= 2 ? Math.floor((valor / parcelas) * 100) / 100 : null;
  });
  protected readonly diaDaCompra = computed(() => Number(this.novaData().slice(8, 10)));

  /** Compra em outra moeda: converte pela cotacao do dia antes de gravar. */
  protected readonly moedaDaCompra = signal('');
  private readonly cotacao = signal<number | null>(null);
  protected readonly moedasDaCompra = computed(() => [
    ...new Set([this.moeda.codigo(), 'BRL', 'USD', 'EUR', 'GBP', 'ARS', 'CLP', 'UYU', 'JPY']),
  ]);
  protected readonly convertido = computed(() => {
    const taxa = this.cotacao();
    const valor = this.novoValor();
    return taxa === null || valor === null ? null : Math.round(valor * taxa * 100) / 100;
  });

  protected readonly membros = signal<MembroDaFamilia[]>([]);
  protected readonly dividirCom = signal<string[]>([]);
  /** Escolha da pessoa; nula segue o cartao (o do casal compartilha) e o padrao do perfil. */
  protected readonly compartilhado = signal<boolean | null>(null);
  protected readonly compartilhadoEfetivo = computed(
    () =>
      this.compartilhado() ??
      (this.contas().find((conta) => conta.id === this.novaConta())?.compartilhado ||
        this.auth.usuario()?.compartilhamento?.compartilharPorPadrao ||
        false),
  );
  protected readonly compartilhadoEditado = signal(false);
  protected readonly transferencias = signal<TransferenciaDoAcerto[]>([]);
  protected readonly acerto = signal<AcertoDeDivisao[]>([]);
  protected readonly precoAcima = signal<ConferenciaDePreco | null>(null);
  protected readonly termoDaRegra = signal('');
  protected readonly categoriaDaRegra = signal('');
  /** Depois de corrigir a categoria de um gasto: oferece virar regra. */
  protected readonly sugestaoDeRegra = signal<{ termo: string; categoria: string } | null>(null);
  protected readonly valorEditado = signal<number | null>(null);

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));
  protected readonly totalDoMes = computed(() =>
    this.gastos().reduce((soma, gasto) => soma + gasto.valor, 0),
  );
  protected readonly totalEmConferencia = computed(() =>
    this.itensEmConferencia().reduce((soma, item) => soma + Number(item.valor || 0), 0),
  );
  /** O que esta escolhido em "Mais opções", para conferir sem abrir. */
  protected readonly resumoDasOpcoes = computed(() => {
    const data = this.novaData() === hojeIso() ? $localize`Hoje` : dataCurta(this.novaData());
    const partes = [data];
    if (this.moedaDaCompra() && this.moedaDaCompra() !== this.moeda.codigo()) {
      partes.push(this.moedaDaCompra());
    }
    if (this.dividirCom().length) partes.push($localize`dividido`);
    if (this.membros().length > 1 && this.compartilhadoEfetivo()) partes.push($localize`compartilhado`);
    return partes.join(' · ');
  });

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
        if (valor > 0) {
          this.novoValor.set(valor);
        }
        afterNextRender(() => document.getElementById('valor')?.focus(), {
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
    this.finance.contas().subscribe({
      next: (lista) => {
        this.contas.set(lista);
        const padrao = lista.find((conta) => conta.id === lerContaPadrao());
        this.trocarForma(padrao?.tipo === 'CARTAO' ? 'CARTAO' : 'DEBITO', padrao?.id);
      },
    });
    this.familia.ver().subscribe({ next: (familia) => this.membros.set(familia.membros) });
    this.moedaDaCompra.set(this.moeda.codigo());
    if (!this.auth.ehPremium()) {
      this.finance.usoDeLeituras().subscribe({
        next: (uso) => this.leiturasRestantes.set(uso.restantes),
        // O contador so informa: sem ele o botao continua funcionando.
        error: () => this.leiturasRestantes.set(null),
      });
    }
  }

  /** Depois de lancar: se a categoria passou de 80% do orcamento do mes, avisa na hora. */
  private avisarOrcamento(categoria: string | undefined, mes: string): void {
    if (!categoria) {
      return;
    }
    this.finance.orcamentos(mes).subscribe({
      next: (orcamentos) => {
        const orcamento = orcamentos.find((o) => o.categoria === categoria && o.alerta !== 'OK');
        if (orcamento?.alerta === 'ESTOUROU') {
          this.aviso.set(
            $localize`Gasto lançado. ${categoria} passou do limite do mês (${orcamento.percentual}%).`,
          );
        } else if (orcamento) {
          this.aviso.set(
            $localize`Gasto lançado. ${categoria} já usou ${orcamento.percentual}% do limite do mês.`,
          );
        }
      },
      // O aviso de orcamento e extra: sem ele o lancamento ja deu certo.
      error: () => undefined,
    });
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregarGastos();
  }

  protected rotuloDe(mes: string): string {
    return mesPorExtenso(mes);
  }

  /** Troca cartao por debito/Pix; com uma conta so daquele tipo, ela ja vem escolhida. */
  protected trocarForma(forma: 'CARTAO' | 'DEBITO', preferida?: string): void {
    const opcoes = forma === 'CARTAO' ? this.cartoes() : this.bancos();
    const escolhida = opcoes.find((c) => c.id === (preferida ?? this.novaConta()));
    this.forma.set(forma);
    this.novaConta.set(escolhida?.id ?? (opcoes.length === 1 ? opcoes[0].id : ''));
    this.faltou.set(null);
  }

  protected escolherConta(id: string): void {
    this.novaConta.set(id);
    this.faltou.set(null);
  }

  /** Recorrente e conta fixa: o tipo vira Fixo; parcelado comeca em 2x. */
  protected trocarModo(modo: 'VISTA' | 'PARCELADO' | 'RECORRENTE'): void {
    this.modo.set(modo);
    this.novasParcelas.set(modo === 'PARCELADO' ? 2 : 1);
    this.novoTipo.set(modo === 'RECORRENTE' ? 'Fixo' : 'Variavel');
    this.faltou.set(null);
  }

  protected lancar(): void {
    const item = this.novoItem().trim();
    const valor = this.novoValor();
    this.faltou.set(null);

    if (!item) {
      this.erro.set($localize`Digite o nome do item.`);
      return;
    }
    if (valor === null || valor < 0) {
      this.erro.set($localize`Digite um valor válido.`);
      return;
    }
    const outraMoeda = this.moedaDaCompra() && this.moedaDaCompra() !== this.moeda.codigo();
    const convertido = this.convertido();
    if (outraMoeda && convertido === null) {
      this.erro.set($localize`Aguarde a cotação da moeda da compra.`);
      return;
    }
    if (this.forma() === 'CARTAO' && !this.cartoes().length) {
      this.erro.set($localize`Cadastre o cartão em Cartões e bancos, ou escolha Débito/Pix.`);
      return;
    }
    if (!this.novaConta() && (this.forma() === 'CARTAO' || this.bancos().length)) {
      this.faltou.set('conta');
      this.erro.set(
        this.forma() === 'CARTAO'
          ? $localize`Escolha em qual cartão foi a compra.`
          : $localize`Escolha de qual conta saiu o dinheiro.`,
      );
      return;
    }
    const parcelas = this.modo() === 'PARCELADO' ? (this.novasParcelas() ?? 0) : 1;
    if (!Number.isInteger(parcelas) || parcelas < 1 || parcelas > 48 || (this.modo() === 'PARCELADO' && parcelas < 2)) {
      this.faltou.set('parcelas');
      this.erro.set($localize`Parcelado: de 2 a 48 parcelas.`);
      return;
    }

    this.salvando.set(true);
    this.erro.set('');
    const pedido: NovoLancamento = {
      data: this.novaData(),
      origem: 'Manual',
      itens: [
        {
          item,
          categoria: this.novaCategoria() || null,
          valor: outraMoeda ? convertido! : valor,
          tipo: this.novoTipo(),
          moedaOriginal: outraMoeda ? this.moedaDaCompra() : null,
          valorOriginal: outraMoeda ? valor : null,
        },
      ],
      contaId: this.novaConta() || null,
      parcelas: parcelas > 1 ? parcelas : null,
      dividirCom: this.dividirCom().length ? this.dividirCom() : null,
      recorrente: this.modo() === 'RECORRENTE' || null,
      compartilhado: this.compartilhadoEfetivo(),
    };
    this.finance.lancarGastos(pedido).subscribe({
      next: (salvos) => {
        this.limparLancamento();
        this.aviso.set($localize`Gasto lançado.`);
        this.carregarGastos();
        this.avisarOrcamento(salvos[0]?.categoria, this.novaData().slice(0, 7));
      },
      error: (falha) => {
        if (semConexao(falha)) {
          // Na fila do mercado, sem sinal: guarda e manda quando a conexao voltar.
          this.fila.guardar(pedido);
          this.limparLancamento();
          this.aviso.set(
            $localize`Sem internet: o gasto ficou guardado neste aparelho e vai assim que a conexão voltar.`,
          );
          return;
        }
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  private limparLancamento(): void {
    guardarContaPadrao(this.novaConta());
    this.novoItem.set('');
    this.novoValor.set(null);
    this.trocarModo('VISTA');
    this.precoAcima.set(null);
    this.dividirCom.set([]);
    this.compartilhado.set(null);
    this.salvando.set(false);
  }

  protected enviarFila(): void {
    void this.fila.enviar();
  }

  protected async escolherFoto(evento: Event): Promise<void> {
    const arquivo = (evento.target as HTMLInputElement).files?.[0];
    if (!arquivo) {
      return;
    }
    if (!imagemCabeNoLimite(arquivo)) {
      this.erro.set($localize`A foto é grande demais. O limite é de 5 MB.`);
      return;
    }

    this.erro.set('');
    this.lendoRecibo.set(true);
    try {
      const { base64, mimeType, dataUrl } = await lerImagemComoBase64(arquivo);
      this.previaDoRecibo.set(dataUrl);

      this.lerRecibo(base64, mimeType);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Não consegui ler essa imagem.`));
      this.lendoRecibo.set(false);
    }
  }

  /** A pessoa leu o aviso e autorizou: a mesma foto vai de novo, agora podendo ir a IA. */
  protected autorizarIa(): void {
    const pedido = this.pedidoDeIa();
    if (!pedido) {
      return;
    }
    this.pedidoDeIa.set(null);
    this.lendoRecibo.set(true);
    this.lerRecibo(pedido.base64, pedido.mimeType, VERSAO_DO_AVISO);
  }

  protected recusarIa(): void {
    this.pedidoDeIa.set(null);
    this.previaDoRecibo.set('');
  }

  private lerRecibo(base64: string, mimeType: string, versaoDoAviso?: string): void {
    this.finance.lerRecibo(base64, mimeType, versaoDoAviso).subscribe({
      next: (lido) => {
        this.leiturasRestantes.set(lido.leiturasRestantes);
        this.recibo.set(lido);
        this.itensEmConferencia.set(lido.itens.map((item) => ({ ...item })));
        this.lendoRecibo.set(false);
      },
      error: (falha) => {
        this.lendoRecibo.set(false);
        if (pedeConsentimento(falha)) {
          this.pedidoDeIa.set({ base64, mimeType, mensagem: mensagemDeErro(falha) });
          return;
        }
        this.erro.set(mensagemDeErro(falha));
        this.previaDoRecibo.set('');
      },
    });
  }

  protected atualizarItemDoRecibo(
    indice: number,
    campo: keyof ItemEmConferencia,
    valor: string,
  ): void {
    this.itensEmConferencia.update((itens) =>
      itens.map((item, i) =>
        i === indice ? { ...item, [campo]: campo === 'valor' ? Number(valor) : valor } : item,
      ),
    );
  }

  protected removerItemDoRecibo(indice: number): void {
    this.itensEmConferencia.update((itens) => itens.filter((_, i) => i !== indice));
  }

  protected cancelarRecibo(): void {
    this.recibo.set(null);
    this.itensEmConferencia.set([]);
    this.previaDoRecibo.set('');
  }

  protected confirmarRecibo(): void {
    const lido = this.recibo();
    const itens = this.itensEmConferencia().filter((item) => item.item.trim() && item.valor >= 0);

    if (!lido || !itens.length) {
      this.erro.set($localize`Adicione pelo menos um item válido.`);
      return;
    }

    this.salvando.set(true);
    this.finance
      .lancarGastos({
        data: lido.data,
        estabelecimento: lido.estabelecimento,
        reciboId: lido.reciboId,
        origem: 'Foto',
        itens: itens as ItemDeGasto[],
        contaId: this.novaConta() || null,
        compartilhado: this.compartilhadoEfetivo(),
      })
      .subscribe({
        next: () => {
          this.cancelarRecibo();
          this.aviso.set($localize`Recibo lançado.`);
          this.salvando.set(false);
          this.carregarGastos();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
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

  protected trocarMoeda(codigo: string): void {
    this.moedaDaCompra.set(codigo);
    this.cotacao.set(null);
    if (codigo === this.moeda.codigo()) {
      return;
    }
    this.finance.consultarCotacao(codigo, this.moeda.codigo()).subscribe({
      next: (cotacao) => this.cotacao.set(cotacao.taxa),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternarDivisao(pessoa: string): void {
    this.dividirCom.update((lista) =>
      lista.includes(pessoa) ? lista.filter((p) => p !== pessoa) : [...lista, pessoa],
    );
  }

  protected nomeDe(pessoa: string | null): string {
    return nomeNaFamilia(this.membros(), pessoa);
  }

  /** Ao sair do valor: avisa se o preco passou da media do item (so na moeda da pessoa). */
  protected conferirPreco(): void {
    const item = this.novoItem().trim();
    const valor = this.novoValor();
    const outraMoeda = this.moedaDaCompra() && this.moedaDaCompra() !== this.moeda.codigo();
    if (!item || valor === null || valor <= 0 || outraMoeda) {
      this.precoAcima.set(null);
      return;
    }
    this.finance.conferirPreco(item, valor).subscribe({
      next: (conferencia) => this.precoAcima.set(conferencia?.acima ? conferencia : null),
      // O aviso de preco e extra: sem ele o lancamento segue.
      error: () => this.precoAcima.set(null),
    });
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

const CONTA_PADRAO = 'piggu_conta_padrao';

/** A ultima conta usada vira a padrao do proximo lancamento, neste aparelho. */
function lerContaPadrao(): string {
  try {
    return localStorage.getItem(CONTA_PADRAO) ?? '';
  } catch {
    return '';
  }
}

function guardarContaPadrao(conta: string): void {
  try {
    if (conta) localStorage.setItem(CONTA_PADRAO, conta);
    else localStorage.removeItem(CONTA_PADRAO);
  } catch {
    // Sem armazenamento (aba privada): segue sem lembrar.
  }
}
