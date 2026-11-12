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
    /* Celular pequeno: mais baixo, para nao cobrir o "Entrar" ate a pessoa responder. */
    @media (max-width: 480px) {
      .aviso-de-cookies {
        bottom: calc(0.5rem + env(safe-area-inset-bottom, 0px));
        width: calc(100% - 1rem);
        padding: 0.65rem 0.8rem;
      }
      .titulo {
        display: inline;
        margin-right: 0.3rem;
        font-size: 0.85rem;
      }
      .texto {
        display: inline;
        font-size: 0.78rem;
        line-height: 1.4;
      }
      .acoes {
        flex-wrap: nowrap;
        margin-top: 0.5rem;
      }
      .acoes .botao {
        flex: 1 1 0;
        min-height: 40px;
        padding-block: 0.4rem;
        font-size: 0.85rem;
      }
    }
  `,
})
export class AvisoDeCookies {
  protected readonly consentimento = inject(ConsentimentoDeCookies);
}
