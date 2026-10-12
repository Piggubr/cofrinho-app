import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { Nota } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Quantas notas cabem no Painel; o resto fica no Calendario. */
const NO_PAINEL = 5;

/**
 * Suas notas no Painel: as mesmas notas e lembretes do Calendario, com um jeito rapido
 * de anotar sem sair da tela inicial. Nota criada aqui nao tem data nem valor; para
 * virar lembrete num dia ou gasto, o caminho e o Calendario.
 */
@Component({
  selector: 'app-notas-do-painel',
  imports: [FormsModule, RouterLink, DataBrPipe, Icone],
  template: `
    <section class="cartao" aria-labelledby="titulo-notas-painel">
      <div class="cartao-titulo">
        <h2 i18n id="titulo-notas-painel">Suas notas</h2>
        <a i18n class="ver-todas" routerLink="/calendario">Ver todas</a>
      </div>

      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      }

      @for (nota of recentes(); track nota.id) {
        <div class="linha">
          <div>
            <p class="linha-titulo">{{ nota.titulo }}</p>
            @if (nota.texto || nota.data) {
              <p class="linha-detalhe">
                @if (nota.data) {
                  {{ nota.data | dataBr }}
                }
                @if (nota.data && nota.texto) {
                  ·
                }
                {{ nota.texto }}
              </p>
            }
          </div>
          <button
            type="button"
            class="botao icone-so perigo"
            (click)="excluir(nota)"
            aria-label="Apagar"
            i18n-aria-label
            title="Apagar"
            i18n-title
          >
            <app-icone nome="lixeira" [tamanho]="18" />
          </button>
        </div>
      } @empty {
        @if (!carregando()) {
          <p i18n class="vazio">Nenhuma nota ainda.</p>
        }
      }

      @if (escrevendo()) {
        <div class="nova">
          <div class="campo">
            <label i18n for="tituloNotaPainel">Título</label>
            <input
              id="tituloNotaPainel"
              type="text"
              maxlength="150"
              [ngModel]="titulo()"
              (ngModelChange)="titulo.set($event)"
            />
          </div>
          <div class="campo">
            <label i18n for="textoNotaPainel">Nota</label>
            <textarea
              id="textoNotaPainel"
              rows="3"
              maxlength="1500"
              i18n-placeholder
              placeholder="Escreve aqui..."
              [ngModel]="texto()"
              (ngModelChange)="texto.set($event)"
            ></textarea>
          </div>
          <div class="acoes">
            <button type="button" class="botao" (click)="salvar()" [disabled]="salvando()">
              @if (salvando()) {
                <ng-container i18n>Guardando...</ng-container>
              } @else {
                <ng-container i18n>Salvar nota</ng-container>
              }
            </button>
            <button i18n type="button" class="botao contorno" (click)="escrevendo.set(false)">
              Cancelar
            </button>
          </div>
        </div>
      } @else {
        <div class="acoes">
          <button i18n type="button" class="botao secundario" (click)="escrevendo.set(true)">
            + Criar outra
          </button>
        </div>
      }
    </section>
  `,
  styles: `
    .ver-todas {
      font-size: 0.8rem;
    }
    .nova {
      margin-top: 0.8rem;
      display: grid;
      gap: 0.7rem;
    }
    .linha-titulo,
    .linha-detalhe {
      margin: 0;
    }
    .linha-detalhe {
      white-space: pre-line;
      overflow-wrap: anywhere;
    }
  `,
})
export class NotasDoPainel {
  private readonly finance = inject(FinanceService);

  protected readonly notas = signal<Nota[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly escrevendo = signal(false);
  protected readonly salvando = signal(false);
  protected readonly titulo = signal('');
  protected readonly texto = signal('');

  protected readonly recentes = computed(() =>
    [...this.notas()].sort((a, b) => b.criadoEm.localeCompare(a.criadoEm)).slice(0, NO_PAINEL),
  );

  constructor() {
    this.finance.listarNotas().subscribe({
      next: (notas) => {
        this.notas.set(notas);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  protected salvar(): void {
    const titulo = this.titulo().trim();
    if (!titulo) {
      this.erro.set($localize`Digite o título da nota.`);
      return;
    }
    this.erro.set('');
    this.salvando.set(true);
    this.finance.criarNota({ titulo, texto: this.texto().trim() || null }).subscribe({
      next: (nota) => {
        this.notas.update((lista) => [nota, ...lista]);
        this.titulo.set('');
        this.texto.set('');
        this.escrevendo.set(false);
        this.salvando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  protected excluir(nota: Nota): void {
    if (!confirm($localize`Apagar a nota "${nota.titulo}"?`)) {
      return;
    }
    this.finance.excluirNota(nota.id).subscribe({
      next: () => this.notas.update((lista) => lista.filter((n) => n.id !== nota.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
