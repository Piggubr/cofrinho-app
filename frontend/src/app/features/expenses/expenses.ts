import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { forkJoin } from 'rxjs';
import { FamilyService } from '../../core/api/family.service';
import { FinanceService } from '../../core/api/finance.service';
import {
  AcertoDeDivisao,
  ConferenciaDePreco,
  ContaOuCartao,
  Gasto,
  ItemDeGasto,
  MembroDaFamilia,
  ReciboLido,
  RegraDeCategoria,
} from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { VERSAO_DO_AVISO, pedeConsentimento } from '../../core/privacidade/aviso';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { ImportarExtrato } from './importar-extrato';

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
  imports: [FormsModule, RouterLink, MoedaPipe, DataBrPipe, ImportarExtrato],
  templateUrl: './expenses.html',
  styleUrl: './expenses.scss',
})
export class Expenses {
  protected readonly moeda = inject(MoedaService);
  protected readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);
  private readonly familia = inject(FamilyService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly salvando = signal(false);

  protected readonly gastos = signal<Gasto[]>([]);
  protected readonly categorias = signal<string[]>([]);
  protected readonly mesAtual = signal(mesKey(new Date()));

  protected readonly novoItem = signal('');
  protected readonly novoValor = signal<number | null>(null);
  protected readonly novaCategoria = signal('');
  protected readonly novaData = signal(hojeIso());
  protected readonly novoTipo = signal('Variavel');

  /** Foto que so a IA consegue ler, esperando a pessoa autorizar o envio. */
  protected readonly pedidoDeIa = signal<{ base64: string; mimeType: string; mensagem: string } | null>(
    null,
  );
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
  protected readonly novasParcelas = signal<number | null>(1);

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

  constructor() {
    this.carregar();
    this.carregarRegras();
    this.finance.contas().subscribe({ next: (lista) => this.contas.set(lista) });
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
          this.aviso.set($localize`Gasto lançado. ${categoria} passou do limite do mês (${orcamento.percentual}%).`);
        } else if (orcamento) {
          this.aviso.set($localize`Gasto lançado. ${categoria} já usou ${orcamento.percentual}% do limite do mês.`);
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

  protected lancar(): void {
    const item = this.novoItem().trim();
    const valor = this.novoValor();

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
    const parcelas = this.novasParcelas() ?? 1;
    if (!Number.isInteger(parcelas) || parcelas < 1 || parcelas > 48) {
      this.erro.set($localize`Parcelas: de 1 a 48.`);
      return;
    }

    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .lancarGastos({
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
      })
      .subscribe({
        next: (salvos) => {
          this.novoItem.set('');
          this.novoValor.set(null);
          this.novasParcelas.set(1);
          this.precoAcima.set(null);
          this.dividirCom.set([]);
          this.aviso.set($localize`Gasto lançado.`);
          this.salvando.set(false);
          this.carregarGastos();
          this.avisarOrcamento(salvos[0]?.categoria, this.novaData().slice(0, 7));
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
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
      .editarGasto(id, {
        item: this.itemEditado().trim(),
        categoria: this.categoriaEditada(),
        valor,
      })
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

  protected alternarDivisao(email: string): void {
    this.dividirCom.update((lista) =>
      lista.includes(email) ? lista.filter((e) => e !== email) : [...lista, email],
    );
  }

  protected nomeDe(email: string): string {
    return this.membros().find((m) => m.email.toLowerCase() === email)?.nome ?? email;
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
    this.finance.excluirGasto(gasto.id).subscribe({
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
    this.finance.acertoDoMes(this.mesAtual()).subscribe({ next: (lista) => this.acerto.set(lista) });
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
