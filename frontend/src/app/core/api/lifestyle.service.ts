import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import {
  Filme,
  FilmeDoCatalogo,
  ItemDeCompra,
  Lugar,
  NovoItemDeCompra,
  NovoLugar,
  ProdutoDoCatalogo,
} from './models';

/** Lugares, filmes e listas de compras. */
@Injectable({ providedIn: 'root' })
export class LifestyleService extends ApiBase {
  listarLugares(): Observable<Lugar[]> {
    return this.http.get<Lugar[]>(this.url('/places'));
  }

  criarLugar(lugar: NovoLugar): Observable<Lugar> {
    return this.http.post<Lugar>(this.url('/places'), lugar);
  }

  atualizarLugar(id: string, lugar: NovoLugar): Observable<Lugar> {
    return this.http.put<Lugar>(this.url(`/places/${id}`), lugar);
  }

  excluirLugar(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/places/${id}`));
  }

  listarMarcadores(): Observable<string[]> {
    return this.http.get<string[]>(this.url('/places/tags'));
  }

  criarMarcador(nome: string): Observable<string[]> {
    return this.http.post<string[]>(this.url('/places/tags'), { nome });
  }

  listarFilmes(): Observable<Filme[]> {
    return this.http.get<Filme[]>(this.url('/movies'));
  }

  buscarFilmes(busca: string): Observable<FilmeDoCatalogo[]> {
    return this.http.get<FilmeDoCatalogo[]>(this.url('/movies/search'), {
      params: this.params({ busca }),
    });
  }

  /** @param genero id de genero do TMDB; ausente sorteia entre todos */
  sortearFilme(genero?: string): Observable<FilmeDoCatalogo> {
    return this.http.get<FilmeDoCatalogo>(this.url('/movies/random'), {
      params: this.params({ genero }),
    });
  }

  adicionarFilme(filme: FilmeDoCatalogo): Observable<Filme> {
    return this.http.post<Filme>(this.url('/movies'), filme);
  }

  marcarFilmeAssistido(id: string, assistido: boolean): Observable<Filme> {
    return this.http.patch<Filme>(this.url(`/movies/${id}/watched`), null, {
      params: this.params({ assistido }),
    });
  }

  avaliarFilme(id: string, nota: number): Observable<Filme> {
    return this.http.put<Filme>(this.url(`/movies/${id}/rating`), { nota });
  }

  excluirFilme(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/movies/${id}`));
  }

  listarCompras(lista?: string): Observable<ItemDeCompra[]> {
    return this.http.get<ItemDeCompra[]>(this.url('/shopping/items'), {
      params: this.params({ lista }),
    });
  }

  criarItemDeCompra(item: NovoItemDeCompra): Observable<ItemDeCompra> {
    return this.http.post<ItemDeCompra>(this.url('/shopping/items'), item);
  }

  marcarComprado(id: string, comprado: boolean): Observable<ItemDeCompra> {
    return this.http.patch<ItemDeCompra>(this.url(`/shopping/items/${id}/purchased`), null, {
      params: this.params({ comprado }),
    });
  }

  excluirItemDeCompra(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/shopping/items/${id}`));
  }

  /** Busca no catalogo aberto, para preencher marca, imagem e codigo de barras. */
  buscarNoCatalogo(busca: string): Observable<ProdutoDoCatalogo[]> {
    return this.http.get<ProdutoDoCatalogo[]>(this.url('/shopping/catalog'), {
      params: this.params({ busca }),
    });
  }
}
