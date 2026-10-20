import { Component, DestroyRef, afterNextRender, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationCancel,
  NavigationCancellationCode,
  NavigationEnd,
  NavigationError,
  NavigationSkipped,
  NavigationStart,
  Router,
  RouterOutlet,
} from '@angular/router';
import { AvisoDeCookies } from './core/privacidade/aviso-de-cookies';
import { levarAoErro } from './core/ui/levar-ao-erro';

/**
 * Raiz do app: a tela atual e o que se ve enquanto ela abre.
 *
 * <p>Na primeira abertura, ate a primeira tela ficar pronta (sessao restaurada e arquivo
 * da tela baixado), fica a mesma abertura que o index.html mostra antes do app carregar,
 * entao em conexao lenta a pessoa nunca ve tela branca. Depois, a troca de tela que
 * demora ganha uma barrinha no alto e, se passar de alguns segundos, o aviso de conexao
 * lenta. Os dois avisos aparecem por atraso de animacao (styles.scss), sem relogio aqui.</p>
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AvisoDeCookies],
  template: `
    @if (abrindo()) {
      <div class="abertura" role="status" [style.animation-delay]="atrasoDaAbertura">
        <img class="abertura-icone" src="icons/icon-192.png" alt="" width="88" height="88" />
        <p i18n class="abertura-texto">Abrindo o Piggu…</p>
        <p i18n class="abertura-lenta" [style.animation-delay]="atrasoDoAviso">
          Sua conexão parece lenta. Já já abre.
        </p>
      </div>
    } @else if (navegando()) {
      <div i18n-aria-label class="navegacao" role="progressbar" aria-label="Abrindo a tela">
        <span class="navegacao-barra"></span>
        <span i18n class="navegacao-lenta" aria-hidden="true">Conexão lenta, abrindo a tela…</span>
      </div>
    }
    <router-outlet />
    <app-aviso-de-cookies />
  `,
})
export class App {
  protected readonly abrindo = signal(true);
  protected readonly navegando = signal(false);

  /**
   * Atrasos que faltam, contados desde que a pagina comecou a carregar: a abertura do
   * index.html ja estava na tela, e recomecar a animacao do zero faria ela piscar.
   */
  protected readonly atrasoDaAbertura = atrasoRestante(250);
  protected readonly atrasoDoAviso = atrasoRestante(5000);

  constructor() {
    const destruir = inject(DestroyRef);
    // Erro de formulario fora da vista: rola ate ele, em todas as telas (ver levarAoErro).
    afterNextRender(() => destruir.onDestroy(levarAoErro(document.body)));

    inject(Router)
      .events.pipe(takeUntilDestroyed())
      .subscribe((evento) => {
        if (evento instanceof NavigationStart) {
          this.navegando.set(true);
          return;
        }
        // Redirecionamento (guard mandando para o login, por exemplo) nao encerra: a
        // navegacao seguinte ja esta a caminho.
        const terminou =
          evento instanceof NavigationEnd ||
          evento instanceof NavigationError ||
          evento instanceof NavigationSkipped ||
          (evento instanceof NavigationCancel &&
            evento.code !== NavigationCancellationCode.Redirect);
        if (terminou) {
          this.navegando.set(false);
          this.abrindo.set(false);
        }
      });
  }
}

function atrasoRestante(ms: number): string {
  return `${Math.max(0, Math.round(ms - performance.now()))}ms`;
}
