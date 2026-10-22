import {
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../api/finance.service';
import { Icone } from './icone';
import { mensagemDeErro } from './mensagem-de-erro';

/** Valor da ultima opcao da lista, que abre o campo de criar. */
const NOVA = '__nova__';

/**
 * Categoria do gasto com "+ Nova categoria" na propria lista.
 *
 * <p>Antes nao havia onde criar categoria: o servidor sabia, mas nenhuma tela chamava, e
 * quem lancava um gasto de pet ficava preso nas oito de fabrica. Escolher a ultima opcao
 * troca a lista por um campo de nome; ao criar, a categoria ja fica escolhida.</p>
 */
@Component({
  selector: 'app-campo-de-categoria',
  imports: [FormsModule, Icone],
  host: { '[class.criando]': 'criando()' },
  template: `
    <label i18n [for]="campoId()">Categoria</label>
    @if (criando()) {
      <div class="nova-categoria">
        <input
          #campoNovo
          i18n-placeholder
          i18n-aria-label
          aria-label="Nome da nova categoria"
          type="text"
          maxlength="50"
          autocomplete="off"
          enterkeyhint="done"
          placeholder="Ex.: Pet"
          [id]="campoId()"
          [ngModel]="nome()"
          [ngModelOptions]="{ standalone: true }"
          (ngModelChange)="nome.set($event); erro.set('')"
          (keydown.enter)="$event.preventDefault(); criar()"
          (keydown.escape)="cancelar()"
          (blur)="aoSair()"
        />
        <button
          type="button"
          class="botao pequeno"
          i18n-aria-label
          aria-label="Criar categoria"
          [disabled]="salvando()"
          (mousedown)="$event.preventDefault()"
          (click)="criar()"
        >
          <app-icone nome="check" [tamanho]="16" /> <span i18n>Criar</span>
        </button>
        <button
          type="button"
          i18n
          class="botao fantasma pequeno"
          (mousedown)="$event.preventDefault()"
          (click)="cancelar()"
        >
          Cancelar
        </button>
      </div>
      @if (erro()) {
        <p class="linha-detalhe erro-da-categoria" role="alert">{{ erro() }}</p>
      }
    } @else {
      <select
        [id]="campoId()"
        [ngModel]="valor()"
        [ngModelOptions]="{ standalone: true }"
        (ngModelChange)="escolher($event)"
      >
        @if (automatica()) {
          <option i18n value="">Automática</option>
        }
        @for (categoria of categorias(); track categoria) {
          <option [value]="categoria">{{ categoria }}</option>
        }
        <option i18n [value]="nova">+ Nova categoria…</option>
      </select>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    /* Criando, ocupa a linha toda da grade: no celular a coluna e estreita demais para o nome e os botoes. */
    :host(.criando) {
      grid-column: 1 / -1;
    }
    .nova-categoria {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .nova-categoria input {
      flex: 1;
      min-width: 0;
    }
    .erro-da-categoria {
      color: var(--danger-ink);
      margin-top: 0.3rem;
    }
  `,
})
export class CampoDeCategoria {
  private readonly finance = inject(FinanceService);
  private readonly injetor = inject(Injector);

  readonly campoId = input.required<string>();
  readonly categorias = input.required<string[]>();
  /** Gasto aceita "Automática" (o Piggu escolhe pelas regras); outras telas, nao. */
  readonly automatica = input(true);
  readonly valor = model('');
  /** Lista nova depois de criar, para a tela atualizar as outras listas. */
  readonly criou = output<string[]>();

  protected readonly nova = NOVA;
  protected readonly criando = signal(false);
  protected readonly nome = signal('');
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  private readonly campoNovo = viewChild<ElementRef<HTMLInputElement>>('campoNovo');

  protected escolher(escolhida: string): void {
    if (escolhida !== NOVA) {
      this.valor.set(escolhida);
      return;
    }
    this.nome.set('');
    this.erro.set('');
    this.criando.set(true);
    afterNextRender(() => this.campoNovo()?.nativeElement.focus(), { injector: this.injetor });
  }

  protected cancelar(): void {
    this.criando.set(false);
  }

  /**
   * Saiu do campo com um nome escrito (tocou em "Lançar" sem confirmar): cria mesmo
   * assim, para o nome digitado nao se perder. Os botoes ao lado nao tiram o foco.
   */
  protected aoSair(): void {
    if (this.criando() && this.nome().trim()) {
      this.criar();
    }
  }

  protected criar(): void {
    if (this.salvando()) {
      return;
    }
    const nome = this.nome().trim().replace(/\s+/g, ' ');
    if (nome.length < 2) {
      this.erro.set($localize`Digite o nome da categoria.`);
      return;
    }
    // Ja existe (talvez com outra caixa): so escolhe, sem erro.
    const existente = this.categorias().find((c) => c.toLowerCase() === nome.toLowerCase());
    if (existente) {
      this.valor.set(existente);
      this.criando.set(false);
      return;
    }
    // Ja vale para um "Lançar" tocado enquanto o servidor responde.
    const anterior = this.valor();
    this.valor.set(nome);
    this.salvando.set(true);
    this.finance.criarCategoria(nome).subscribe({
      next: ({ categorias }) => {
        this.salvando.set(false);
        this.criou.emit(categorias);
        this.valor.set(categorias.find((c) => c.toLowerCase() === nome.toLowerCase()) ?? nome);
        this.criando.set(false);
      },
      error: (falha) => {
        this.salvando.set(false);
        this.valor.set(anterior);
        this.erro.set(mensagemDeErro(falha));
      },
    });
  }
}
