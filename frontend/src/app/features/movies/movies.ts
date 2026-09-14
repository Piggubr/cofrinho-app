import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { AuthService } from '../../core/auth/auth.service';
import { Filme, FilmeDoCatalogo } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Generos do TMDB que cabem em um sorteio de noite de filme. */
const GENEROS: { id: string; nome: string }[] = [
  { id: '', nome: 'Qualquer gênero' },
  { id: '28', nome: 'Ação' },
  { id: '35', nome: 'Comédia' },
  { id: '18', nome: 'Drama' },
  { id: '27', nome: 'Terror' },
  { id: '10749', nome: 'Romance' },
  { id: '878', nome: 'Ficção científica' },
  { id: '16', nome: 'Animação' },
  { id: '53', nome: 'Suspense' },
];

/**
 * Lista de filmes do casal.
 *
 * <p>Cada pessoa tem a propria nota de 1 a 5, guardada por e-mail. A media das
 * notas so faz sentido depois que as duas avaliaram, entao a tela mostra as duas
 * separadas em vez de uma media precoce.</p>
 */
@Component({
  selector: 'app-movies',
  imports: [FormsModule],
  templateUrl: './movies.html',
  styleUrl: './movies.scss',
})
export class Movies {
  private readonly lifestyle = inject(LifestyleService);
  private readonly auth = inject(AuthService);

  protected readonly generos = GENEROS;
  protected readonly notasPossiveis = [1, 2, 3, 4, 5];

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly filmes = signal<Filme[]>([]);

  protected readonly busca = signal('');
  protected readonly buscando = signal(false);
  protected readonly resultados = signal<FilmeDoCatalogo[]>([]);

  protected readonly generoEscolhido = signal('');
  protected readonly sorteando = signal(false);
  protected readonly sorteado = signal<FilmeDoCatalogo | null>(null);

  protected readonly paraVer = computed(() => this.filmes().filter((filme) => !filme.assistido));
  protected readonly assistidos = computed(() => this.filmes().filter((filme) => filme.assistido));

  constructor() {
    this.carregar();
  }

  protected minhaNota(filme: Filme): number {
    const email = this.auth.usuario()?.email ?? '';
    return filme.avaliacoes[email] ?? 0;
  }

  protected outrasNotas(filme: Filme): { email: string; nota: number }[] {
    const meu = this.auth.usuario()?.email ?? '';
    return Object.entries(filme.avaliacoes)
      .filter(([email]) => email !== meu)
      .map(([email, nota]) => ({ email, nota }));
  }

  protected buscar(): void {
    const termo = this.busca().trim();
    if (termo.length < 2) {
      this.erro.set('Digite pelo menos duas letras.');
      return;
    }

    this.erro.set('');
    this.buscando.set(true);
    this.lifestyle.buscarFilmes(termo).subscribe({
      next: (filmes) => {
        this.resultados.set(filmes);
        this.buscando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.buscando.set(false);
      },
    });
  }

  protected sortear(): void {
    this.erro.set('');
    this.sorteando.set(true);
    this.lifestyle.sortearFilme(this.generoEscolhido() || undefined).subscribe({
      next: (filme) => {
        this.sorteado.set(filme);
        this.sorteando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.sorteando.set(false);
      },
    });
  }

  protected adicionar(filme: FilmeDoCatalogo): void {
    this.erro.set('');
    this.lifestyle.adicionarFilme(filme).subscribe({
      next: (criado) => {
        this.filmes.update((lista) => [criado, ...lista]);
        this.resultados.set([]);
        this.sorteado.set(null);
        this.busca.set('');
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected alternarAssistido(filme: Filme): void {
    this.lifestyle.marcarFilmeAssistido(filme.id, !filme.assistido).subscribe({
      next: (atualizado) => this.substituir(atualizado),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected avaliar(filme: Filme, nota: number): void {
    this.lifestyle.avaliarFilme(filme.id, nota).subscribe({
      next: (atualizado) => this.substituir(atualizado),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluir(filme: Filme): void {
    if (!confirm(`Tirar "${filme.titulo}" da lista?`)) {
      return;
    }
    this.lifestyle.excluirFilme(filme.id).subscribe({
      next: () => this.filmes.update((lista) => lista.filter((item) => item.id !== filme.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private substituir(filme: Filme): void {
    this.filmes.update((lista) => lista.map((item) => (item.id === filme.id ? filme : item)));
  }

  private carregar(): void {
    this.lifestyle.listarFilmes().subscribe({
      next: (filmes) => {
        this.filmes.set(filmes);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
