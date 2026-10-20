import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConsentimentoDeCookies } from './consentimento-de-cookies';

/** Banner de cookies, no rodape de qualquer tela do site ate a pessoa escolher. */
@Component({
  selector: 'app-aviso-de-cookies',
  imports: [RouterLink],
  template: `
    @if (consentimento.perguntar()) {
      <section class="aviso-de-cookies" role="region" aria-labelledby="titulo-cookies">
        <p id="titulo-cookies" class="titulo" i18n>Cookies no Piggu</p>
        <p class="texto" i18n>
          Usamos um cookie essencial para manter você conectado. Com a sua permissão, também medimos
          como o site é usado, sem anúncios, para deixar o Piggu mais rápido e melhor.
          <a routerLink="/privacidade" fragment="cookies">Saiba mais</a>
        </p>
        <div class="acoes">
          <button type="button" class="botao secundario" (click)="consentimento.escolher(false)" i18n>
            Só os essenciais
          </button>
          <button type="button" class="botao" (click)="consentimento.escolher(true)" i18n>Aceitar</button>
        </div>
      </section>
    }
  `,
  styles: `
    .aviso-de-cookies {
      position: fixed;
      z-index: 1000;
      left: 50%;
      bottom: calc(1rem + env(safe-area-inset-bottom, 0px));
      transform: translateX(-50%);
      width: min(560px, calc(100% - 2rem));
      padding: 1rem 1.1rem;
      border: 1px solid var(--border);
      border-radius: var(--raio);
      background: var(--surface);
      color: var(--ink);
      box-shadow: var(--shadow-float);
    }
    .titulo {
      margin: 0 0 0.25rem;
      font-weight: 600;
    }
    .texto {
      margin: 0 0 0.75rem;
      font-size: 0.88rem;
      line-height: 1.5;
      color: var(--ink-soft);
    }
    .acoes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      justify-content: flex-end;
    }
    .acoes .botao {
      flex: 1 1 10rem;
    }
  `,
})
export class AvisoDeCookies {
  protected readonly consentimento = inject(ConsentimentoDeCookies);
}
