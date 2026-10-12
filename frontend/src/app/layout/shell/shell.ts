import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Location } from '@angular/common';
import {
  NavigationEnd,
  NavigationStart,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { FinanceService } from '../../core/api/finance.service';
import { Cotacao, ModuloDeEstiloDeVida } from '../../core/api/models';
import { AbaNativa, AbasNativas } from '../../core/nativo/abas-nativas';
import { AvisosNoCelular } from '../../core/nativo/avisos-no-celular';
import { Atalhos } from '../../core/nativo/atalhos';
import { FilaDeGastos } from '../../core/offline/fila-de-gastos';
import { Icone, NomeDoIcone } from '../../core/ui/icone';
import { formatadorDe } from '../../core/ui/moeda';
import { MODO_DEMO } from '../../demo/modo-demo';
import { BuscaGlobal } from './busca-global';
import { Assistente } from './assistente';
import { LancamentoRapido } from './lancamento-rapido';
import { TutorialDePrimeiroAcesso } from './tutorial';
import { Tutorial } from '../../core/ui/tutorial';
import { toqueNaTela } from '../../core/ui/focar';
import { animarEntrada, caminhoDe, direcaoDaTroca } from './transicao-de-tela';

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
  { id: 'contas', titulo: $localize`Cartões e datas` },
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
 * folha com as demais telas. As de estilo de vida (compras, lugares, filmes, fotos,
 * premios e Girls Math) so aparecem quando a pessoa as liga no perfil.</p>
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BuscaGlobal, Assistente, Icone, LancamentoRapido, TutorialDePrimeiroAcesso],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  host: {
    '[class.abas-nativas]': 'usaAbasNativas',
    '(document:keydown.escape)': 'fecharOpcoes()',
  },
})
export class Shell {
  private readonly finance = inject(FinanceService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly abasNativas = inject(AbasNativas);
  private readonly avisosNoCelular = inject(AvisosNoCelular);
  private readonly filaDeGastos = inject(FilaDeGastos);
  private readonly atalhosDoApp = inject(Atalhos);
  private readonly location = inject(Location);
  private readonly elemento: HTMLElement = inject(ElementRef).nativeElement;
  private readonly injetor = inject(Injector);
  protected readonly tutorial = inject(Tutorial);

  protected readonly modoDemo = MODO_DEMO;
  protected readonly menuAberto = signal(false);
  /** Balao do "+": "Lançar gasto" e "Foto do recibo". */
  protected readonly opcoesAbertas = signal(false);
  /** Painel do "+": lanca gasto ou receita por cima da tela aberta. */
  protected readonly lancamentoAberto = signal(false);
  /** Foto escolhida no balao, entregue ao painel para ja ler o recibo. */
  protected readonly fotoDoRecibo = signal<File | null>(null);
  protected readonly cotacao = signal<Cotacao | null>(null);
  protected readonly usaAbasNativas = this.abasNativas.disponivel;
  private readonly rotaAtual = signal(this.router.url);
  /** Trocas de tela dentro do app: sem nenhuma, o voltar do cabecalho leva ao Painel. */
  private trocasDeTela = 0;

  /** O membro da familia so enxerga o painel; o resto do menu some para ele. */
  // prettier-ignore
  protected readonly itens: ItemDeMenu[] = [
    { rota: '/painel', rotulo: $localize`Painel`, icone: 'casa', grupo: 'dia-a-dia' },
    { rota: '/gastos', rotulo: $localize`Gastos`, icone: 'carteira', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/receitas', rotulo: $localize`Receitas`, icone: 'entrada', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/relatorios', rotulo: $localize`Relatórios`, icone: 'grafico', grupo: 'dia-a-dia', somenteCompleto: true },
    { rota: '/planejar', rotulo: $localize`Planejar`, icone: 'alvo', grupo: 'planejar', somenteCompleto: true },
    { rota: '/metas', rotulo: $localize`Minhas metas`, icone: 'bandeira', grupo: 'planejar' },
    { rota: '/orcamentos', rotulo: $localize`Orçamento`, icone: 'pizza', grupo: 'planejar', somenteCompleto: true },
    { rota: '/contas-fixas', rotulo: $localize`Contas do mês`, icone: 'recibo', grupo: 'planejar', somenteCompleto: true },
    { rota: '/contas-e-cartoes', rotulo: $localize`Cartões e bancos`, icone: 'cartao', grupo: 'contas', somenteCompleto: true },
    { rota: '/calendario', rotulo: $localize`Calendário`, icone: 'calendario', grupo: 'contas', somenteCompleto: true },
    { rota: '/compras', rotulo: $localize`Compras`, icone: 'carrinho', grupo: 'estilo', modulo: 'compras', somenteCompleto: true },
    { rota: '/lugares', rotulo: $localize`Lugares`, icone: 'local', grupo: 'estilo', modulo: 'lugares', somenteCompleto: true },
    { rota: '/filmes', rotulo: $localize`Filmes`, icone: 'filme', grupo: 'estilo', modulo: 'filmes', somenteCompleto: true },
    { rota: '/feed', rotulo: $localize`Fotos`, icone: 'imagem', grupo: 'estilo', modulo: 'fotos', somenteCompleto: true },
    { rota: '/premios', rotulo: $localize`Prêmios`, icone: 'trofeu', grupo: 'estilo', modulo: 'premios', somenteCompleto: true },
    { rota: '/girls-math', rotulo: $localize`Girls Math`, icone: 'sacola', grupo: 'estilo', modulo: 'girls-math' },
    { rota: '/familia', rotulo: $localize`Família`, icone: 'pessoas', grupo: 'conta' },
    { rota: '/plano', rotulo: $localize`Premium`, icone: 'estrela', grupo: 'conta', somenteCompleto: true, somenteTitular: true },
  ];

  constructor() {
    // Fecha a folha do "Mais" a cada troca de tela, inclusive pelo voltar do navegador, e
    // anima a entrada da tela nova para ficar claro que ela mudou.
    let voltando = false;
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationStart || evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((evento) => {
        if (evento instanceof NavigationStart) {
          voltando = evento.navigationTrigger === 'popstate';
          return;
        }
        const anterior = this.rotaAtual();
        this.rotaAtual.set(evento.urlAfterRedirects);
        this.fecharMenu();
        this.fecharOpcoes();
        const direcao = direcaoDaTroca(
          anterior,
          evento.urlAfterRedirects,
          this.atalhos().map((item) => item.rota),
          voltando,
        );
        if (direcao) {
          this.trocasDeTela++;
          const conteudo = this.elemento.querySelector<HTMLElement>('main.conteudo');
          if (conteudo) {
            animarEntrada(conteudo, direcao);
          }
        }
      });

    // Gastos lancados sem sinal vao assim que o app abre com rede (e no evento online).
    void this.filaDeGastos.enviar();

    // "Lançar gasto" pelo icone, pela Siri ou por um link piggu:// (so no app instalado).
    void this.atalhosDoApp.ligar();

    // O token do aparelho muda quando o sistema quer: renova a cada abertura do app.
    if (this.auth.usuario()?.avisos?.push) {
      void this.avisosNoCelular.renovar();
    }

    if (this.usaAbasNativas) {
      this.ligarAbasNativas();
    }

    // Tour das funcoes principais no primeiro acesso. A demo ja abre com dados e a faixa.
    if (!this.modoDemo) {
      this.tutorial.abrirSeNovo();
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

  /**
   * Tela de dentro (Perfil, itens do "Mais"): no celular o cabecalho troca a marca pelo
   * voltar e pelo nome da tela, para a pessoa saber onde esta sem procurar.
   */
  protected readonly telaInterna = computed(() => {
    const rota = caminhoDe(this.rotaAtual());
    return !this.atalhos().some((item) => rota === item.rota || rota.startsWith(item.rota + '/'));
  });

  protected readonly tituloDaTela = computed(() => {
    const rota = caminhoDe(this.rotaAtual());
    if (rota.startsWith('/perfil')) {
      return $localize`Perfil`;
    }
    return this.itens.find((item) => rota === item.rota || rota.startsWith(item.rota + '/'))?.rotulo ?? '';
  });

  /** Volta pelo historico; se a pessoa abriu direto nesta tela, vai para o Painel. */
  protected voltar(): void {
    if (this.trocasDeTela > 0) {
      this.location.back();
      return;
    }
    void this.router.navigateByUrl('/painel');
  }

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
    if (id === 'lancar') {
      this.lancamentoAberto.set(true);
      return;
    }
    void this.router.navigateByUrl(id);
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

  /**
   * O "+" vira um balao com as duas formas de lancar. Com o balao ou o painel aberto, o
   * mesmo botao (agora um "x") fecha.
   */
  protected alternarLancamento(): void {
    // Tocar no "+" destacado pelo tour encerra o tour e ja mostra as opcoes.
    if (this.tutorial.aberto()) {
      this.tutorial.concluir();
    }
    this.fecharMenu();
    if (this.lancamentoAberto() || this.opcoesAbertas()) {
      this.fecharLancamento();
      this.fecharOpcoes();
      return;
    }
    this.opcoesAbertas.set(true);
    // Teclado fisico: o foco vai para a primeira opcao. No toque, nada de anel de foco.
    if (!toqueNaTela()) {
      afterNextRender(
        () => this.elemento.querySelector<HTMLElement>('.opcoes-do-lancar .opcao')?.focus(),
        { injector: this.injetor },
      );
    }
  }

  protected fecharOpcoes(): void {
    this.opcoesAbertas.set(false);
  }

  protected abrirLancamento(): void {
    this.fecharMenu();
    this.fecharOpcoes();
    this.fotoDoRecibo.set(null);
    this.lancamentoAberto.set(true);
  }

  /** "Foto do recibo" no balao: a camera abre direto e o painel ja chega lendo a foto. */
  protected escolherFotoDoRecibo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0] ?? null;
    campo.value = '';
    if (!arquivo) {
      return;
    }
    this.fecharOpcoes();
    this.fotoDoRecibo.set(arquivo);
    this.lancamentoAberto.set(true);
  }

  protected fecharLancamento(): void {
    this.lancamentoAberto.set(false);
    this.fotoDoRecibo.set(null);
  }

  /** "Mais opções" do painel: parcelas, moeda e divisao ficam na tela de Gastos. */
  protected abrirGastos(): void {
    this.lancamentoAberto.set(false);
    void this.router.navigate(['/gastos'], { queryParams: { rapido: 1 } });
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
