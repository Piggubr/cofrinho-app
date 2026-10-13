import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { ContaOuCartao, ItemDeGasto, NovoLancamento, ReciboLido } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { FilaDeGastos, semConexao } from '../../core/offline/fila-de-gastos';
import { VERSAO_DO_AVISO, pedeConsentimento } from '../../core/privacidade/aviso';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { hojeIso } from '../../core/ui/datas';
import { toqueNaTela } from '../../core/ui/focar';
import { Icone } from '../../core/ui/icone';
import { Lancamentos } from '../../core/ui/lancamentos';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';

type Tipo = 'gasto' | 'receita';

interface ItemEmConferencia {
  item: string;
  categoria: string;
  valor: number;
}

/**
 * Lancar gasto ou receita sem sair da tela: o "+" abre este painel no canto (no
 * celular, como uma folha acima da barra de abas).
 *
 * <p>Tem o essencial de cada um: valor, descricao, categoria, data e conta; no gasto,
 * tambem a foto do recibo, que traz os itens para conferir antes de gravar. Parcelas,
 * outra moeda e divisao continuam na tela de Gastos, com o link "Mais opções".</p>
 */
@Component({
  selector: 'app-lancamento-rapido',
  imports: [FormsModule, Icone, MoedaPipe],
  templateUrl: './lancamento-rapido.html',
  styleUrl: './lancamento-rapido.scss',
  host: {
    role: 'dialog',
    'aria-modal': 'false',
    'aria-labelledby': 'titulo-lancamento-rapido',
    '(keydown.escape)': 'fechar.emit()',
  },
})
export class LancamentoRapido {
  private readonly finance = inject(FinanceService);
  private readonly fila = inject(FilaDeGastos);
  private readonly lancamentos = inject(Lancamentos);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly injetor = inject(Injector);
  protected readonly auth = inject(AuthService);
  protected readonly moeda = inject(MoedaService);

  /** Foto escolhida no balao do "+": o painel ja abre lendo o recibo. */
  readonly foto = input<File | null>(null);

  /** Aba com que o painel abre: "Lançar receita" no balao ja chega em Receita. */
  readonly tipoInicial = input<Tipo>('gasto');

  readonly fechar = output<void>();
  /** Lancamento gravado, com a mensagem para o aviso; quem abriu o painel o fecha. */
  readonly lancado = output<string>();
  /** "Mais opções": leva para a tela de Gastos (parcelas, moeda, divisao). */
  readonly abrirGastos = output<void>();

  protected readonly tipo = linkedSignal<Tipo>(() => this.tipoInicial());
  protected readonly valor = signal<number | null>(null);
  protected readonly descricao = signal('');
  protected readonly categoria = signal('');
  protected readonly data = signal(hojeIso());
  protected readonly conta = signal('');

  protected readonly categoriasDeGasto = signal<string[]>([]);
  protected readonly categoriasDeReceita = signal<string[]>([]);
  protected readonly contas = signal<ContaOuCartao[]>([]);
  protected readonly categorias = computed(() =>
    this.tipo() === 'gasto' ? this.categoriasDeGasto() : this.categoriasDeReceita(),
  );

  protected readonly salvando = signal(false);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');

  protected readonly lendoRecibo = signal(false);
  /** Foto esperando a pessoa autorizar o envio para a IA. */
  protected readonly pedidoDeIa = signal<{
    base64: string;
    mimeType: string;
    mensagem: string;
  } | null>(null);
  protected readonly recibo = signal<ReciboLido | null>(null);
  protected readonly itens = signal<ItemEmConferencia[]>([]);
  protected readonly totalDoRecibo = computed(() =>
    this.itens().reduce((soma, item) => soma + Number(item.valor || 0), 0),
  );

  constructor() {
    this.finance.listarCategorias().subscribe({
      next: (resposta) => this.categoriasDeGasto.set(resposta?.categorias ?? []),
    });
    this.finance.categoriasDeReceita().subscribe({
      next: (lista) => this.categoriasDeReceita.set(lista ?? []),
    });
    this.finance.contas().subscribe({ next: (lista) => this.contas.set(lista ?? []) });
    afterNextRender(() => {
      const foto = this.foto();
      if (foto) {
        void this.lerArquivo(foto);
        return;
      }
      // No celular o teclado so sobe quando a pessoa toca no valor.
      if (!toqueNaTela()) {
        this.focarValor();
      }
    });
  }

  protected trocarTipo(tipo: Tipo): void {
    this.tipo.set(tipo);
    this.categoria.set('');
    this.erro.set('');
    this.aviso.set('');
    this.cancelarRecibo();
    if (!toqueNaTela()) {
      afterNextRender(() => this.focarValor(), { injector: this.injetor });
    }
  }

  protected salvar(): void {
    const descricao = this.descricao().trim();
    const valor = this.valor();
    if (valor === null || !(valor > 0)) {
      this.erro.set($localize`Digite um valor válido.`);
      return;
    }
    if (!descricao) {
      this.erro.set(
        this.tipo() === 'gasto'
          ? $localize`Digite o que foi comprado.`
          : $localize`Digite a descrição da receita.`,
      );
      return;
    }
    this.erro.set('');
    this.salvando.set(true);
    if (this.tipo() === 'receita') {
      this.finance
        .lancarReceita({
          data: this.data(),
          descricao,
          categoria: this.categoria() || this.categoriasDeReceita()[0] || 'Outros',
          valor,
        })
        .subscribe({
          next: () => this.concluido($localize`Receita lançada.`),
          error: (falha) => this.falhou(falha),
        });
      return;
    }
    const pedido: NovoLancamento = {
      data: this.data(),
      origem: 'Manual',
      itens: [{ item: descricao, categoria: this.categoria() || null, valor, tipo: 'Variavel' }],
      contaId: this.conta() || null,
    };
    this.finance.lancarGastos(pedido).subscribe({
      next: () => this.concluido($localize`Gasto lançado.`),
      error: (falha) => {
        if (semConexao(falha)) {
          this.fila.guardar(pedido);
          this.concluido(
            $localize`Sem internet: o gasto ficou guardado e vai quando a conexão voltar.`,
          );
          return;
        }
        this.falhou(falha);
      },
    });
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
      const { base64, mimeType } = await lerImagemComoBase64(arquivo);
      this.lerRecibo(base64, mimeType);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Não consegui ler essa imagem.`));
      this.lendoRecibo.set(false);
    }
  }

  protected autorizarIa(): void {
    const pedido = this.pedidoDeIa();
    if (!pedido) {
      return;
    }
    this.pedidoDeIa.set(null);
    this.lendoRecibo.set(true);
    this.lerRecibo(pedido.base64, pedido.mimeType, VERSAO_DO_AVISO);
  }

  protected atualizarItem(indice: number, campo: keyof ItemEmConferencia, valor: string): void {
    this.itens.update((itens) =>
      itens.map((item, i) =>
        i === indice ? { ...item, [campo]: campo === 'valor' ? Number(valor) : valor } : item,
      ),
    );
  }

  protected removerItem(indice: number): void {
    this.itens.update((itens) => itens.filter((_, i) => i !== indice));
  }

  protected cancelarRecibo(): void {
    this.recibo.set(null);
    this.itens.set([]);
    this.pedidoDeIa.set(null);
  }

  protected confirmarRecibo(): void {
    const lido = this.recibo();
    const itens = this.itens().filter((item) => item.item.trim() && item.valor >= 0);
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
        contaId: this.conta() || null,
      })
      .subscribe({
        next: () => this.concluido($localize`Recibo lançado.`),
        error: (falha) => this.falhou(falha),
      });
  }

  private lerRecibo(base64: string, mimeType: string, versaoDoAviso?: string): void {
    this.finance.lerRecibo(base64, mimeType, versaoDoAviso).subscribe({
      next: (lido) => {
        this.recibo.set(lido);
        this.itens.set(lido.itens.map((item) => ({ ...item })));
        this.lendoRecibo.set(false);
        if (lido.aviso) this.aviso.set(lido.aviso);
      },
      error: (falha) => {
        this.lendoRecibo.set(false);
        if (pedeConsentimento(falha)) {
          this.pedidoDeIa.set({ base64, mimeType, mensagem: mensagemDeErro(falha) });
          return;
        }
        this.erro.set(mensagemDeErro(falha));
      },
    });
  }

  /** Gravou: avisa as telas abertas e entrega a mensagem para o painel fechar. */
  private concluido(mensagem: string): void {
    this.lancamentos.avisar();
    this.salvando.set(false);
    this.valor.set(null);
    this.descricao.set('');
    this.cancelarRecibo();
    this.lancado.emit(mensagem);
  }

  private falhou(falha: unknown): void {
    this.erro.set(mensagemDeErro(falha));
    this.salvando.set(false);
  }

  private focarValor(): void {
    (this.elemento.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement>('#valor-rapido')
      ?.focus();
  }
}
