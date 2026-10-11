import { Injectable, computed, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { NomeDoIcone } from './icone';

export interface PassoDoTutorial {
  readonly id: string;
  readonly icone: NomeDoIcone;
  readonly titulo: string;
  readonly texto: string;
  /**
   * Valores de `data-tour` do botao ou aba para onde o balao aponta, em ordem de
   * preferencia: vale o primeiro que estiver na tela (a aba no celular, o item do menu
   * no computador, o "Mais" quando a tela fica dentro dele). Sem alvo visivel, o balao
   * fica no centro.
   */
  readonly alvos?: readonly string[];
}

// prettier-ignore
const INICIO: PassoDoTutorial = { id: 'inicio', icone: 'cofrinho', titulo: $localize`Bem-vindo ao Piggu`, texto: $localize`Um tour rápido pelas funções principais. Leva menos de um minuto.` };
// prettier-ignore
const COMPLETO: PassoDoTutorial[] = [
  INICIO,
  { id: 'lancar', icone: 'mais', alvos: ['lancar'], titulo: $localize`Lance pelo +`, texto: $localize`Gasto ou receita, de qualquer tela, sem sair de onde está. Dá para tirar foto da nota e o Piggu preenche para você.` },
  { id: 'painel', icone: 'casa', alvos: ['/painel'], titulo: $localize`Painel`, texto: $localize`O resumo do mês: quanto entrou, quanto saiu, quanto ainda dá para gastar e as contas que vencem.` },
  { id: 'planejar', icone: 'alvo', alvos: ['/planejar', 'mais'], titulo: $localize`Planejar`, texto: $localize`Defina quanto quer gastar por categoria, cadastre as contas do mês e acompanhe seus objetivos.` },
  { id: 'relatorios', icone: 'grafico', alvos: ['/relatorios', 'mais'], titulo: $localize`Relatórios`, texto: $localize`Veja para onde o dinheiro foi, mês a mês e por categoria.` },
  { id: 'familia', icone: 'pessoas', alvos: ['/familia', 'mais'], titulo: $localize`Família`, texto: $localize`Convide quem divide as contas com você. Cada um lança do seu celular e tudo aparece junto.` },
  { id: 'busca', icone: 'busca', alvos: ['busca'], titulo: $localize`Busca`, texto: $localize`Encontre qualquer tela ou lançamento pela lupa.` },
  { id: 'assistente', icone: 'brilho', alvos: ['assistente'], titulo: $localize`Assistente`, texto: $localize`Pergunte sobre o seu dinheiro, como "quanto gastei este mês?", e ele responde.` },
];
// prettier-ignore
const MEMBRO: PassoDoTutorial[] = [
  INICIO,
  { id: 'painel', icone: 'casa', alvos: ['/painel'], titulo: $localize`Painel`, texto: $localize`O resumo do mês da família: quanto entrou, quanto saiu e o que ainda dá para gastar.` },
  { id: 'familia', icone: 'pessoas', alvos: ['/familia', 'mais'], titulo: $localize`Família`, texto: $localize`Veja quem faz parte da família no Piggu.` },
];

/**
 * Tour das funcoes principais, mostrado no primeiro acesso.
 *
 * <p>Fica lembrado por pessoa neste aparelho, como o guia de primeiros passos do painel.
 * O Perfil tem um botao para ver de novo.</p>
 */
@Injectable({ providedIn: 'root' })
export class Tutorial {
  private readonly auth = inject(AuthService);

  readonly aberto = signal(false);
  readonly indice = signal(0);

  readonly passos = computed(() => {
    if (this.auth.ehMembro()) {
      return MEMBRO;
    }
    // O assistente so aparece no topo para quem pode lancar.
    return this.auth.podeLancar()
      ? COMPLETO
      : COMPLETO.filter((passo) => passo.id !== 'assistente');
  });
  readonly passo = computed(() => this.passos()[this.indice()]);
  readonly ultimo = computed(() => this.indice() === this.passos().length - 1);

  /** Abre so se a pessoa ainda nao viu (nem pulou) o tour neste aparelho. */
  abrirSeNovo(): void {
    if (!this.visto()) {
      this.abrir();
    }
  }

  abrir(): void {
    this.indice.set(0);
    this.aberto.set(true);
  }

  avancar(): void {
    if (this.ultimo()) {
      this.concluir();
    } else {
      this.indice.update((i) => i + 1);
    }
  }

  voltar(): void {
    this.indice.update((i) => Math.max(0, i - 1));
  }

  /** Fecha e lembra, seja no fim ou pelo "Pular". */
  concluir(): void {
    this.aberto.set(false);
    try {
      localStorage.setItem(this.chave(), '1');
    } catch {
      // Sem armazenamento (aba privada): o tour volta na proxima visita.
    }
  }

  private visto(): boolean {
    try {
      return localStorage.getItem(this.chave()) === '1';
    } catch {
      return false;
    }
  }

  private chave(): string {
    return `piggu_tutorial_${this.auth.usuario()?.id ?? ''}`;
  }
}
