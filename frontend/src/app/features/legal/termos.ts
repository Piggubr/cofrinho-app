import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CONTATO_PRIVACIDADE, VERSAO_DO_AVISO } from '../../core/privacidade/aviso';

/** Termos de uso: abertos, sem login. */
@Component({
  selector: 'app-termos',
  imports: [RouterLink],
  templateUrl: './termos.html',
  styleUrl: './legal.scss',
})
export class Termos {
  protected readonly versao = VERSAO_DO_AVISO;
  protected readonly contato = CONTATO_PRIVACIDADE;
}
