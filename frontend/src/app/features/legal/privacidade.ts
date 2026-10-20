import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { CONTATO_PRIVACIDADE, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';
import { ConsentimentoDeCookies } from '../../core/privacidade/consentimento-de-cookies';

/** Aviso de privacidade: aberto, sem login, linkado no login e onde se pede consentimento. */
@Component({
  selector: 'app-privacidade',
  imports: [RouterLink],
  templateUrl: './privacidade.html',
  styleUrl: './legal.scss',
})
export class Privacidade {
  protected readonly versao = VERSAO_DO_AVISO;
  protected readonly contato = CONTATO_PRIVACIDADE;
  protected readonly cookies = inject(ConsentimentoDeCookies);
  /** No app da loja nao ha banner de cookies para reabrir. */
  protected readonly naWeb = !Capacitor.isNativePlatform();
}
