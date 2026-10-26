import { Component, ElementRef, afterNextRender, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FamilyService } from '../../core/api/family.service';
import { FinanceService } from '../../core/api/finance.service';
import {
  ConferenciaDePreco,
  ContaOuCartao,
  ItemDeGasto,
  MembroDaFamilia,
  NovoLancamento,
  ReciboLido,
} from '../../core/api/models';
import { FilaDeGastos, semConexao } from '../../core/offline/fila-de-gastos';
import { VERSAO_DO_AVISO, pedeConsentimento } from '../../core/privacidade/aviso';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { dataCurta, hojeIso, mesPorExtenso } from '../../core/ui/datas';
import { faturaDaCompra } from '../../core/ui/fatura';
import { CampoDeCategoria } from '../../core/ui/campo-de-categoria';
import { CampoDeValor } from '../../core/ui/campo-de-valor';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';

/** Item de recibo em conferencia, antes de virar gasto. */
interface ItemEmConferencia {
  item: string;
  categoria: string;
  valor: number;
}

/** Gasto gravado (ou guardado sem conexao), com a mensagem para quem abriu o formulario. */
export interface GastoLancado {
  mensagem: string;
  /** Ficou na fila deste aparelho: nada mudou no servidor ainda. */
  offline: boolean;
}

/**
 * Formulario completo de gasto: o mesmo na tela de Gastos e no painel do "+".
 *
 * <p>A leitura por foto nao grava nada sozinha. O backend devolve os itens que a IA
 * encontrou e eles ficam em conferencia ate a pessoa confirmar, porque a IA erra e um
 * gasto errado contamina a media de precos.</p>
 */
@Component({
  selector: 'app-formulario-de-gasto',
  imports: [CampoDeCategoria, CampoDeValor, FormsModule, RouterLink, Icone, MoedaPipe, DataBrPipe],
  templateUrl: './formulario-de-gasto.html',
  styleUrl: './formulario-de-gasto.scss',
})
export class FormularioDeGasto {
  protected readonly moeda = inject(MoedaService);
  protected readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);
  private readonly fila = inject(FilaDeGastos);
  private readonly familia = inject(FamilyService);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  /** Prefixo dos ids: a tela de Gastos e o painel do "+" podem estar abertos juntos. */
  readonly prefixo = input('');
  /** Foto escolhida no balao do "+": o formulario ja abre lendo o recibo. */
  readonly foto = input<File | null>(null);
  readonly lancado = output<GastoLancado>();

  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  protected readonly categorias = signal<string[]>([]);

  protected readonly novoItem = signal('');
  protected readonly novoValor = signal<number | null>(null);
  protected readonly novaCategoria = signal('');
  protected readonly novaData = signal(hojeIso());

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

  protected readonly contas = signal<ContaOuCartao[]>([]);
  protected readonly novaConta = signal('');
  /** Lancamento rapido: data, moeda e divisao ficam recolhidos. */
  protected readonly maisOpcoes = signal(false);
  protected readonly novasParcelas = signal<number | null>(1);
  /** No cartao a compra entra na fatura; no debito/Pix so desconta da conta, sempre a vista. */
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
  protected readonly precoAcima = signal<ConferenciaDePreco | null>(null);

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
    this.finance.listarCategorias().subscribe({
      next: (resposta) => this.categorias.set(resposta?.categorias ?? []),
    });
    this.finance.contas().subscribe({
      next: (lista) => {
        this.contas.set(lista ?? []);
        const padrao = this.contas().find((conta) => conta.id === lerContaPadrao());
        this.trocarForma(padrao?.tipo === 'CARTAO' ? 'CARTAO' : 'DEBITO', padrao?.id);
      },
    });
    this.familia.ver().subscribe({ next: (familia) => this.membros.set(familia?.membros ?? []) });
    this.moedaDaCompra.set(this.moeda.codigo());
    if (!this.auth.ehPremium()) {
      this.finance.usoDeLeituras().subscribe({
        next: (uso) => this.leiturasRestantes.set(uso.restantes),
        // O contador so informa: sem ele o botao continua funcionando.
        error: () => this.leiturasRestantes.set(null),
      });
    }
    afterNextRender(() => {
      const foto = this.foto();
      if (foto) void this.lerArquivo(foto);
    });
  }

  /** Atalho "Lançar gasto" (Siri, icone, piggu://): valor ja dito e cursor no campo. */
  preencherValor(valor: number | null): void {
    if (valor !== null && valor > 0) {
      this.novoValor.set(valor);
    }
    this.focarValor();
  }

  focarValor(): void {
    (this.elemento.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement>(`#${this.prefixo()}valor`)
      ?.focus();
  }

  protected rotuloDe(mes: string): string {
    return mesPorExtenso(mes);
  }

  /**
   * Troca cartao por debito/Pix; com uma conta so daquele tipo, ela ja vem escolhida.
   * Debito/Pix e sempre a vista: parcelado e recorrente sao so do cartao.
   */
  protected trocarForma(forma: 'CARTAO' | 'DEBITO', preferida?: string): void {
    const opcoes = forma === 'CARTAO' ? this.cartoes() : this.bancos();
    const escolhida = opcoes.find((c) => c.id === (preferida ?? this.novaConta()));
    this.forma.set(forma);
    this.novaConta.set(escolhida?.id ?? (opcoes.length === 1 ? opcoes[0].id : ''));
    if (forma === 'DEBITO') {
      this.trocarModo('VISTA');
    }
    this.faltou.set(null);
  }

  protected escolherConta(id: string): void {
    this.novaConta.set(id);
    this.faltou.set(null);
  }

  /** Parcelado comeca em 2x. */
  protected trocarModo(modo: 'VISTA' | 'PARCELADO' | 'RECORRENTE'): void {
    this.modo.set(modo);
    this.novasParcelas.set(modo === 'PARCELADO' ? 2 : 1);
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
    const mes = this.novaData().slice(0, 7);
    const pedido: NovoLancamento = {
      data: this.novaData(),
      origem: 'Manual',
      itens: [
        {
          item,
          categoria: this.novaCategoria() || null,
          valor: outraMoeda ? convertido! : valor,
          // Sem o campo Tipo na tela: o recorrente vira conta fixa, o resto e variavel.
          tipo: this.modo() === 'RECORRENTE' ? 'Fixo' : 'Variavel',
          moedaOriginal: outraMoeda ? this.moedaDaCompra() : null,
          valorOriginal: outraMoeda ? valor : null,
        },
      ],
      contaId: this.novaConta() || null,
      parcelas: parcelas > 1 ? parcelas : null,
      dividirCom: this.dividirCom().length ? this.dividirCom() : null,
      recorrente: this.modo() === 'RECORRENTE' || null,
      compartilhado: this.compartilhadoEfetivo(),
      // Sem resposta (status 0) o servidor pode ter gravado: a fila reenvia com o mesmo id.
      idDoEnvio: crypto.randomUUID(),
    };
    this.finance.lancarGastos(pedido).subscribe({
      next: (salvos) => {
        this.limparLancamento();
        this.avisarOrcamento(salvos[0]?.categoria, mes);
      },
      error: (falha) => {
        if (semConexao(falha)) {
          // Na fila do mercado, sem sinal: guarda e manda quando a conexao voltar.
          this.fila.guardar(pedido);
          this.limparLancamento();
          this.lancado.emit({
            mensagem: $localize`Sem internet: o gasto ficou guardado neste aparelho e vai assim que a conexão voltar.`,
            offline: true,
          });
          return;
        }
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  /** Depois de lancar: se a categoria passou de 80% do orcamento do mes, a mensagem avisa. */
  private avisarOrcamento(categoria: string | undefined, mes: string): void {
    const lancado = (mensagem: string) => this.lancado.emit({ mensagem, offline: false });
    if (!categoria) {
      lancado($localize`Gasto lançado.`);
      return;
    }
    this.finance.orcamentos(mes).subscribe({
      next: (orcamentos) => {
        const orcamento = orcamentos.find((o) => o.categoria === categoria && o.alerta !== 'OK');
        if (orcamento?.alerta === 'ESTOUROU') {
          lancado($localize`Gasto lançado. ${categoria} passou do limite do mês (${orcamento.percentual}%).`);
        } else if (orcamento) {
          lancado($localize`Gasto lançado. ${categoria} já usou ${orcamento.percentual}% do limite do mês.`);
        } else {
          lancado($localize`Gasto lançado.`);
        }
      },
      // O aviso de orcamento e extra: sem ele o lancamento ja deu certo.
      error: () => lancado($localize`Gasto lançado.`),
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

  protected async escolherFoto(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    if (arquivo) {
      await this.lerArquivo(arquivo);
    }
  }

  private async lerArquivo(arquivo: File): Promise<void> {
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
    valor: string | number | null,
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
          this.salvando.set(false);
          this.lancado.emit({ mensagem: $localize`Recibo lançado.`, offline: false });
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
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
