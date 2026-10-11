import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icone, NomeDoIcone } from '../../core/ui/icone';

interface Atalho {
  readonly rota: string;
  readonly titulo: string;
  readonly descricao: string;
  readonly icone: NomeDoIcone;
}

/**
 * Aba "Planejar": o que olha para a frente (quanto gastar e o que vence) num lugar so,
 * em vez de itens soltos no menu.
 */
@Component({
  selector: 'app-plan-hub',
  imports: [RouterLink, Icone],
  templateUrl: './plan-hub.html',
  styleUrl: './plan-hub.scss',
})
export class PlanHub {
  protected readonly atalhos: Atalho[] = [
    {
      rota: '/orcamentos',
      titulo: $localize`Orçamento`,
      descricao: $localize`O limite do mês e, por categoria, com aviso em 80% e 100%.`,
      icone: 'pizza',
    },
    {
      rota: '/contas-fixas',
      titulo: $localize`Contas do mês`,
      descricao: $localize`Aluguel, luz, internet: o que vence todo mês, lançado no dia.`,
      icone: 'recibo',
    },
  ];
}
