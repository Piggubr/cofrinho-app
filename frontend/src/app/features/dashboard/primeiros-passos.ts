import { Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { MoedaService } from '../../core/ui/moeda';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

interface EstadoDoGuia {
  moeda?: boolean;
  fechado?: boolean;
}

/** As moedas mais comuns de quem usa o Piggu; a lista inteira fica no Perfil. */
const MOEDAS_DO_GUIA = ['BRL', 'EUR', 'USD', 'GBP', 'ARS', 'CLP', 'UYU', 'JPY'];

/**
 * Guia de primeiro uso no painel: moeda, limite do mes e primeiro gasto.
 *
 * <p>O limite e o gasto contam como feitos pelo que ja existe na conta, entao o guia
 * acompanha o que a pessoa faz em qualquer tela. Quem ja tem os dois (conta antiga)
 * nao ve o guia. A moeda e o "pular" ficam lembrados neste aparelho.</p>
 */
@Component({
  selector: 'app-primeiros-passos',
  imports: [FormsModule, RouterLink],
  template: `
    @if (visivel()) {
      <section class="cartao guia" aria-labelledby="titulo-guia">
        <div class="cartao-titulo">
          <h2 i18n id="titulo-guia">Primeiros passos</h2>
          <span i18n class="etiqueta">{{ feitos() }} de 3</span>
        </div>
        <ol class="passos">
          <li [class.feito]="moedaFeita()">
            <p class="linha-titulo" i18n>1. Em que moeda você gasta?</p>
            @if (moedaFeita()) {
              <p class="linha-detalhe">{{ nomeDaMoeda(moeda.codigo()) }}</p>
            } @else {
              <div class="linha-de-campos">
                <div class="campo">
                  <label i18n for="moeda-do-guia">Moeda</label>
                  <select
                    id="moeda-do-guia"
                    [ngModel]="moedaEscolhida()"
                    (ngModelChange)="moedaEscolhida.set($event)"
                  >
                    @for (codigo of moedas(); track codigo) {
                      <option [value]="codigo">{{ nomeDaMoeda(codigo) }}</option>
                    }
                  </select>
                </div>
                <div class="campo alinhado">
                  <button
                    i18n
                    type="button"
                    class="botao"
                    (click)="confirmarMoeda()"
                    [disabled]="salvando()"
                  >
                    Usar esta
                  </button>
                </div>
              </div>
            }
          </li>
          <li [class.feito]="temLimite()">
            <p class="linha-titulo" i18n>2. Quanto quer gastar por mês?</p>
            @if (!temLimite()) {
              <a i18n class="botao secundario pequeno" routerLink="/orcamentos">Definir o limite</a>
            }
          </li>
          <li [class.feito]="temGasto()">
            <p class="linha-titulo" i18n>3. Lance o primeiro gasto ou importe o extrato</p>
            @if (!temGasto()) {
              <div class="acoes">
                <a i18n class="botao secundario pequeno" routerLink="/gastos">Lançar gasto</a>
                <a i18n class="botao contorno pequeno" routerLink="/gastos" fragment="importar"
                  >Importar extrato</a
                >
              </div>
            }
          </li>
        </ol>
        @if (erro()) {
          <p class="aviso erro" role="alert">{{ erro() }}</p>
        }
        <button i18n type="button" class="botao fantasma pequeno" (click)="pular()">
          Pular o guia
        </button>
      </section>
    }
  `,
  styles: `
    .passos {
      list-style: none;
      margin: 0 0 0.6rem;
      padding: 0;
    }
    .passos li {
      padding: 0.6rem 0;
      border-bottom: 1px solid var(--border);
    }
    .passos li.feito .linha-titulo {
      color: var(--muted);
      text-decoration: line-through;
    }
    .passos .linha-titulo {
      margin-bottom: 0.4rem;
    }
  `,
})
export class PrimeirosPassos {
  readonly temLimite = input(false);
  readonly temGasto = input(false);

  protected readonly moeda = inject(MoedaService);
  private readonly auth = inject(AuthService);
  private readonly users = inject(UsersService);

  private readonly chave = `piggu_guia_${this.auth.usuario()?.id ?? ''}`;
  private readonly estado = signal<EstadoDoGuia>(lerEstado(this.chave));
  protected readonly moedaEscolhida = signal(this.moeda.codigo());
  protected readonly moedas = computed(() => [
    ...new Set([this.moeda.codigo(), ...MOEDAS_DO_GUIA]),
  ]);
  protected readonly salvando = signal(false);
  protected readonly erro = signal('');

  protected readonly moedaFeita = computed(() => !!this.estado().moeda);
  protected readonly feitos = computed(
    () => [this.moedaFeita(), this.temLimite(), this.temGasto()].filter(Boolean).length,
  );
  protected readonly visivel = computed(
    () => !this.estado().fechado && !(this.temLimite() && this.temGasto()),
  );

  protected nomeDaMoeda(codigo: string): string {
    try {
      const nome = new Intl.DisplayNames(['pt-BR'], { type: 'currency' }).of(codigo);
      return nome ? `${nome} (${codigo})` : codigo;
    } catch {
      return codigo;
    }
  }

  protected confirmarMoeda(): void {
    const preferencias = this.auth.usuario()?.preferencias;
    this.salvando.set(true);
    this.erro.set('');
    this.users
      .salvarPreferencias({
        moeda: this.moedaEscolhida(),
        moedaConversao: preferencias?.moedaConversao ?? 'BRL',
        mostrarCotacao: preferencias?.mostrarCotacao ?? true,
      })
      .subscribe({
        next: (usuario) => {
          this.auth.atualizarUsuario(usuario);
          this.guardar({ moeda: true });
          this.salvando.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }

  protected pular(): void {
    this.guardar({ fechado: true });
  }

  private guardar(mudanca: EstadoDoGuia): void {
    this.estado.update((atual) => ({ ...atual, ...mudanca }));
    try {
      localStorage.setItem(this.chave, JSON.stringify(this.estado()));
    } catch {
      // Sem armazenamento (aba privada): vale so ate recarregar.
    }
  }
}

function lerEstado(chave: string): EstadoDoGuia {
  try {
    return JSON.parse(localStorage.getItem(chave) ?? '{}') as EstadoDoGuia;
  } catch {
    return {};
  }
}
