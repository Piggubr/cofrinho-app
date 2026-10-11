import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { ContaOuCartao, LinhaDoExtrato } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';

/** Arquivo ate 2 MB: o mesmo limite do servidor. */
const TAMANHO_MAXIMO = 2_000_000;

/** Importar extrato OFX/CSV com previa (o que ja entrou vem desmarcado) e exportar CSV. */
@Component({
  selector: 'app-importar-extrato',
  imports: [FormsModule, MoedaPipe, DataBrPipe],
  template: `
    <section class="cartao" aria-labelledby="titulo-extrato">
      <div class="cartao-titulo">
        <h2 i18n id="titulo-extrato">Importar e exportar</h2>
      </div>
      <p i18n class="linha-detalhe">
        Importe o extrato do banco em OFX ou CSV (colunas data, descrição e valor). Você confere
        antes de gravar, e o que já foi importado fica de fora.
      </p>
      <div class="acoes">
        <label i18n class="botao secundario arquivo">
          Escolher extrato
          <input type="file" accept=".ofx,.csv,.txt,text/csv" (change)="escolher($event)" hidden />
        </label>
        <button i18n type="button" class="botao contorno" (click)="exportar()">
          Exportar CSV do mês
        </button>
      </div>
      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      }
      @if (resultado()) {
        <p class="aviso sucesso">{{ resultado() }}</p>
      }

      @if (linhas().length) {
        <p i18n class="linha-detalhe">
          {{ marcadas().length }} de {{ linhas().length }} marcados · {{ totalMarcado() | moeda }}
        </p>
        @if (contas().length) {
          <div class="campo">
            <label i18n for="conta-extrato">Conta do extrato</label>
            <select id="conta-extrato" [ngModel]="conta()" (ngModelChange)="conta.set($event)">
              <option i18n value="">Não informar</option>
              @for (c of contas(); track c.id) {
                <option [value]="c.id">{{ c.nome }}</option>
              }
            </select>
          </div>
        }
        @for (linha of linhas(); track linha.idExterno; let i = $index) {
          <div class="linha" [class.ja-importada]="linha.jaImportada">
            <label class="marcar">
              <input type="checkbox" [checked]="linha.marcada" (change)="alternar(i)" />
              <span>
                <span class="linha-titulo">{{ linha.descricao }}</span>
                <span class="linha-detalhe">
                  {{ linha.data | dataBr }}
                  @if (linha.jaImportada) {
                    <ng-container i18n>· já importado</ng-container>
                  }
                </span>
              </span>
            </label>
            <div class="linha-acoes">
              <select
                [attr.aria-label]="linha.descricao"
                [ngModel]="linha.categoria"
                (ngModelChange)="mudarCategoria(i, $event)"
              >
                @for (categoria of categorias(); track categoria) {
                  <option [value]="categoria">{{ categoria }}</option>
                }
              </select>
              <span class="linha-valor">{{ linha.valor | moeda }}</span>
            </div>
          </div>
        }
        <div class="acoes">
          <button
            i18n
            type="button"
            class="botao"
            (click)="importar()"
            [disabled]="ocupado() || !marcadas().length"
          >
            Importar marcados
          </button>
          <button i18n type="button" class="botao contorno" (click)="linhas.set([])">
            Cancelar
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    .marcar {
      display: flex;
      gap: 0.6rem;
      align-items: flex-start;
    }
    .marcar > span {
      display: grid;
    }
    .ja-importada {
      opacity: 0.6;
    }
  `,
})
export class ImportarExtrato {
  private readonly finance = inject(FinanceService);

  readonly categorias = input.required<string[]>();
  readonly contas = input.required<ContaOuCartao[]>();
  /** Mes da tela (AAAA-MM), usado na exportacao. */
  readonly mes = input.required<string>();
  readonly importado = output<void>();

  protected readonly linhas = signal<(LinhaDoExtrato & { marcada: boolean })[]>([]);
  protected readonly conta = signal('');
  protected readonly erro = signal('');
  protected readonly resultado = signal('');
  protected readonly ocupado = signal(false);
  protected readonly marcadas = computed(() => this.linhas().filter((l) => l.marcada));
  protected readonly totalMarcado = computed(() =>
    this.marcadas().reduce((soma, l) => soma + l.valor, 0),
  );

  protected async escolher(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    this.erro.set('');
    this.resultado.set('');
    if (!arquivo) {
      return;
    }
    if (arquivo.size > TAMANHO_MAXIMO) {
      this.erro.set($localize`Arquivo grande demais (máximo 2 MB).`);
      return;
    }
    const conteudo = await lerTexto(arquivo);
    this.finance.previaDoExtrato(conteudo).subscribe({
      next: (linhas) => {
        this.linhas.set(linhas.map((l) => ({ ...l, marcada: !l.jaImportada })));
        if (!linhas.length) {
          this.erro.set($localize`Nenhum gasto encontrado no extrato.`);
        }
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternar(i: number): void {
    this.linhas.update((lista) =>
      lista.map((l, j) => (j === i ? { ...l, marcada: !l.marcada } : l)),
    );
  }

  protected mudarCategoria(i: number, categoria: string): void {
    this.linhas.update((lista) => lista.map((l, j) => (j === i ? { ...l, categoria } : l)));
  }

  protected importar(): void {
    this.ocupado.set(true);
    this.finance
      .importarExtrato(
        this.marcadas().map(({ data, descricao, valor, idExterno, categoria }) => ({
          data,
          descricao,
          valor,
          idExterno,
          categoria,
        })),
        this.conta() || null,
      )
      .subscribe({
        next: ({ importados, pulados }) => {
          this.resultado.set(
            pulados
              ? $localize`Importados: ${importados}. Já estavam no Piggu: ${pulados}.`
              : $localize`Importados: ${importados}.`,
          );
          this.linhas.set([]);
          this.ocupado.set(false);
          this.importado.emit();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.ocupado.set(false);
        },
      });
  }

  protected exportar(): void {
    this.finance.exportarGastos(this.mes()).subscribe({
      next: (csv) => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(csv);
        link.download = `piggu-gastos-${this.mes()}.csv`;
        link.click();
        URL.revokeObjectURL(link.href);
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}

/** OFX de banco brasileiro costuma vir em Latin-1; se o UTF-8 der caractere quebrado, rele assim. */
async function lerTexto(arquivo: File): Promise<string> {
  const bytes = await arquivo.arrayBuffer();
  const utf8 = new TextDecoder('utf-8').decode(bytes);
  return utf8.includes('�') ? new TextDecoder('iso-8859-1').decode(bytes) : utf8;
}
