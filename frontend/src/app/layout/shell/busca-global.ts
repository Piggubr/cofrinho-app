import { Component, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, catchError, debounceTime, forkJoin, of, switchMap } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { Filme, Gasto, ItemDeCompra, Lugar } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { Icone, NomeDoIcone } from '../../core/ui/icone';
import { MoedaService } from '../../core/ui/moeda';

export interface TelaBuscavel {
  readonly rota: string;
  readonly rotulo: string;
  readonly icone: NomeDoIcone;
}

interface Resultado {
  readonly grupo: string;
  readonly icone: NomeDoIcone;
  readonly titulo: string;
  readonly detalhe: string;
  readonly rota: string;
  readonly params?: Record<string, string>;
}

/** Sem acento e em minusculas: "Pão" acha "pao". */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Busca global (lupa ou Ctrl+K): telas, gastos, lugares, filmes e compras.
 * Gastos sao buscados no servidor; o resto, que e lista curta da familia, e filtrado aqui.
 */
@Component({
  selector: 'app-busca-global',
  imports: [Icone],
  template: `
    <button
      type="button"
      class="lupa"
      (click)="abrir()"
      aria-label="Buscar (Ctrl+K)"
      i18n-aria-label
      title="Ctrl+K"
    >
      <app-icone nome="busca" />
    </button>
    @if (aberta()) {
      <div class="fundo" (click)="fechar()"></div>
      <div class="busca" role="dialog" aria-modal="true" aria-label="Buscar no Piggu" i18n-aria-label>
        <input
          #campo
          type="search"
          role="combobox"
          aria-controls="resultados-da-busca"
          [attr.aria-activedescendant]="resultados().length ? 'resultado-' + selecionado() : null"
          aria-expanded="true"
          placeholder="Buscar gastos, lugares, filmes, compras e telas"
          i18n-placeholder
          maxlength="100"
          [value]="termo()"
          (input)="digitar($any($event.target).value)"
          (keydown)="teclar($event)"
        />
        <ul id="resultados-da-busca" role="listbox" aria-label="Resultados" i18n-aria-label>
          @for (r of resultados(); track $index; let i = $index) {
            @if (i === 0 || resultados()[i - 1].grupo !== r.grupo) {
              <li class="grupo" role="presentation">{{ r.grupo }}</li>
            }
            <li
              [id]="'resultado-' + i"
              role="option"
              [attr.aria-selected]="i === selecionado()"
              [class.selecionado]="i === selecionado()"
              (click)="ir(r)"
              (mouseenter)="selecionado.set(i)"
            >
              <span class="icone-do-resultado"><app-icone [nome]="r.icone" [tamanho]="18" /></span>
              <span class="texto">
                <span>{{ r.titulo }}</span>
                @if (r.detalhe) {
                  <small>{{ r.detalhe }}</small>
                }
              </span>
            </li>
          }
        </ul>
        @if (termo().trim().length >= 2 && !resultados().length) {
          <p i18n class="vazio">Nada encontrado para "{{ termo() }}".</p>
        }
      </div>
    }
  `,
  styles: `
    .lupa {
      display: inline-grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border: 0;
      border-radius: 50%;
      background: none;
      color: var(--ink-soft);
      cursor: pointer;
      transition: background-color 0.15s ease;
    }
    .lupa:hover {
      background: var(--surface);
    }
    .fundo {
      position: fixed;
      inset: 0;
      background: rgba(43, 30, 50, 0.35);
      z-index: 40;
    }
    .busca {
      position: fixed;
      top: calc(10vh + env(safe-area-inset-top, 0px));
      left: 50%;
      transform: translateX(-50%);
      width: min(560px, calc(100vw - 32px));
      max-height: 70vh;
      overflow: auto;
      background: var(--surface);
      border-radius: var(--raio);
      box-shadow: var(--shadow-float);
      padding: 0.75rem;
      z-index: 41;
    }
    ul {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0;
    }
    .grupo {
      font-size: 0.7rem;
      font-weight: 600;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.06em;
      margin: 0.75rem 0.5rem 0.3rem;
    }
    [role='option'] {
      display: flex;
      gap: 0.7rem;
      align-items: center;
      padding: 0.5rem;
      border-radius: var(--raio-pequeno);
      cursor: pointer;
    }
    .icone-do-resultado {
      display: grid;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 10px;
      background: var(--surface-sunken);
      color: var(--rose-deep);
    }
    .selecionado {
      background: var(--surface-sunken);
    }
    .selecionado .icone-do-resultado {
      background: var(--surface);
    }
    .texto {
      display: grid;
    }
    small {
      color: var(--muted);
    }
  `,
  host: { '(document:keydown)': 'atalho($event)' },
})
export class BuscaGlobal {
  private readonly finance = inject(FinanceService);
  private readonly lifestyle = inject(LifestyleService);
  private readonly auth = inject(AuthService);
  private readonly moeda = inject(MoedaService);
  private readonly router = inject(Router);

  /** Telas que a pessoa ve no menu. */
  readonly telas = input.required<TelaBuscavel[]>();

  protected readonly aberta = signal(false);
  protected readonly termo = signal('');
  protected readonly selecionado = signal(0);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  private readonly gastos = signal<Gasto[]>([]);
  private readonly listas = signal<{ lugares: Lugar[]; filmes: Filme[]; compras: ItemDeCompra[] } | null>(null);
  private readonly digitado = new Subject<string>();

  protected readonly resultados = computed<Resultado[]>(() => {
    const termo = normalizar(this.termo().trim());
    if (!termo) {
      return [];
    }
    const tem = (...textos: (string | null | undefined)[]) => textos.some((t) => t && normalizar(t).includes(termo));
    const telas = this.telas()
      .filter((t) => tem(t.rotulo))
      .map((t) => ({ grupo: $localize`Telas`, icone: t.icone, titulo: t.rotulo, detalhe: '', rota: t.rota }));
    if (termo.length < 2) {
      return telas;
    }
    const listas = this.listas();
    const gastos = this.gastos().map((g) => ({
      grupo: $localize`Gastos`,
      icone: 'carteira' as const,
      titulo: g.item,
      detalhe: `${g.data.split('-').reverse().join('/')} · ${this.moeda.formatar(g.valor)}`,
      rota: '/gastos',
      params: { mes: g.data.slice(0, 7) },
    }));
    const lugares = (listas?.lugares ?? [])
      .filter((l) => tem(l.nome, l.localizacao, l.categoria))
      .slice(0, 8)
      .map((l) => ({ grupo: $localize`Lugares`, icone: 'local' as const, titulo: l.nome, detalhe: l.localizacao, rota: '/lugares' }));
    const filmes = (listas?.filmes ?? [])
      .filter((f) => tem(f.titulo))
      .slice(0, 8)
      .map((f) => ({ grupo: $localize`Filmes`, icone: 'filme' as const, titulo: f.titulo, detalhe: f.ano, rota: '/filmes' }));
    const compras = (listas?.compras ?? [])
      .filter((c) => tem(c.item, c.marca))
      .slice(0, 8)
      .map((c) => ({ grupo: $localize`Compras`, icone: 'carrinho' as const, titulo: c.item, detalhe: c.lista, rota: '/compras' }));
    return [...telas, ...gastos, ...lugares, ...filmes, ...compras];
  });

  constructor() {
    this.digitado
      .pipe(
        debounceTime(250),
        switchMap((termo) =>
          termo.trim().length < 2 || this.auth.ehMembro()
            ? of([])
            : this.finance.buscarGastos(termo).pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((gastos) => this.gastos.set(gastos));
  }

  /** Ctrl+K / Cmd+K abre e fecha; Esc fecha. */
  protected atalho(evento: KeyboardEvent): void {
    if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === 'k') {
      evento.preventDefault();
      if (this.aberta()) {
        this.fechar();
      } else {
        this.abrir();
      }
    } else if (evento.key === 'Escape' && this.aberta()) {
      this.fechar();
    }
  }

  protected abrir(): void {
    this.aberta.set(true);
    this.termo.set('');
    this.selecionado.set(0);
    setTimeout(() => this.campo()?.nativeElement.focus());
    // Membro da familia so ve as telas: gastos e listas sao do titular.
    if (!this.auth.ehMembro() && !this.listas()) {
      forkJoin({
        lugares: this.lifestyle.listarLugares().pipe(catchError(() => of([]))),
        filmes: this.lifestyle.listarFilmes().pipe(catchError(() => of([]))),
        compras: this.lifestyle.listarCompras().pipe(catchError(() => of([]))),
      }).subscribe((listas) => this.listas.set(listas));
    }
  }

  protected fechar(): void {
    this.aberta.set(false);
    this.gastos.set([]);
  }

  protected digitar(termo: string): void {
    this.termo.set(termo);
    this.selecionado.set(0);
    this.digitado.next(termo);
  }

  protected teclar(evento: KeyboardEvent): void {
    const total = this.resultados().length;
    if (evento.key === 'ArrowDown' && total) {
      evento.preventDefault();
      this.selecionado.update((i) => (i + 1) % total);
    } else if (evento.key === 'ArrowUp' && total) {
      evento.preventDefault();
      this.selecionado.update((i) => (i - 1 + total) % total);
    } else if (evento.key === 'Enter' && total) {
      evento.preventDefault();
      this.ir(this.resultados()[this.selecionado()]);
    }
  }

  protected ir(resultado: Resultado): void {
    this.fechar();
    void this.router.navigate([resultado.rota], { queryParams: resultado.params });
  }
}
