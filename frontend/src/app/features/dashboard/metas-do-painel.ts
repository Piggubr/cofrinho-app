import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { MetaDoMes } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { AnelDasMetas } from '../minhas-metas/anel-das-metas';
import { percentualConcluido } from '../minhas-metas/minhas-metas';

/** Minhas metas no Painel: o anel com o porquinho e o atalho para a tela completa. */
@Component({
  selector: 'app-metas-do-painel',
  imports: [RouterLink, AnelDasMetas],
  template: `
    <section class="cartao" aria-labelledby="titulo-metas-painel">
      <div class="conteudo">
        <app-anel-das-metas [percentual]="percentual()" [pequeno]="true" />
        <div class="texto">
          <p i18n class="sobretitulo">Seu ritmo</p>
          <h2 i18n id="titulo-metas-painel">Metas do mês</h2>
          @if (erro()) {
            <p class="linha-detalhe" role="alert">{{ erro() }}</p>
          } @else if (metas().length) {
            <p i18n class="linha-detalhe">{{ concluidas() }} de {{ metas().length }} concluídas</p>
          } @else {
            <p i18n class="linha-detalhe">Crie sua primeira meta para começar a jornada.</p>
          }
          <a i18n class="botao contorno pequeno" routerLink="/metas">Ver metas</a>
        </div>
      </div>
    </section>
  `,
  styles: `
    .conteudo {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 1.1rem;
      align-items: center;
    }
    .texto p,
    .texto h2 {
      margin: 0 0 0.35rem;
    }
    .texto .botao {
      margin-top: 0.35rem;
    }
    .sobretitulo {
      color: var(--rose-deep);
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
  `,
})
export class MetasDoPainel {
  private readonly lifestyle = inject(LifestyleService);

  protected readonly metas = signal<MetaDoMes[]>([]);
  protected readonly erro = signal('');
  protected readonly concluidas = computed(() => this.metas().filter((m) => m.concluida).length);
  protected readonly percentual = computed(() => percentualConcluido(this.metas()));

  constructor() {
    this.lifestyle.listarMetasDoMes().subscribe({
      next: (metas) => this.metas.set(metas),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
