import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FinanceService } from '../../core/api/finance.service';
import { Cotacao } from '../../core/api/models';

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
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);

  protected readonly menuAberto = signal(false);
  protected readonly cotacao = signal<Cotacao | null>(null);

  /** O perfil familiar so enxerga o painel; o resto do menu some para ele. */
  protected readonly itens: ItemDeMenu[] = [
    { rota: '/painel', rotulo: 'Painel', icone: '🏠' },
    { rota: '/gastos', rotulo: 'Gastos', icone: '💸', somenteCompleto: true },
    { rota: '/calendario', rotulo: 'Calendário', icone: '📅', somenteCompleto: true },
    { rota: '/compras', rotulo: 'Compras', icone: '🛒', somenteCompleto: true },
    { rota: '/lugares', rotulo: 'Lugares', icone: '📍', somenteCompleto: true },
    { rota: '/filmes', rotulo: 'Filmes', icone: '🎬', somenteCompleto: true },
    { rota: '/feed', rotulo: 'Fotos', icone: '📸', somenteCompleto: true },
    { rota: '/premios', rotulo: 'Prêmios', icone: '🏆', somenteCompleto: true },
    { rota: '/metas', rotulo: 'Metas', icone: '🎯', somenteCompleto: true },
  ];

  constructor() {
    this.finance.consultarCotacao().subscribe({
      next: (cotacao) => this.cotacao.set(cotacao),
      // A cotacao e informativa: sem ela o app segue normalmente.
      error: () => this.cotacao.set(null),
    });
  }

  protected itensVisiveis(): ItemDeMenu[] {
    return this.auth.ehFamiliar() ? this.itens.filter((item) => !item.somenteCompleto) : this.itens;
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

  protected taxaFormatada(): string {
    const taxa = this.cotacao()?.taxa;
    return taxa ? `R$ ${taxa.toFixed(2).replace('.', ',')}` : '';
  }
}
