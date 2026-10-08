import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import {
  Cofrinho,
  Cotacao,
  MoedaDisponivel,
  Deposito,
  Gasto,
  ItemDeGasto,
  Nota,
  NovoLancamento,
  Produto,
  ReciboLido,
} from './models';

/** Gastos, metas, cofrinho, notas, produtos, categorias, recibos e cambio. */
@Injectable({ providedIn: 'root' })
export class FinanceService extends ApiBase {
  listarGastos(mes?: string): Observable<Gasto[]> {
    return this.http.get<Gasto[]>(this.url('/expenses'), { params: this.params({ mes }) });
  }

  lancarGastos(pedido: NovoLancamento): Observable<Gasto[]> {
    return this.http.post<Gasto[]>(this.url('/expenses'), pedido);
  }

  editarGasto(id: string, item: ItemDeGasto): Observable<Gasto> {
    return this.http.put<Gasto>(this.url(`/expenses/${id}`), {
      item: item.item,
      categoria: item.categoria,
      valor: item.valor,
    });
  }

  excluirGasto(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/expenses/${id}`));
  }

  /** @returns mapa de AAAA-MM para o limite daquele mes */
  listarMetas(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(this.url('/monthly-goals'));
  }

  definirMeta(mes: string, limite: number): Observable<Record<string, number>> {
    return this.http.put<Record<string, number>>(this.url('/monthly-goals'), { mes, limite });
  }

  consultarCofrinho(): Observable<Cofrinho> {
    return this.http.get<Cofrinho>(this.url('/piggy-bank'));
  }

  depositar(valor: number, data?: string): Observable<Deposito> {
    return this.http.post<Deposito>(this.url('/piggy-bank/deposits'), { valor, data });
  }

  excluirDeposito(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/piggy-bank/deposits/${id}`));
  }

  listarNotas(): Observable<Nota[]> {
    return this.http.get<Nota[]>(this.url('/notes'));
  }

  criarNota(nota: {
    titulo: string;
    texto?: string | null;
    data?: string | null;
    valor?: number | null;
    categoria?: string | null;
  }): Observable<Nota> {
    return this.http.post<Nota>(this.url('/notes'), nota);
  }

  excluirNota(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/notes/${id}`));
  }

  listarProdutos(): Observable<Produto[]> {
    return this.http.get<Produto[]>(this.url('/products'));
  }

  listarCategorias(): Observable<{ categorias: string[] }> {
    return this.http.get<{ categorias: string[] }>(this.url('/categories'));
  }

  criarCategoria(nome: string): Observable<{ categorias: string[] }> {
    return this.http.post<{ categorias: string[] }>(this.url('/categories'), { nome });
  }

  /** Le um recibo por foto. Nada e gravado: o usuario confere antes de lancar. */
  /** @param versaoDoAviso so quando a pessoa autorizou, agora, o envio da foto a IA */
  lerRecibo(imageBase64: string, mimeType: string, versaoDoAviso?: string): Observable<ReciboLido> {
    return this.http.post<ReciboLido>(this.url('/receipts/parse'), {
      imageBase64,
      mimeType,
      autorizoIa: versaoDoAviso ? true : undefined,
      versaoDoAviso,
    });
  }

  consultarCotacao(de: string, para: string): Observable<Cotacao> {
    return this.http.get<Cotacao>(this.url('/exchange-rate'), {
      params: this.params({ de, para }),
    });
  }

  /** Moedas que a fonte de cambio conhece, para a escolha nas preferencias. */
  listarMoedas(): Observable<MoedaDisponivel[]> {
    return this.http.get<MoedaDisponivel[]>(this.url('/exchange-rate/currencies'));
  }
}
