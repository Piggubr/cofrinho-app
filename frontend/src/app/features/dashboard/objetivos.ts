import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { ObjetivoDoCofrinho } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { hojeIso } from '../../core/ui/datas';
import { Focar } from '../../core/ui/focar';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';

/**
 * Cofrinhos por objetivo no painel: quanto juntar, ate quando e quanto guardar por mes.
 *
 * <p>O gratuito tem um objetivo; o Premium, quantos quiser. Quem barra e o backend; aqui
 * o formulario de criar so some e da lugar ao convite do Premium. O membro nao ve este
 * bloco: o total do objetivo soma o deposito de todos.</p>
 */
@Component({
  selector: 'app-objetivos',
  imports: [FormsModule, RouterLink, MoedaPipe, DataBrPipe, Focar, Icone],
  template: `
    <section class="cartao" aria-labelledby="titulo-objetivos">
      <div class="cartao-titulo">
        <h2 i18n id="titulo-objetivos">Cofrinhos por objetivo</h2>
      </div>

      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      }

      @if (carregando()) {
        <p i18n class="carregando">Carregando...</p>
      } @else {
        @for (objetivo of objetivos(); track objetivo.id) {
          <article class="objetivo">
            <div class="linha">
              <div>
                <p class="linha-titulo">{{ objetivo.nome }}</p>
                <p i18n class="linha-detalhe">
                  {{ objetivo.guardado | moeda }} de {{ objetivo.alvo | moeda }}
                </p>
              </div>
              <div class="linha-acoes">
                <button
                  type="button"
                  class="botao icone-so fantasma"
                  (click)="editar(objetivo)"
                  aria-label="Editar"
                  i18n-aria-label
                  title="Editar"
                  i18n-title
                >
                  <app-icone nome="lapis" [tamanho]="18" />
                </button>
                <button
                  type="button"
                  class="botao icone-so perigo"
                  (click)="excluir(objetivo)"
                  aria-label="Apagar"
                  i18n-aria-label
                  title="Apagar"
                  i18n-title
                >
                  <app-icone nome="lixeira" [tamanho]="18" />
                </button>
              </div>
            </div>
            <div class="progresso" [class.concluido]="objetivo.concluido">
              <span [style.width.%]="objetivo.percentual"></span>
            </div>
            <p class="linha-detalhe situacao">
              @if (objetivo.concluido) {
                <ng-container i18n>Objetivo alcançado!</ng-container>
              } @else if (objetivo.atrasado) {
                <ng-container i18n
                  >O prazo passou e faltam {{ objetivo.falta | moeda }}.</ng-container
                >
              } @else if (objetivo.porMes !== null) {
                <ng-container i18n
                  >Guarde {{ objetivo.porMes | moeda }} por mês até
                  {{ objetivo.prazo | dataBr }}.</ng-container
                >
              } @else {
                <ng-container i18n>Faltam {{ objetivo.falta | moeda }}, sem prazo.</ng-container>
              }
            </p>
            @if (!objetivo.concluido) {
              <div class="linha-de-campos">
                <div class="campo">
                  <label i18n [for]="'deposito-' + objetivo.id"
                    >Guardar em {{ objetivo.nome }} ({{ moeda.simbolo() }})</label
                  >
                  <input
                    [id]="'deposito-' + objetivo.id"
                    type="number"
                    min="0.01"
                    step="0.01"
                    inputmode="decimal"
                    [ngModel]="valores()[objetivo.id] ?? null"
                    (ngModelChange)="guardarValor(objetivo.id, $event)"
                    placeholder="0,00"
                  />
                </div>
                <div class="campo alinhado">
                  <button
                    i18n
                    type="button"
                    class="botao secundario"
                    (click)="depositar(objetivo)"
                    [disabled]="depositando() === objetivo.id"
                  >
                    Guardar
                  </button>
                </div>
              </div>
            }
          </article>
        } @empty {
          <p i18n class="vazio">
            Juntando para uma viagem, um sofá ou a reserva? Dê um nome, quanto e até quando, e o
            Piggu diz quanto guardar por mês.
          </p>
          <p class="vazio-acao">
            <button i18n class="botao contorno pequeno" appFocar="nome-do-objetivo">
              Criar o primeiro objetivo
            </button>
          </p>
        }

        @if (editando() || podeCriar()) {
          <h3 class="subtitulo">
            @if (editando()) {
              <ng-container i18n>Editar objetivo</ng-container>
            } @else {
              <ng-container i18n>Novo objetivo</ng-container>
            }
          </h3>
          <div class="linha-de-campos">
            <div class="campo">
              <label i18n for="nome-do-objetivo">Nome</label>
              <input
                id="nome-do-objetivo"
                maxlength="60"
                [ngModel]="nome()"
                (ngModelChange)="nome.set($event)"
                placeholder="Viagem de férias"
                i18n-placeholder
              />
            </div>
            <div class="campo">
              <label i18n for="alvo-do-objetivo">Quanto ({{ moeda.simbolo() }})</label>
              <input
                id="alvo-do-objetivo"
                type="number"
                min="0.01"
                step="0.01"
                inputmode="decimal"
                [ngModel]="alvo()"
                (ngModelChange)="alvo.set($event)"
                placeholder="0,00"
              />
            </div>
            <div class="campo">
              <label i18n for="prazo-do-objetivo">Até quando (opcional)</label>
              <input
                id="prazo-do-objetivo"
                type="date"
                [min]="hoje"
                [ngModel]="prazo()"
                (ngModelChange)="prazo.set($event)"
              />
            </div>
            <div class="campo alinhado acoes">
              <button i18n type="button" class="botao" (click)="salvar()" [disabled]="salvando()">
                Salvar objetivo
              </button>
              @if (editando()) {
                <button i18n type="button" class="botao fantasma" (click)="limpar()">
                  Cancelar
                </button>
              }
            </div>
          </div>
        } @else {
          <p i18n class="linha-detalhe convite">
            No gratuito, um objetivo por vez. Com o
            <a routerLink="/plano">Piggu Premium</a>, quantos quiser.
          </p>
        }
      }
    </section>
  `,
  styles: `
    .objetivo {
      padding: 0.6rem 0;
      border-bottom: 1px solid var(--border);
    }
    .objetivo .situacao {
      margin: 0.35rem 0 0.5rem;
    }
    .progresso.concluido > span {
      background: var(--ok);
    }
    .subtitulo {
      margin: 1rem 0 0.4rem;
      font-size: 1rem;
    }
    .acoes {
      display: flex;
      gap: 0.5rem;
    }
    .convite {
      margin-top: 0.8rem;
    }
  `,
})
export class Objetivos {
  /** Um deposito entrou: o total do cofrinho no painel muda junto. */
  readonly depositou = output<void>();

  protected readonly moeda = inject(MoedaService);
  private readonly finance = inject(FinanceService);
  private readonly auth = inject(AuthService);

  protected readonly hoje = hojeIso();
  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly objetivos = signal<ObjetivoDoCofrinho[]>([]);
  protected readonly valores = signal<Record<string, number | null>>({});
  protected readonly depositando = signal<string | null>(null);

  protected readonly editando = signal<string | null>(null);
  protected readonly nome = signal('');
  protected readonly alvo = signal<number | null>(null);
  protected readonly prazo = signal<string | null>(null);
  protected readonly salvando = signal(false);

  /** Espelha o backend: o gratuito tem um objetivo. */
  protected readonly podeCriar = computed(
    () => this.auth.ehPremium() || this.objetivos().length < 1,
  );

  constructor() {
    this.recarregar();
  }

  protected guardarValor(id: string, valor: number | null): void {
    this.valores.update((atuais) => ({ ...atuais, [id]: valor }));
  }

  protected depositar(objetivo: ObjetivoDoCofrinho): void {
    const valor = this.valores()[objetivo.id];
    if (!valor || valor <= 0) {
      this.erro.set($localize`Digite um valor de depósito válido.`);
      return;
    }
    this.depositando.set(objetivo.id);
    this.erro.set('');
    this.finance.depositar(valor, hojeIso(), objetivo.id).subscribe({
      next: () => {
        this.guardarValor(objetivo.id, null);
        this.depositando.set(null);
        this.depositou.emit();
        this.recarregar();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.depositando.set(null);
      },
    });
  }

  protected editar(objetivo: ObjetivoDoCofrinho): void {
    this.editando.set(objetivo.id);
    this.nome.set(objetivo.nome);
    this.alvo.set(objetivo.alvo);
    this.prazo.set(objetivo.prazo);
  }

  protected limpar(): void {
    this.editando.set(null);
    this.nome.set('');
    this.alvo.set(null);
    this.prazo.set(null);
  }

  protected salvar(): void {
    const nome = this.nome().trim();
    const alvo = this.alvo();
    if (!nome) {
      this.erro.set($localize`Dê um nome ao objetivo.`);
      return;
    }
    if (!alvo || alvo <= 0) {
      this.erro.set($localize`Digite quanto quer juntar.`);
      return;
    }
    const pedido = { nome, alvo, prazo: this.prazo() || null };
    const id = this.editando();
    this.salvando.set(true);
    this.erro.set('');
    const chamada: Observable<unknown> = id
      ? this.finance.alterarObjetivo(id, pedido)
      : this.finance.criarObjetivo(pedido);
    chamada.subscribe({
      next: () => {
        this.salvando.set(false);
        this.limpar();
        this.recarregar();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  protected excluir(objetivo: ObjetivoDoCofrinho): void {
    if (
      !confirm(
        $localize`Apagar "${objetivo.nome}"? O que foi guardado nele volta para o cofrinho geral.`,
      )
    ) {
      return;
    }
    this.finance.excluirObjetivo(objetivo.id).subscribe({
      next: () => {
        if (this.editando() === objetivo.id) {
          this.limpar();
        }
        this.recarregar();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private recarregar(): void {
    this.finance.listarObjetivos().subscribe({
      next: (objetivos) => {
        this.objetivos.set(objetivos);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
