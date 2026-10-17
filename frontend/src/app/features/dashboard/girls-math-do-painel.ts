import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { ItemDoGirlsMath } from '../../core/api/models';
import { mesKey } from '../../core/ui/datas';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { totaisDoGirlsMath } from '../girls-math/girls-math';

/** Placar do Girls Math no Painel: quanto a pessoa deixou de gastar este mes. */
@Component({
  selector: 'app-girls-math-do-painel',
  imports: [RouterLink, Icone, MoedaPipe],
  template: `
    <section class="cartao" aria-labelledby="titulo-gm-painel">
      <div class="cartao-titulo">
        <h2 i18n id="titulo-gm-painel">Girls Math</h2>
        <app-icone nome="sacola" [tamanho]="20" />
      </div>
      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      } @else {
        <p i18n class="linha-detalhe">Você deixou de gastar este mês</p>
        <p class="valor">{{ totais().noMes | moeda }}</p>
      }
      <div class="acoes">
        <a i18n class="botao contorno pequeno" routerLink="/girls-math"
          >Registrar o que não comprei</a
        >
      </div>
    </section>
  `,
  styles: `
    .cartao-titulo app-icone {
      color: var(--rose-deep);
    }
    .linha-detalhe {
      margin: 0;
    }
    .valor {
      margin: 0.15rem 0 0.4rem;
      color: var(--primaria);
      font-size: 1.6rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
  `,
})
export class GirlsMathDoPainel {
  private readonly lifestyle = inject(LifestyleService);

  protected readonly itens = signal<ItemDoGirlsMath[]>([]);
  protected readonly erro = signal('');
  protected readonly totais = computed(() => totaisDoGirlsMath(this.itens(), mesKey(new Date())));

  constructor() {
    this.lifestyle.listarGirlsMath().subscribe({
      next: (itens) => this.itens.set(itens),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
