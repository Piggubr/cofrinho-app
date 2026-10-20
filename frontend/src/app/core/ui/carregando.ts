import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Icone } from './icone';

/** Depois disso a tela avisa que a conexao parece lenta. */
export const ESPERA_LENTA_MS = 4000;
/** Depois disso oferece recarregar, para quem ficou preso numa rede que caiu no meio. */
export const ESPERA_MUITO_LENTA_MS = 15000;

/**
 * Estado de carregamento das telas.
 *
 * <p>No lugar do "Carregando..." solto, desenha o formato do que vem (linhas de lista,
 * cartoes ou paragrafo) em blocos cinza que brilham, para a tela nao pular quando os
 * dados chegam. Ele so aparece depois de um instante, entao em conexao boa nem pisca.</p>
 *
 * <p>Em conexao lenta conta o que esta acontecendo: passados alguns segundos (ou logo de
 * cara, se o navegador ja diz que a rede e 2G ou que a pessoa pediu economia de dados)
 * avisa que a conexao esta lenta, e mais adiante oferece recarregar.</p>
 */
@Component({
  selector: 'app-carregando',
  imports: [Icone],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { role: 'status', 'aria-live': 'polite', '[class]': "'formato-' + formato()" },
  template: `
    <span class="so-leitor">{{ texto() || textoPadrao }}</span>

    <div class="esqueleto" aria-hidden="true">
      @for (i of contagem(); track i) {
        @switch (formato()) {
          @case ('cartoes') {
            <div class="bloco cartao-falso">
              <span class="barra curta"></span>
              <span class="barra grande"></span>
              <span class="barra longa"></span>
            </div>
          }
          @case ('texto') {
            <span class="barra" [style.width.%]="i % 3 === 2 ? 60 : 100"></span>
          }
          @default {
            <div class="linha-falsa">
              <span class="bloco bolinha"></span>
              <span class="textos">
                <span class="barra" [style.width.%]="i % 2 ? 48 : 66"></span>
                <span class="barra fina" [style.width.%]="i % 2 ? 30 : 38"></span>
              </span>
              <span class="barra valor"></span>
            </div>
          }
        }
      }
    </div>

    @if (lento()) {
      <div class="lento">
        <p>
          <app-icone nome="alerta" [tamanho]="16" />
          <span i18n
            >Sua conexão parece lenta. O Piggu continua carregando, só mais um pouquinho.</span
          >
        </p>
        @if (muitoLento()) {
          <button i18n type="button" class="botao contorno pequeno" (click)="recarregar()">
            Recarregar a página
          </button>
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      padding: 0.5rem 0;
      animation: aparece 0.3s ease-out 0.15s backwards;
    }

    .so-leitor {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
    }

    .esqueleto {
      display: grid;
      gap: 0.9rem;
    }

    :host(.formato-cartoes) .esqueleto {
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    }

    :host(.formato-texto) .esqueleto {
      gap: 0.55rem;
    }

    .barra,
    .bloco {
      display: block;
      border-radius: 999px;
      background: linear-gradient(
        90deg,
        var(--track) 0%,
        var(--surface-sunken) 45%,
        var(--track) 90%
      );
      background-size: 300% 100%;
      animation: brilha 1.4s ease-in-out infinite;
    }

    .barra {
      height: 12px;
    }

    .barra.fina {
      height: 9px;
    }

    .linha-falsa {
      display: flex;
      align-items: center;
      gap: 0.8rem;
    }

    .bolinha {
      width: 38px;
      height: 38px;
      flex-shrink: 0;
      border-radius: var(--raio-pequeno);
    }

    .textos {
      display: grid;
      flex: 1;
      gap: 0.45rem;
    }

    .valor {
      width: 64px;
      flex-shrink: 0;
    }

    .cartao-falso {
      display: grid;
      gap: 0.7rem;
      padding: 1.1rem;
      border-radius: var(--raio);
      background: none;
      border: 1px solid var(--border);
      animation: none;
    }

    .cartao-falso .curta {
      width: 40%;
    }

    .cartao-falso .grande {
      width: 70%;
      height: 22px;
    }

    .lento {
      display: grid;
      justify-items: center;
      gap: 0.6rem;
      margin-top: 1.1rem;
      text-align: center;
      animation: aparece 0.3s ease-out;
    }

    .lento p {
      max-width: 340px;
      margin: 0;
      color: var(--ink-soft);
      font-size: 0.85rem;
    }

    .lento app-icone {
      margin-right: 0.35rem;
      vertical-align: -3px;
      color: var(--ouro-ink);
    }

    @keyframes brilha {
      from {
        background-position: 100% 0;
      }
      to {
        background-position: 0 0;
      }
    }

    @keyframes aparece {
      from {
        opacity: 0;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host,
      .barra,
      .bloco,
      .lento {
        animation: none;
      }
    }
  `,
})
export class Carregando {
  /** Formato do que esta para chegar: linhas de lista, cartoes ou um paragrafo. */
  readonly formato = input<'lista' | 'cartoes' | 'texto'>('lista');
  readonly linhas = input(3);
  /** O que o leitor de tela anuncia. */
  readonly texto = input('');

  protected readonly textoPadrao = $localize`Carregando…`;
  protected readonly lento = signal(conexaoLenta());
  protected readonly muitoLento = signal(false);

  protected readonly contagem = computed(() =>
    Array.from({ length: Math.max(1, this.linhas()) }, (_, i) => i),
  );

  constructor() {
    const avisar = setTimeout(() => this.lento.set(true), ESPERA_LENTA_MS);
    const oferecer = setTimeout(() => this.muitoLento.set(true), ESPERA_MUITO_LENTA_MS);
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(avisar);
      clearTimeout(oferecer);
    });
  }

  protected recarregar(): void {
    location.reload();
  }
}

/** O navegador ja sabe que a rede e lenta? (Chrome e Android; Safari nao informa.) */
export function conexaoLenta(): boolean {
  const conexao = (
    navigator as Navigator & { connection?: { effectiveType?: string; saveData?: boolean } }
  ).connection;
  return !!conexao && (conexao.saveData === true || /(^|-)2g$/.test(conexao.effectiveType ?? ''));
}
