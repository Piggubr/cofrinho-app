import { Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { ConferenciaDaFatura, ContaOuCartao, LinhaDaConferencia } from '../../core/api/models';
import { codigoDoErro, pedeConsentimento, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { TAMANHO_MAXIMO_DE_IMAGEM, lerImagemComoBase64 } from '../../core/ui/arquivo';

type Linha = LinhaDaConferencia & { marcada: boolean };

/**
 * Confere o PDF da fatura do cartao com o que ja foi lancado. O plano gratuito ve so as
 * contagens; o Premium ve as linhas por grupo e adiciona as que faltam pela importacao.
 */
@Component({
  selector: 'app-conferir-fatura',
  imports: [FormsModule, RouterLink, MoedaPipe, DataBrPipe],
  template: `
    <section class="cartao" aria-labelledby="titulo-conferir-fatura">
      <div class="cartao-titulo">
        <h2 i18n id="titulo-conferir-fatura">Conferir com a fatura do banco</h2>
      </div>
      <p i18n class="linha-detalhe">
        Escolha o PDF da fatura do cartão. O Piggu mostra o que já está lançado e o que falta.
      </p>
      @if (!cartao()) {
        <div class="campo">
          <label i18n for="cartao-da-fatura">Cartão da fatura</label>
          <select
            id="cartao-da-fatura"
            [ngModel]="cartaoEscolhido()"
            (ngModelChange)="cartaoEscolhido.set($event)"
          >
            <option i18n value="">Escolha o cartão</option>
            @for (c of cartoes(); track c.id) {
              <option [value]="c.id">{{ c.nome }}</option>
            }
          </select>
        </div>
      }
      <div class="acoes">
        <label i18n class="botao secundario arquivo">
          Escolher PDF da fatura
          <input type="file" accept=".pdf,application/pdf" (change)="escolher($event)" hidden />
        </label>
      </div>

      @if (precisaSenha()) {
        <div class="campo">
          <label i18n for="senha-da-fatura">Senha do PDF</label>
          <input
            id="senha-da-fatura"
            type="password"
            maxlength="100"
            autocomplete="off"
            [ngModel]="senha()"
            (ngModelChange)="senha.set($event)"
          />
        </div>
        <div class="acoes">
          <button i18n type="button" class="botao" (click)="enviar()" [disabled]="ocupado()">
            Tentar de novo
          </button>
        </div>
      }
      @if (pedidoDeIa()) {
        <p class="aviso">{{ pedidoDeIa() }}</p>
        <div class="acoes">
          <button
            i18n
            type="button"
            class="botao"
            (click)="enviar(versaoDoAviso)"
            [disabled]="ocupado()"
          >
            Autorizar e continuar
          </button>
          <button i18n type="button" class="botao contorno" (click)="limpar()">Cancelar</button>
        </div>
      }
      @if (ocupado()) {
        <p i18n class="linha-detalhe" role="status">Lendo a fatura...</p>
      }
      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      }
      @if (convite()) {
        <p class="aviso">
          <ng-container i18n>A conferência linha a linha faz parte do Piggu Premium.</ng-container>
          <a i18n routerLink="/plano" class="botao pequeno">Conhecer o Piggu Premium</a>
        </p>
      }
      @if (feito()) {
        <p class="aviso sucesso" role="status">{{ feito() }}</p>
        @if (cartao() && mes() && !paga()) {
          <div class="acoes">
            <button i18n type="button" class="botao secundario" (click)="pagar()" [disabled]="ocupado()">
              Marcar fatura como paga
            </button>
          </div>
        }
        @if (paga()) {
          <p i18n class="aviso sucesso" role="status">Fatura marcada como paga.</p>
        }
      }

      <div #resultado tabindex="-1" class="resultado">
        @if (conferencia(); as c) {
          <p i18n class="linha-titulo">
            {{ c.contagem.jaLancadas }} já lançadas, faltam {{ c.contagem.faltam }}
          </p>
          @if (c.contagem.confira) {
            <p i18n class="linha-detalhe">Para conferir: {{ c.contagem.confira }}</p>
          }
          @if (c.aviso) {
            <p class="aviso">{{ c.aviso }}</p>
          }
          @if (c.linhas) {
            @for (grupo of grupos(); track grupo.chave) {
              @if (grupo.linhas.length) {
                <h3 class="linha-titulo">{{ grupo.titulo }} ({{ grupo.linhas.length }})</h3>
                @for (l of grupo.linhas; track l.idExterno) {
                  <div class="linha">
                    <label class="marcar">
                      <input type="checkbox" [checked]="l.marcada" (change)="alternar(l.idExterno)" />
                      <span>
                        <span class="linha-titulo">{{ l.descricao }}</span>
                        <span class="linha-detalhe">
                          {{ l.data | dataBr }} · {{ l.categoria }}
                          @if (l.parcelaTotal) {
                            <ng-container i18n>· parcela {{ l.parcelaNumero }}/{{ l.parcelaTotal }}</ng-container>
                          }
                          @if (l.gastoDescricao) {
                            <ng-container i18n>
                              · parecido com {{ l.gastoDescricao }} ({{ l.gastoData | dataBr }})
                            </ng-container>
                          }
                        </span>
                      </span>
                    </label>
                    <span class="linha-valor">{{ l.valor | moeda }}</span>
                  </div>
                }
              }
            }
            @if (jaLancadas().length) {
              <details>
                <summary i18n>Já lançadas ({{ jaLancadas().length }})</summary>
                @for (l of jaLancadas(); track l.idExterno) {
                  <div class="linha">
                    <div>
                      <p class="linha-titulo">{{ l.descricao }}</p>
                      <p class="linha-detalhe">{{ l.data | dataBr }}</p>
                    </div>
                    <span class="linha-valor">{{ l.valor | moeda }}</span>
                  </div>
                }
              </details>
            }
            @if (marcadas().length) {
              <div class="acoes">
                <button type="button" class="botao" (click)="adicionar()" [disabled]="ocupado()">
                  <ng-container i18n>Adicionar {{ marcadas().length }}</ng-container>
                </button>
              </div>
            }
          }
        }
      </div>
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
    .resultado:focus {
      outline: none;
    }
  `,
})
export class ConferirFatura {
  private readonly finance = inject(FinanceService);

  /** Cartao da fatura aberta (Contas); sem ele, a pessoa escolhe um dos `cartoes` (Gastos). */
  readonly cartao = input<ContaOuCartao | null>(null);
  readonly cartoes = input<ContaOuCartao[]>([]);
  /** Mes (AAAA-MM) da fatura aberta, o que "Marcar fatura como paga" quita. */
  readonly mes = input<string | null>(null);
  readonly importado = output<void>();

  private readonly alvo = viewChild<ElementRef<HTMLElement>>('resultado');
  protected readonly versaoDoAviso = VERSAO_DO_AVISO;
  protected readonly cartaoEscolhido = signal('');
  protected readonly senha = signal('');
  protected readonly precisaSenha = signal(false);
  protected readonly pedidoDeIa = signal('');
  protected readonly convite = signal(false);
  protected readonly erro = signal('');
  protected readonly feito = signal('');
  protected readonly paga = signal(false);
  protected readonly ocupado = signal(false);
  protected readonly conferencia = signal<ConferenciaDaFatura | null>(null);
  protected readonly linhas = signal<Linha[]>([]);
  /** O PDF fica em memoria para a senha e a autorizacao reenviarem sem escolher de novo. */
  private pdfBase64 = '';

  protected readonly marcadas = computed(() => this.linhas().filter((l) => l.marcada));
  protected readonly jaLancadas = computed(() => this.linhas().filter((l) => l.situacao === 'JA_LANCADA'));
  protected readonly grupos = computed(() => {
    const lista = this.linhas();
    return [
      { chave: 'faltam', titulo: $localize`Faltam`, linhas: lista.filter((l) => l.tipo === 'COMPRA' && l.situacao === 'FALTA') },
      { chave: 'confira', titulo: $localize`Confira`, linhas: lista.filter((l) => l.tipo === 'COMPRA' && l.situacao === 'CONFIRA') },
      { chave: 'tarifas', titulo: $localize`Tarifas`, linhas: lista.filter((l) => l.tipo === 'TARIFA' && l.situacao !== 'JA_LANCADA') },
    ];
  });

  private contaId(): string | null {
    return this.cartao()?.id ?? (this.cartaoEscolhido() || null);
  }

  protected async escolher(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    campo.value = '';
    this.limpar();
    if (!arquivo) {
      return;
    }
    if (arquivo.size > TAMANHO_MAXIMO_DE_IMAGEM) {
      this.erro.set($localize`PDF grande demais (máximo 5 MB).`);
      return;
    }
    try {
      this.pdfBase64 = (await lerImagemComoBase64(arquivo)).base64;
    } catch {
      this.erro.set($localize`Não consegui abrir esse PDF.`);
      return;
    }
    this.enviar();
  }

  protected enviar(versaoDoAviso?: string): void {
    this.erro.set('');
    this.convite.set(false);
    this.pedidoDeIa.set('');
    this.ocupado.set(true);
    this.finance
      .conferirFatura(this.pdfBase64, this.senha() || null, this.contaId(), versaoDoAviso)
      .subscribe({
        next: (c) => {
          this.ocupado.set(false);
          this.precisaSenha.set(false);
          this.conferencia.set(c);
          this.convite.set(c.linhas === null);
          this.linhas.set((c.linhas ?? []).map((l) => ({ ...l, marcada: l.situacao === 'FALTA' && l.tipo === 'COMPRA' })));
          setTimeout(() => this.alvo()?.nativeElement.focus());
        },
        error: (falha) => {
          this.ocupado.set(false);
          const codigo = codigoDoErro(falha);
          const mensagem = mensagemDeErro(falha);
          if (codigo === 'PDF_COM_SENHA') {
            this.precisaSenha.set(true);
            this.erro.set(mensagem);
          } else if (pedeConsentimento(falha) || codigo === 'AVISO_DESATUALIZADO') {
            this.pedidoDeIa.set(mensagem);
          } else if (codigo === 'PLANO_PREMIUM') {
            this.convite.set(true);
            this.erro.set(mensagem);
          } else {
            this.erro.set(mensagem);
          }
        },
      });
  }

  protected limpar(): void {
    this.pdfBase64 = '';
    this.senha.set('');
    this.precisaSenha.set(false);
    this.pedidoDeIa.set('');
    this.convite.set(false);
    this.erro.set('');
    this.feito.set('');
    this.paga.set(false);
    this.conferencia.set(null);
    this.linhas.set([]);
  }

  protected alternar(idExterno: string): void {
    this.linhas.update((lista) => lista.map((l) => (l.idExterno === idExterno ? { ...l, marcada: !l.marcada } : l)));
  }

  protected adicionar(): void {
    this.ocupado.set(true);
    this.finance
      .importarExtrato(
        this.marcadas().map(({ data, descricao, valor, idExterno, categoria, parcelaNumero, parcelaTotal }) => ({
          data, descricao, valor, idExterno, categoria, parcelaNumero, parcelaTotal,
        })),
        this.contaId(),
      )
      .subscribe({
        next: ({ importados, pulados }) => {
          this.ocupado.set(false);
          this.conferencia.set(null);
          this.linhas.set([]);
          this.feito.set(
            pulados
              ? $localize`Adicionados: ${importados}. Já estavam no Piggu: ${pulados}.`
              : $localize`Adicionados: ${importados}.`,
          );
          this.importado.emit();
        },
        error: (falha) => {
          this.ocupado.set(false);
          this.erro.set(mensagemDeErro(falha));
        },
      });
  }

  protected pagar(): void {
    const cartao = this.cartao();
    const mes = this.mes();
    if (!cartao || !mes) {
      return;
    }
    this.ocupado.set(true);
    this.finance.pagarFatura(cartao.id, mes).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.paga.set(true);
        this.importado.emit();
      },
      error: (falha) => {
        this.ocupado.set(false);
        this.erro.set(mensagemDeErro(falha));
      },
    });
  }
}
