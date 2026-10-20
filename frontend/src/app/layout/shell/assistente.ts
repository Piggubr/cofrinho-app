import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { FinanceService } from '../../core/api/finance.service';
import { SituacaoDoAssistente } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { VERSAO_DO_AVISO, pedeConsentimento } from '../../core/privacidade/aviso';
import {
  DIAS_GUARDADA,
  FalaGuardada,
  esquecerConversa,
  guardarConversa,
  lerConversa,
  podarConversa,
} from '../../core/privacidade/conversa-do-assistente';
import { toqueNaTela } from '../../core/ui/focar';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaService } from '../../core/ui/moeda';
import { travarRolagemEnquanto } from '../../core/ui/travar-rolagem';

/** Nome das telas que o assistente pode sugerir abrir. */
const TELAS: Record<string, string> = {
  painel: 'Painel',
  gastos: 'Gastos',
  receitas: 'Receitas',
  relatorios: 'Relatórios',
  orcamentos: 'Orçamento',
  'contas-fixas': 'Contas do mês',
  'contas-e-cartoes': 'Cartões e bancos',
  calendario: 'Calendário',
  planejar: 'Planejar',
  plano: 'Plano',
};

const SUGESTOES = [
  'Quanto gastei este mês?',
  'Vou estourar algum orçamento?',
  'Onde dá para cortar gastos?',
  'Quanto falta para meus objetivos?',
];

/** Falas que vao como contexto para a IA. Igual a AssistenteService.FALAS_NO_CONTEXTO. */
const FALAS_NO_CONTEXTO = 10;

/**
 * Assistente de IA (Premium): perguntas sobre o dinheiro da familia, so leitura.
 *
 * <p>A conversa fica so neste aparelho, por {@link DIAS_GUARDADA} dias, e sai ao sair da
 * conta: o servidor nao guarda. No primeiro uso a pessoa autoriza o envio ao Gemini; a
 * autorizacao e gravada no toque e vale ate o aviso de privacidade mudar. Se a gravacao
 * falhar, ela segue na primeira pergunta, como na leitura de nota pela IA.</p>
 */
@Component({
  selector: 'app-assistente',
  imports: [FormsModule, RouterLink, Icone],
  template: `
    <button
      type="button"
      class="abrir"
      (click)="abrir()"
      aria-label="Perguntar ao assistente"
      i18n-aria-label
      title="Assistente"
      i18n-title
    >
      <app-icone nome="brilho" />
    </button>
    @if (aberto()) {
      <div class="fundo" (click)="fechar()"></div>
      <section
        class="painel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-assistente"
        (keydown.escape)="fechar()"
      >
        <div class="topo">
          <img class="avatar grande" src="ilustracoes/piggu-assistente.svg" alt="" />
          <h2 i18n id="titulo-assistente">Assistente</h2>
          @if (falas().length) {
            <button
              i18n
              type="button"
              class="botao fantasma pequeno"
              (click)="recomecar()"
              title="Apaga a conversa deste aparelho"
              i18n-title
            >
              Nova conversa
            </button>
          }
          <button
            type="button"
            class="botao icone-so fantasma"
            (click)="fechar()"
            aria-label="Fechar"
            i18n-aria-label
          >
            <app-icone nome="fechar" [tamanho]="18" />
          </button>
        </div>

        @if (!auth.ehPremium()) {
          <p i18n>
            Pergunte do seu jeito, como "quanto gastei com mercado em setembro?" ou "vou estourar
            algum orçamento?", e o Piggu responde com os seus números.
          </p>
          <a i18n routerLink="/plano" class="botao" (click)="fechar()">Conhecer o Piggu Premium</a>
        } @else if (carregando()) {
          <p i18n class="carregando">Carregando...</p>
        } @else if (situacao() && !situacao()!.habilitado) {
          <p i18n class="vazio">O assistente ainda não está ligado nesta instalação.</p>
        } @else if (precisaAutorizar()) {
          <div class="consentimento">
            <p i18n>
              Para responder, o assistente envia ao Google (Gemini) só o que a pergunta pede: totais
              e gastos do período, sem nome nem e-mail. A conversa não fica guardada no Piggu: fica só
              neste aparelho por {{ diasGuardada }} dias e sai quando você sai da conta.
            </p>
            <p i18n class="linha-detalhe">
              A IA pode errar e não faz recomendação de investimento. Detalhes no
              <a routerLink="/privacidade" (click)="fechar()">aviso de privacidade</a>.
            </p>
            <button i18n type="button" class="botao" (click)="autorizar()">
              Autorizo e quero perguntar
            </button>
          </div>
        } @else {
          <div class="conversa" #conversa aria-live="polite">
            @for (fala of falas(); track $index) {
              <div class="vez" [class.da-pessoa]="fala.pessoa">
                @if (!fala.pessoa) {
                  <img class="avatar" src="ilustracoes/piggu-assistente.svg" alt="" />
                }
                <div class="fala" [class.da-pessoa]="fala.pessoa">
                  <p>{{ fala.texto }}</p>
                  @if (fala.tela && nomeDaTela(fala.tela); as nome) {
                    <a
                      class="botao contorno pequeno"
                      [routerLink]="'/' + fala.tela"
                      (click)="fechar()"
                      i18n
                      >Abrir {{ nome }}</a
                    >
                  }
                </div>
              </div>
            } @empty {
              <p i18n class="linha-detalhe">Pergunte sobre gastos, orçamento, contas e cofrinho.</p>
              <div class="sugestoes">
                @for (sugestao of sugestoes; track sugestao) {
                  <button
                    type="button"
                    class="botao contorno pequeno"
                    (click)="perguntar(sugestao)"
                  >
                    {{ sugestao }}
                  </button>
                }
              </div>
            }
            @if (pensando()) {
              <div class="vez">
                <img class="avatar" src="ilustracoes/piggu-assistente.svg" alt="" />
                <p i18n class="fala pensando">Pensando...</p>
              </div>
            }
          </div>
          @if (erro()) {
            <p class="aviso erro" role="alert">{{ erro() }}</p>
          }
          <form class="pergunta" (ngSubmit)="perguntar(texto())">
            <input
              id="pergunta-ao-assistente"
              #campo
              maxlength="500"
              autocomplete="off"
              [ngModel]="texto()"
              (ngModelChange)="texto.set($event)"
              name="pergunta"
              aria-label="Sua pergunta"
              i18n-aria-label
              placeholder="Ex.: quanto gastei com mercado?"
              i18n-placeholder
            />
            <button
              type="submit"
              class="botao icone-so"
              [disabled]="pensando() || !texto().trim()"
              aria-label="Enviar"
              i18n-aria-label
            >
              <app-icone nome="enviar" [tamanho]="18" />
            </button>
          </form>
          @if (situacao(); as s) {
            <p i18n class="rodape">
              {{ s.restantes }} de {{ s.limite }} perguntas neste mês · a IA pode errar
            </p>
          }
        }
      </section>
    }
  `,
  styles: `
    .abrir {
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
    .abrir:hover {
      background: var(--surface);
    }
    .fundo {
      position: fixed;
      inset: 0;
      background: rgba(43, 30, 50, 0.35);
      z-index: 40;
      touch-action: none;
    }
    .painel {
      position: fixed;
      /* --tela-*: parte visivel da tela, que o teclado do iPhone encolhe e desloca. */
      top: calc(var(--tela-topo, 0px) + 8vh + env(safe-area-inset-top, 0px));
      left: 50%;
      transform: translateX(-50%);
      width: min(560px, calc(100vw - 32px));
      max-height: min(80vh, calc(var(--tela-altura, 100vh) - 8vh - env(safe-area-inset-top, 0px) - 12px));
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
      background: var(--surface);
      border-radius: var(--raio);
      box-shadow: var(--shadow-float);
      padding: 1rem;
      z-index: 41;
    }
    .topo {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .topo h2 {
      margin: 0;
      flex: 1;
      font-size: 1.1rem;
    }
    .conversa {
      overflow: auto;
      overscroll-behavior: contain;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      min-height: 6rem;
    }
    .avatar {
      flex: 0 0 30px;
      width: 30px;
      height: 30px;
    }
    .avatar.grande {
      flex-basis: 36px;
      width: 36px;
      height: 36px;
    }
    /* Uma vez de falar: o avatar do Piggu ao lado da resposta. */
    .vez {
      display: flex;
      align-items: flex-end;
      gap: 0.45rem;
      max-width: 92%;
    }
    .vez.da-pessoa {
      align-self: flex-end;
      justify-content: flex-end;
    }
    .vez .fala {
      max-width: 100%;
    }
    .fala {
      align-self: flex-start;
      max-width: 88%;
      padding: 0.55rem 0.75rem;
      border-radius: var(--raio-pequeno);
      background: var(--surface-sunken);
      white-space: pre-line;
    }
    .fala p {
      margin: 0 0 0.3rem;
    }
    .fala p:last-child {
      margin: 0;
    }
    .fala.da-pessoa {
      align-self: flex-end;
      background: var(--rose-soft, var(--surface-sunken));
    }
    .pensando {
      color: var(--muted);
    }
    .sugestoes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    .pergunta {
      display: flex;
      gap: 0.4rem;
    }
    .pergunta input {
      flex: 1;
    }
    .rodape {
      margin: 0;
      font-size: 0.75rem;
      color: var(--muted);
    }
  `,
  host: { '[class.janela-aberta]': 'aberto()' },
})
export class Assistente {
  protected readonly auth = inject(AuthService);
  private readonly finance = inject(FinanceService);
  private readonly moeda = inject(MoedaService);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private readonly conversa = viewChild<ElementRef<HTMLElement>>('conversa');

  protected readonly sugestoes = SUGESTOES;
  protected readonly aberto = signal(false);
  protected readonly carregando = signal(false);
  protected readonly situacao = signal<SituacaoDoAssistente | null>(null);
  /** A pessoa acabou de autorizar: a proxima pergunta leva a autorizacao. */
  private readonly autorizandoAgora = signal(false);
  protected readonly diasGuardada = DIAS_GUARDADA;
  protected readonly falas = signal<FalaGuardada[]>([]);
  /** De quem sao as falas na tela: trocou a pessoa, le a conversa dela do aparelho. */
  private donoDasFalas: string | null = null;
  protected readonly texto = signal('');
  protected readonly pensando = signal(false);
  protected readonly erro = signal('');

  protected readonly precisaAutorizar = computed(
    () => !!this.situacao() && !this.situacao()!.autorizado && !this.autorizandoAgora(),
  );

  constructor() {
    travarRolagemEnquanto(this.aberto);
    podarConversa();
  }

  protected nomeDaTela(rota: string): string | null {
    return TELAS[rota] ?? null;
  }

  protected abrir(): void {
    this.aberto.set(true);
    this.erro.set('');
    const usuario = this.auth.usuario()?.id ?? null;
    if (usuario !== this.donoDasFalas) {
      this.donoDasFalas = usuario;
      this.falas.set(usuario ? lerConversa(usuario) : []);
      this.rolar();
    }
    if (this.auth.ehPremium() && !this.situacao()) {
      this.carregando.set(true);
      this.finance.situacaoDoAssistente().subscribe({
        next: (situacao) => {
          this.situacao.set(situacao);
          this.carregando.set(false);
          this.focar();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.carregando.set(false);
        },
      });
    } else {
      this.focar();
    }
  }

  protected fechar(): void {
    this.aberto.set(false);
  }

  protected recomecar(): void {
    this.falas.set([]);
    esquecerConversa();
    this.erro.set('');
    this.focar();
  }

  protected autorizar(): void {
    this.autorizandoAgora.set(true);
    this.focar();
    this.finance.autorizarAssistente(VERSAO_DO_AVISO).subscribe({
      next: (situacao) => {
        this.situacao.set(situacao);
        this.autorizandoAgora.set(false);
      },
      // Sem gravar agora, a autorizacao vai junto com a primeira pergunta.
      error: () => undefined,
    });
  }

  protected perguntar(texto: string): void {
    const pergunta = texto.trim();
    if (!pergunta || this.pensando()) {
      return;
    }
    const historico = this.falas()
      .slice(-FALAS_NO_CONTEXTO)
      .map(({ pessoa, texto }) => ({ pessoa, texto }));
    const autorizo = this.autorizandoAgora();
    this.mudarFalas((falas) => [...falas, { pessoa: true, texto: pergunta, em: agora() }]);
    this.texto.set('');
    this.erro.set('');
    this.pensando.set(true);
    this.rolar();
    this.finance
      .perguntarAoAssistente({
        pergunta,
        historico,
        moeda: this.moeda.codigo(),
        ...(autorizo ? { autorizoIa: true, versaoDoAviso: VERSAO_DO_AVISO } : {}),
      })
      .subscribe({
        next: (resposta) => {
          this.mudarFalas((falas) => [
            ...falas,
            { pessoa: false, texto: resposta.texto, tela: resposta.tela, em: agora() },
          ]);
          this.situacao.update(
            (s) => s && { ...s, autorizado: true, restantes: resposta.restantes },
          );
          this.autorizandoAgora.set(false);
          this.pensando.set(false);
          this.rolar();
        },
        error: (falha) => {
          // A pergunta volta para o campo, para nao ter de digitar de novo.
          this.mudarFalas((falas) => falas.slice(0, -1));
          this.texto.set(pergunta);
          this.pensando.set(false);
          if (pedeConsentimento(falha)) {
            this.autorizandoAgora.set(false);
            this.situacao.update((s) => s && { ...s, autorizado: false });
            return;
          }
          this.erro.set(mensagemDeErro(falha));
        },
      });
  }

  /** Muda a conversa na tela e a guarda no aparelho, em nome de quem conversa. */
  private mudarFalas(mudanca: (falas: FalaGuardada[]) => FalaGuardada[]): void {
    this.falas.update(mudanca);
    if (this.donoDasFalas) {
      guardarConversa(this.donoDasFalas, this.falas());
    }
  }

  /** No toque o cursor nao vai sozinho para o campo: subiria o teclado de surpresa. */
  private focar(): void {
    if (toqueNaTela()) {
      return;
    }
    setTimeout(() => this.campo()?.nativeElement.focus());
  }

  private rolar(): void {
    setTimeout(() => {
      const conversa = this.conversa()?.nativeElement;
      conversa?.scrollTo?.({ top: conversa.scrollHeight });
    });
  }
}

function agora(): string {
  return new Date().toISOString();
}
