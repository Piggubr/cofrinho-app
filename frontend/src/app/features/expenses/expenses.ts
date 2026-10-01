import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { forkJoin } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { Gasto, ItemDeGasto, ReciboLido } from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso, mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';

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
  imports: [FormsModule, RouterLink, MoedaPipe, DataBrPipe],
  templateUrl: './expenses.html',
  styleUrl: './expenses.scss',
})
export class Expenses {
  protected readonly moeda = inject(MoedaService);
  protected readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);

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

  protected readonly lendoRecibo = signal(false);
  protected readonly recibo = signal<ReciboLido | null>(null);
  protected readonly itensEmConferencia = signal<ItemEmConferencia[]>([]);
  protected readonly previaDoRecibo = signal('');

  protected readonly emEdicao = signal<string | null>(null);
  protected readonly itemEditado = signal('');
  protected readonly categoriaEditada = signal('');
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
      this.erro.set('Digite o nome do item.');
      return;
    }
    if (valor === null || valor < 0) {
      this.erro.set('Digite um valor válido.');
      return;
    }

    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .lancarGastos({
        data: this.novaData(),
        origem: 'Manual',
        itens: [{ item, categoria: this.novaCategoria() || null, valor, tipo: this.novoTipo() }],
      })
      .subscribe({
        next: () => {
          this.novoItem.set('');
          this.novoValor.set(null);
          this.aviso.set('Gasto lançado.');
          this.salvando.set(false);
          this.carregarGastos();
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
      this.erro.set('A foto é grande demais. O limite é de 5 MB.');
      return;
    }

    this.erro.set('');
    this.lendoRecibo.set(true);
    try {
      const { base64, mimeType, dataUrl } = await lerImagemComoBase64(arquivo);
      this.previaDoRecibo.set(dataUrl);

      this.finance.lerRecibo(base64, mimeType).subscribe({
        next: (lido) => {
          this.recibo.set(lido);
          this.itensEmConferencia.set(lido.itens.map((item) => ({ ...item })));
          this.lendoRecibo.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.lendoRecibo.set(false);
          this.previaDoRecibo.set('');
        },
      });
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, 'Não consegui ler essa imagem.'));
      this.lendoRecibo.set(false);
    }
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
      this.erro.set('Adicione pelo menos um item válido.');
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
          this.aviso.set('Recibo lançado.');
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
      this.erro.set('Confira o nome e o valor do gasto.');
      return;
    }

    this.finance
      .editarGasto(id, {
        item: this.itemEditado().trim(),
        categoria: this.categoriaEditada(),
        valor,
      })
      .subscribe({
        next: () => {
          this.emEdicao.set(null);
          this.carregarGastos();
        },
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
  }

  protected excluir(gasto: Gasto): void {
    if (!confirm(`Apagar "${gasto.item}"?`)) {
      return;
    }
    this.finance.excluirGasto(gasto.id).subscribe({
      next: () => this.carregarGastos(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregarGastos(): void {
    this.finance.listarGastos(this.mesAtual()).subscribe({
      next: (gastos) => this.gastos.set(gastos),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    forkJoin({
      gastos: this.finance.listarGastos(this.mesAtual()),
      categorias: this.finance.listarCategorias(),
    }).subscribe({
      next: ({ gastos, categorias }) => {
        this.gastos.set(gastos);
        this.categorias.set(categorias.categorias);
        this.novaCategoria.set(categorias.categorias[0] ?? '');
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
