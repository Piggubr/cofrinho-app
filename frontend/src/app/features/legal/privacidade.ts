import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CONTATO_PRIVACIDADE, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';

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
}
