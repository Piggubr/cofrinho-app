import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { FinanceService } from '../../core/api/finance.service';
import { Cotacao, ModuloDeEstiloDeVida } from '../../core/api/models';
import { AbaNativa, AbasNativas } from '../../core/nativo/abas-nativas';
import { Icone, NomeDoIcone } from '../../core/ui/icone';
import { formatadorDe } from '../../core/ui/moeda';
import { MODO_DEMO } from '../../demo/modo-demo';
import { BuscaGlobal } from './busca-global';

type Grupo = 'dia-a-dia' | 'planejar' | 'contas' | 'estilo' | 'conta';

interface ItemDeMenu {
  readonly rota: string;
  readonly rotulo: string;
  readonly icone: NomeDoIcone;
  readonly grupo: Grupo;
  /** Tela de estilo de vida: so aparece com o modulo ligado no perfil. */
  readonly modulo?: ModuloDeEstiloDeVida;
  /** Quando ausente, o item aparece para todos os perfis. */
  readonly somenteCompleto?: boolean;
  /** Assinar e cancelar o Premium e so do titular; o parceiro nao ve o item. */
  readonly somenteTitular?: boolean;
}

/** SF Symbols dos atalhos na barra nativa do iOS. */
const SIMBOLOS: Record<string, string> = {
  '/painel': 'house',
  '/gastos': 'creditcard',
  '/relatorios': 'chart.bar',
  '/planejar': 'target',
  '/familia': 'person.2',
};

const GRUPOS: { readonly id: Grupo; readonly titulo: string }[] = [
  { id: 'dia-a-dia', titulo: $localize`Dia a dia` },
  { id: 'planejar', titulo: $localize`Planejar` },
  { id: 'contas', titulo: $localize`Cartões e contas` },
  { id: 'estilo', titulo: $localize`Estilo de vida` },
  { id: 'conta', titulo: $localize`Família e plano` },
];

export interface GrupoDeMenu {
  readonly id: Grupo;
  readonly titulo: string;
  readonly itens: ItemDeMenu[];
}

/**
 * Moldura do app: cabecalho, menu e area de conteudo.
 *
 * <p>No computador o menu e uma coluna fixa a esquerda, em grupos. No celular ele vira
 * uma barra de cinco abas embaixo (Painel, Gastos, Relatorios, Planejar e Mais), com o
 * botao "+" de lancar gasto flutuando acima dela em todas as telas. O "Mais" abre uma
 * folha com as demais telas. As de estilo de vida (compras, lugares, filmes, fotos e
 * premios) so aparecem quando a pessoa as liga no perfil.</p>
 *
 * <p>O app original tinha onze abas em um unico HTML, trocadas por classe CSS.
 * Aqui cada uma virou rota, o que traz historico de navegacao, link direto para
 * uma tela e carregamento sob demanda.</p>
 *
 * <p>No app iOS a barra de abas HTML da lugar a um UITabBar nativo (ver {@link AbasNativas}),
 * que ganha o Liquid Glass do iOS 26. Os atalhos e o "Mais" sao os mesmos.</p>
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BuscaGlobal, Icone],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  host: { '[class.abas-nativas]': 'usaAbasNativas' },
})
export class Shell {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly abasNativas = inject(AbasNativas);

  protected readonly modoDemo = MODO_DEMO;
  protected readonly menuAberto = signal(false);
  protected readonly cotacao = signal<Cotacao | null>(null);
  protected readonly usaAbasNativas = this.abasNativas.disponivel;
  private readonly rotaAtual = signal(this.router.url);

  /** O membro da familia so enxerga o painel; o resto do menu some para ele. */
  // prettier-ignore
  protected readonly itens: ItemDeMenu[] = [
    { rota: '/painel', rotulo: $localize`Painel`, icone: 'casa', grupo: 'dia-a-dia' },
    { rota: '/gastos', rotulo: $localize`Gastos`, icone: 'carteira', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/receitas', rotulo: $localize`Receitas`, icone: 'entrada', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/relatorios', rotulo: $localize`Relatórios`, icone: 'grafico', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/planejar', rotulo: $localize`Planejar`, icone: 'alvo', grupo: 'planejar', somenteCompleto: true },
    { rota: '/orcamentos', rotulo: $localize`Orçamentos`, icone: 'pizza', grupo: 'planejar', somenteCompleto: true },
    { rota: '/metas', rotulo: $localize`Metas`, icone: 'alvo', grupo: 'planejar', somenteCompleto: true },
    { rota: '/contas-fixas', rotulo: $localize`Contas fixas`, icone: 'recibo', grupo: 'planejar', somenteCompleto: true },
    { rota: '/contas-e-cartoes', rotulo: $localize`Contas e cartões`, icone: 'cartao', grupo: 'contas', somenteCompleto: true },
    { rota: '/calendario', rotulo: $localize`Calendário`, icone: 'calendario', grupo: 'contas', somenteCompleto: true },
    { rota: '/compras', rotulo: $localize`Compras`, icone: 'carrinho', grupo: 'estilo', modulo: 'compras', somenteCompleto: true },
    { rota: '/lugares', rotulo: $localize`Lugares`, icone: 'local', grupo: 'estilo', modulo: 'lugares', somenteCompleto: true },
    { rota: '/filmes', rotulo: $localize`Filmes`, icone: 'filme', grupo: 'estilo', modulo: 'filmes', somenteCompleto: true },
    { rota: '/feed', rotulo: $localize`Fotos`, icone: 'imagem', grupo: 'estilo', modulo: 'fotos', somenteCompleto: true },
    { rota: '/premios', rotulo: $localize`Prêmios`, icone: 'trofeu', grupo: 'estilo', modulo: 'premios', somenteCompleto: true },
    { rota: '/familia', rotulo: $localize`Família`, icone: 'pessoas', grupo: 'conta' },
    { rota: '/plano', rotulo: $localize`Premium`, icone: 'estrela', grupo: 'conta', somenteCompleto: true, somenteTitular: true },
  ];

  constructor() {
    // Fecha a folha do "Mais" a cada troca de tela, inclusive pelo voltar do navegador.
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((evento) => {
        this.rotaAtual.set(evento.urlAfterRedirects);
        this.fecharMenu();
      });

    if (this.usaAbasNativas) {
      this.ligarAbasNativas();
    }

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

  /** Atalhos da barra de baixo no celular; o resto fica em "Mais". */
  protected atalhos(): ItemDeMenu[] {
    const rotas = this.auth.ehMembro()
      ? ['/painel', '/familia']
      : ['/painel', '/gastos', '/relatorios', '/planejar'];
    return this.itens.filter((item) => rotas.includes(item.rota));
  }

  /**
   * Os mesmos atalhos da barra HTML, com o "Mais" no fim. O "+" de lancar nao entra: o
   * UITabBar mostra no maximo cinco abas, e o botao flutuante da pagina continua por cima.
   */
  private readonly abasDoNativo = computed<AbaNativa[]>(() => {
    const abas: AbaNativa[] = this.atalhos().map((item) => ({
      id: item.rota,
      titulo: item.rotulo,
      simbolo: SIMBOLOS[item.rota] ?? 'circle',
    }));
    abas.push({ id: 'mais', titulo: $localize`Mais`, simbolo: 'square.grid.2x2' });
    return abas;
  });

  /** "Mais" enquanto a folha esta aberta; senao o atalho da tela atual, se houver. */
  private readonly abaSelecionada = computed(() => {
    if (this.menuAberto()) {
      return 'mais';
    }
    const rota = this.rotaAtual().split(/[?#]/)[0];
    return (
      this.atalhos().find((item) => rota === item.rota || rota.startsWith(item.rota + '/'))?.rota ??
      null
    );
  });

  private ligarAbasNativas(): void {
    // Refaz as abas quando o perfil muda (titular x membro da familia).
    effect(() => {
      const abas = this.abasDoNativo();
      this.abasNativas.configurar(abas, untracked(this.abaSelecionada));
    });
    effect(() => this.abasNativas.selecionar(this.abaSelecionada()));

    const destruir = inject(DestroyRef);
    destruir.onDestroy(this.abasNativas.mostrarSoEmTelaEstreita());
    destruir.onDestroy(this.abasNativas.ouvir((id) => this.tocarAbaNativa(id)));
  }

  /** Mesmo efeito dos cliques na barra HTML. */
  protected tocarAbaNativa(id: string): void {
    if (id === 'mais') {
      this.alternarMenu();
      return;
    }
    this.fecharMenu();
    void this.router.navigateByUrl(id === 'lancar' ? '/gastos' : id);
  }

  protected itensVisiveis(): ItemDeMenu[] {
    if (this.auth.ehMembro()) {
      return this.itens.filter((item) => !item.somenteCompleto);
    }
    // Sem a lista (conta antiga, demo), todos os modulos ficam ligados.
    const modulos = this.auth.usuario()?.preferencias?.modulos;
    return this.itens
      .filter((item) => this.auth.ehTitular() || !item.somenteTitular)
      .filter((item) => !item.modulo || !modulos || modulos.includes(item.modulo));
  }

  /** Menu do computador e folha do "Mais", em grupos; grupo vazio nao aparece. */
  protected gruposVisiveis(): GrupoDeMenu[] {
    const visiveis = this.itensVisiveis();
    return GRUPOS.map((grupo) => ({
      ...grupo,
      itens: visiveis.filter((item) => item.grupo === grupo.id),
    })).filter((grupo) => grupo.itens.length > 0);
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
