import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CONTATO_PRIVACIDADE } from '../../core/privacidade/aviso';

/** Como excluir a conta: aberta, sem login, para o link pedido pelo Google Play. */
@Component({
  selector: 'app-excluir-conta',
  imports: [RouterLink],
  templateUrl: './excluir-conta.html',
  styleUrl: './legal.scss',
})
export class ExcluirConta {
  protected readonly contato = CONTATO_PRIVACIDADE;
}
