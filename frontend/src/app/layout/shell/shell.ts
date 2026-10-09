import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FinanceService } from '../../core/api/finance.service';
import { Cotacao } from '../../core/api/models';
import { formatadorDe } from '../../core/ui/moeda';
import { MODO_DEMO } from '../../demo/modo-demo';
import { BuscaGlobal } from './busca-global';

interface ItemDeMenu {
  readonly rota: string;
  readonly rotulo: string;
  readonly icone: string;
  /** Quando ausente, o item aparece para todos os perfis. */
  readonly somenteCompleto?: boolean;
}

/**
 * Moldura do app: cabecalho, menu e area de conteudo.
 *
 * <p>O app original tinha onze abas em um unico HTML, trocadas por classe CSS.
 * Aqui cada uma virou rota, o que traz historico de navegacao, link direto para
 * uma tela e carregamento sob demanda.</p>
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BuscaGlobal],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);

  protected readonly modoDemo = MODO_DEMO;
  protected readonly menuAberto = signal(false);
  protected readonly cotacao = signal<Cotacao | null>(null);

  /** O membro da familia so enxerga o painel; o resto do menu some para ele. */
  protected readonly itens: ItemDeMenu[] = [
    { rota: '/painel', rotulo: $localize`Painel`, icone: '🏠' },
    { rota: '/gastos', rotulo: $localize`Gastos`, icone: '💸', somenteCompleto: true },
    { rota: '/receitas', rotulo: $localize`Receitas`, icone: '💰', somenteCompleto: true },
    { rota: '/relatorios', rotulo: $localize`Relatórios`, icone: '📈', somenteCompleto: true },
    { rota: '/contas-e-cartoes', rotulo: $localize`Contas e cartões`, icone: '💳', somenteCompleto: true },
    { rota: '/contas-fixas', rotulo: $localize`Contas fixas`, icone: '🧾', somenteCompleto: true },
    { rota: '/orcamentos', rotulo: $localize`Orçamentos`, icone: '📊', somenteCompleto: true },
    { rota: '/calendario', rotulo: $localize`Calendário`, icone: '📅', somenteCompleto: true },
    { rota: '/compras', rotulo: $localize`Compras`, icone: '🛒', somenteCompleto: true },
    { rota: '/lugares', rotulo: $localize`Lugares`, icone: '📍', somenteCompleto: true },
    { rota: '/filmes', rotulo: $localize`Filmes`, icone: '🎬', somenteCompleto: true },
    { rota: '/feed', rotulo: $localize`Fotos`, icone: '📸', somenteCompleto: true },
    { rota: '/premios', rotulo: $localize`Prêmios`, icone: '🏆', somenteCompleto: true },
    { rota: '/metas', rotulo: $localize`Metas`, icone: '🎯', somenteCompleto: true },
    { rota: '/familia', rotulo: $localize`Família`, icone: '👪' },
    { rota: '/plano', rotulo: $localize`Premium`, icone: '⭐', somenteCompleto: true },
  ];

  constructor() {
    // Refaz a consulta quando a pessoa troca as moedas ou liga a cotacao no perfil.
    effect((onCleanup) => {
      const preferencias = this.auth.usuario()?.preferencias;
      if (!preferencias?.mostrarCotacao || preferencias.moeda === preferencias.moedaConversao) {
        this.cotacao.set(null);
        return;
      }
      const pedido = this.finance
        .consultarCotacao(preferencias.moeda, preferencias.moedaConversao)
        .subscribe({
          next: (cotacao) => this.cotacao.set(cotacao),
          // A cotacao e informativa: sem ela o app segue normalmente.
          error: () => this.cotacao.set(null),
        });
      onCleanup(() => pedido.unsubscribe());
    });
  }

  protected itensVisiveis(): ItemDeMenu[] {
    return this.auth.ehMembro() ? this.itens.filter((item) => !item.somenteCompleto) : this.itens;
  }

  protected alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenu(): void {
    this.menuAberto.set(false);
  }

  protected async sair(): Promise<void> {
    await this.auth.sair();
  }

  /** "EUR → R$ 6,15": uma unidade da moeda da pessoa na moeda de conversao. */
  protected taxaFormatada(): string {
    const cotacao = this.cotacao();
    return cotacao?.taxa
      ? `${cotacao.de} → ${formatadorDe(cotacao.para).format(cotacao.taxa)}`
      : '';
  }
}
