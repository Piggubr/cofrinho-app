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
import { Cotacao } from '../../core/api/models';
import { AbaNativa, AbasNativas } from '../../core/nativo/abas-nativas';
import { AvisosNoCelular } from '../../core/nativo/avisos-no-celular';
import { Atalhos } from '../../core/nativo/atalhos';
import { IconesDoApp } from '../../core/nativo/icone-do-app';
import { Temas } from '../../core/ui/tema';
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
  /** Assinar e cancelar o Premium e so do titular; os outros nao veem o item. */
  readonly somenteTitular?: boolean;
}

/** SF Symbols dos atalhos na barra nativa do iOS. */
const SIMBOLOS: Record<string, string> = {
  '/painel': 'house',
  '/gastos': 'creditcard',
  '/receitas': 'arrow.down.circle',
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

/**
 * Ordem das telas na folha do "Mais" do celular, das mais usadas para as menos. As que ja
 * estao na barra de abas nao aparecem ali; tela fora da lista vai para o fim.
 */
const ORDEM_DO_MAIS = [
  '/planejar',
  '/contas-fixas',
  '/orcamentos',
  '/metas',
  '/contas-e-cartoes',
  '/calendario',
  '/compras',
  '/girls-math',
  '/lugares',
  '/filmes',
  '/feed',
  '/premios',
  '/familia',
  '/plano',
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
 * uma barra de cinco abas embaixo (Painel, Gastos, Receitas, Relatorios e Mais), com o
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
    '(window:scroll)': 'aoRolar()',
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
  // Criados aqui para que a cor e o icone do Premium voltem ao padrao logo que a pessoa
  // entra sem o plano, mesmo sem abrir o Perfil.
  private readonly temas = inject(Temas);
  private readonly iconesDoApp = inject(IconesDoApp);
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
  /** Gasto ou receita: a aba com que o painel abre, conforme a opcao do balao. */
  protected readonly tipoDoLancamento = signal<'gasto' | 'receita'>('gasto');
  /** Aviso curto depois de lancar ("Gasto lançado."), ja com o painel fechado. */
  protected readonly avisoDoLancamento = signal('');
  private temporizadorDoAviso?: ReturnType<typeof setTimeout>;
  /**
   * O "+" se recolhe para a direita ao rolar para baixo, deixando uma pontinha na borda,
   * e volta inteiro no alto da tela ou quando a pessoa toca na pontinha.
   */
  protected readonly maisRecolhido = signal(false);
  private ultimaRolagem = 0;
  /** Foto escolhida no balao, entregue ao painel para ja ler o recibo. */
  protected readonly fotoDoRecibo = signal<File | null>(null);
  protected readonly cotacao = signal<Cotacao | null>(null);
  protected readonly usaAbasNativas = this.abasNativas.disponivel;
  private readonly rotaAtual = signal(this.router.url);
  /** Trocas de tela dentro do app: sem nenhuma, o voltar do cabecalho leva ao Painel. */
  private trocasDeTela = 0;

  /** Todo papel ve o menu inteiro; so o Premium e do titular (somenteTitular). */
  // prettier-ignore
  protected readonly itens: ItemDeMenu[] = [
    { rota: '/painel', rotulo: $localize`Painel`, icone: 'casa', grupo: 'dia-a-dia' },
    { rota: '/gastos', rotulo: $localize`Gastos`, icone: 'carteira', grupo: 'dia-a-dia' },
    { rota: '/receitas', rotulo: $localize`Receitas`, icone: 'entrada', grupo: 'dia-a-dia' },
    { rota: '/relatorios', rotulo: $localize`Relatórios`, icone: 'grafico', grupo: 'dia-a-dia' },
    { rota: '/planejar', rotulo: $localize`Planejar`, icone: 'alvo', grupo: 'planejar' },
    { rota: '/metas', rotulo: $localize`Minhas metas`, icone: 'bandeira', grupo: 'planejar' },
    { rota: '/orcamentos', rotulo: $localize`Orçamento`, icone: 'pizza', grupo: 'planejar' },
    { rota: '/contas-fixas', rotulo: $localize`Contas do mês`, icone: 'recibo', grupo: 'planejar' },
    { rota: '/contas-e-cartoes', rotulo: $localize`Cartões e bancos`, icone: 'cartao', grupo: 'contas' },
    { rota: '/calendario', rotulo: $localize`Calendário`, icone: 'calendario', grupo: 'contas' },
    { rota: '/compras', rotulo: $localize`Compras`, icone: 'carrinho', grupo: 'estilo' },
    { rota: '/lugares', rotulo: $localize`Lugares`, icone: 'local', grupo: 'estilo' },
    { rota: '/filmes', rotulo: $localize`Filmes`, icone: 'filme', grupo: 'estilo' },
    { rota: '/feed', rotulo: $localize`Fotos`, icone: 'imagem', grupo: 'estilo' },
    { rota: '/premios', rotulo: $localize`Prêmios`, icone: 'trofeu', grupo: 'estilo' },
    { rota: '/girls-math', rotulo: $localize`Girls Math`, icone: 'sacola', grupo: 'estilo' },
    { rota: '/familia', rotulo: $localize`Família`, icone: 'pessoas', grupo: 'conta' },
    { rota: '/plano', rotulo: $localize`Premium`, icone: 'estrela', grupo: 'conta', somenteTitular: true },
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
    // O par da cotacao e escolhido a parte: trocar a moeda do app nao mexe nele.
    effect((onCleanup) => {
      const preferencias = this.auth.usuario()?.preferencias;
      const de = preferencias?.moedaCotacao ?? preferencias?.moeda;
      if (!preferencias?.mostrarCotacao || !de || de === preferencias.moedaConversao) {
        this.cotacao.set(null);
        return;
      }
      const pedido = this.finance
        .consultarCotacao(de, preferencias.moedaConversao)
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
    const rotas = ['/painel', '/gastos', '/receitas', '/relatorios'];
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
    return this.itens.filter((item) => this.auth.ehTitular() || !item.somenteTitular);
  }

  /** Menu do computador e folha do "Mais", em grupos; grupo vazio nao aparece. */
  protected gruposVisiveis(): GrupoDeMenu[] {
    const visiveis = this.itensVisiveis();
    return GRUPOS.map((grupo) => ({
      ...grupo,
      itens: visiveis.filter((item) => item.grupo === grupo.id),
    })).filter((grupo) => grupo.itens.length > 0);
  }

  /** Tela que ja tem aba embaixo: no celular ela sai da folha do "Mais". */
  protected naBarraDeAbas(rota: string): boolean {
    return this.atalhos().some((item) => item.rota === rota);
  }

  protected ordemNoMais(rota: string): number {
    const posicao = ORDEM_DO_MAIS.indexOf(rota);
    return posicao < 0 ? ORDEM_DO_MAIS.length : posicao;
  }

  protected alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenu(): void {
    this.menuAberto.set(false);
  }

  /**
   * Arraste da folha do "Mais" pelo topo (onde fica a alca), so no celular. Para baixo a
   * folha acompanha o dedo e, passado um terco da altura ou num gesto rapido, fecha; para
   * cima ela cede um pouco, com resistencia, e volta ao lugar ao soltar.
   */
  protected readonly arrasteDoMais = signal<number | null>(null);
  private inicioDoArraste: { y: number; tempo: number; altura: number } | null = null;

  protected comecarArraste(evento: PointerEvent): void {
    const folha = evento.currentTarget as HTMLElement;
    const naGrade = !matchMedia('(max-width: 899px)').matches;
    if (naGrade || !this.menuAberto() || (evento.target as HTMLElement).closest('button')) {
      return;
    }
    folha.setPointerCapture?.(evento.pointerId);
    this.inicioDoArraste = {
      y: evento.clientY,
      tempo: evento.timeStamp,
      altura: folha.closest('nav')?.getBoundingClientRect().height ?? 400,
    };
    this.arrasteDoMais.set(0);
  }

  protected arrastar(evento: PointerEvent): void {
    if (!this.inicioDoArraste) {
      return;
    }
    const dy = evento.clientY - this.inicioDoArraste.y;
    // Para cima: cresce cada vez menos e para em 24px, so para mostrar que nao estica.
    this.arrasteDoMais.set(dy >= 0 ? dy : -24 * (1 - Math.exp(dy / 80)));
  }

  protected soltarArraste(evento: PointerEvent): void {
    const inicio = this.inicioDoArraste;
    if (!inicio) {
      return;
    }
    this.inicioDoArraste = null;
    const dy = evento.clientY - inicio.y;
    const velocidade = dy / Math.max(evento.timeStamp - inicio.tempo, 1);
    this.arrasteDoMais.set(null);
    if (dy > inicio.altura / 3 || (dy > 24 && velocidade > 0.5)) {
      this.fecharMenu();
    }
  }

  /** O fundo escuro clareia conforme a folha desce. */
  protected opacidadeDoFundo(): number | null {
    const dy = this.arrasteDoMais();
    return dy && dy > 0 ? Math.max(0, 1 - dy / 400) : null;
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
    // Tocar na pontinha do "+" recolhido traz o botao de volta e ja abre o balao.
    this.maisRecolhido.set(false);
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

  protected abrirLancamento(tipo: 'gasto' | 'receita' = 'gasto'): void {
    this.fecharMenu();
    this.fecharOpcoes();
    this.fotoDoRecibo.set(null);
    this.tipoDoLancamento.set(tipo);
    this.lancamentoAberto.set(true);
  }

  /** Lancou: o painel fecha e um aviso curto confirma. */
  protected aoLancar(mensagem: string): void {
    this.fecharLancamento();
    this.avisoDoLancamento.set(mensagem);
    clearTimeout(this.temporizadorDoAviso);
    this.temporizadorDoAviso = setTimeout(() => this.avisoDoLancamento.set(''), 2500);
  }

  protected aoRolar(): void {
    const y = window.scrollY;
    if (y <= 8) {
      this.maisRecolhido.set(false);
    } else if (y > this.ultimaRolagem + 4 && y > 80 && !this.lancamentoAberto() && !this.opcoesAbertas()) {
      this.maisRecolhido.set(true);
    }
    this.ultimaRolagem = y;
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
    this.tipoDoLancamento.set('gasto');
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

  /**
   * "1 USD = R$ 5,00": uma unidade da moeda mais forte do par, escrita na mais fraca,
   * qualquer que seja a ordem em que a pessoa escolheu as duas.
   */
  protected taxaFormatada(): string {
    const cotacao = this.cotacao();
    if (!cotacao?.taxa) {
      return '';
    }
    return cotacao.taxa >= 1
      ? `1 ${cotacao.de} = ${formatadorDe(cotacao.para).format(cotacao.taxa)}`
      : `1 ${cotacao.para} = ${formatadorDe(cotacao.de).format(1 / cotacao.taxa)}`;
  }
}
