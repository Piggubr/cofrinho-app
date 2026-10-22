import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiBase } from './api-base';
import {
  AReceber,
  AcertoDeDivisao,
  Cofrinho,
  ConferenciaDePreco,
  ContaFixa,
  ContaOuCartao,
  Cotacao,
  Deposito,
  Fatura,
  Gasto,
  ItemDeGasto,
  LinhaDoExtrato,
  MoedaDisponivel,
  Nota,
  NovoLancamento,
  ObjetivoDoCofrinho,
  Orcamento,
  PedidoAoAssistente,
  PedidoDeObjetivo,
  Produto,
  Receita,
  ReciboLido,
  RegraDeCategoria,
  RelatorioDoAno,
  RespostaDoAssistente,
  ResumoDoMes,
  SituacaoDoAssistente,
  TransferenciaDoAcerto,
  UsoDeLeituras,
  Visao,
} from './models';

/** Gastos, metas, cofrinho, notas, produtos, categorias, recibos e cambio. */
@Injectable({ providedIn: 'root' })
export class FinanceService extends ApiBase {
  /** @param visao recorte do painel: so o que a pessoa lancou ou so o compartilhado */
  listarGastos(mes?: string, visao: Visao = 'tudo'): Observable<Gasto[]> {
    return this.http.get<Gasto[]>(this.url('/expenses'), {
      params: this.params({ mes, visao: visao === 'tudo' ? null : visao }),
    });
  }

  lancarGastos(pedido: NovoLancamento): Observable<Gasto[]> {
    return this.http.post<Gasto[]>(this.url('/expenses'), pedido);
  }

  /** @param compartilhado ausente mantem como esta; so quem lancou muda */
  editarGasto(id: string, item: ItemDeGasto, compartilhado?: boolean): Observable<Gasto> {
    return this.http.put<Gasto>(this.url(`/expenses/${id}`), {
      item: item.item,
      categoria: item.categoria,
      valor: item.valor,
      // Ausente, o backend deixa como esta: so quem lancou muda isso.
      ...(compartilhado === undefined ? {} : { compartilhado }),
    });
  }

  /** @param eAsProximas na parcela, apaga tambem as parcelas seguintes */
  excluirGasto(id: string, eAsProximas = false): Observable<void> {
    return this.http.delete<void>(this.url(`/expenses/${id}`), {
      params: eAsProximas ? { proximas: true } : {},
    });
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

  /** @param objetivo cofrinho por objetivo; sem ele o deposito vai para o cofrinho geral */
  depositar(valor: number, data?: string, objetivo?: string): Observable<Deposito> {
    return this.http.post<Deposito>(this.url('/piggy-bank/deposits'), { valor, data, objetivo });
  }

  situacaoDoAssistente(): Observable<SituacaoDoAssistente> {
    return this.http.get<SituacaoDoAssistente>(this.url('/assistant'));
  }

  perguntarAoAssistente(pedido: PedidoAoAssistente): Observable<RespostaDoAssistente> {
    return this.http.post<RespostaDoAssistente>(this.url('/assistant/ask'), pedido);
  }

  /** Grava a autorizacao de enviar perguntas e totais ao Gemini, no toque em "Autorizo". */
  autorizarAssistente(versaoDoAviso: string): Observable<SituacaoDoAssistente> {
    return this.http.post<SituacaoDoAssistente>(this.url('/assistant/consent'), { versaoDoAviso });
  }

  /** Retira a autorizacao de enviar perguntas e totais ao Gemini (LGPD art. 8, par. 5). */
  retirarAutorizacaoDoAssistente(): Observable<void> {
    return this.http.delete<void>(this.url('/assistant/consent'));
  }

  listarObjetivos(): Observable<ObjetivoDoCofrinho[]> {
    return this.http.get<ObjetivoDoCofrinho[]>(this.url('/piggy-bank/goals'));
  }

  criarObjetivo(pedido: PedidoDeObjetivo): Observable<ObjetivoDoCofrinho> {
    return this.http.post<ObjetivoDoCofrinho>(this.url('/piggy-bank/goals'), pedido);
  }

  alterarObjetivo(id: string, pedido: PedidoDeObjetivo): Observable<void> {
    return this.http.put<void>(this.url(`/piggy-bank/goals/${id}`), pedido);
  }

  excluirObjetivo(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/piggy-bank/goals/${id}`));
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
  listarReceitas(mes: string): Observable<Receita[]> {
    return this.http.get<Receita[]>(this.url('/incomes'), { params: { mes } });
  }

  categoriasDeReceita(): Observable<string[]> {
    return this.http.get<string[]>(this.url('/incomes/categories'));
  }

  /** @param recorrente entra sozinha no mesmo dia, todo mes */
  lancarReceita(receita: Omit<Receita, 'id' | 'usuario'>, recorrente = false): Observable<Receita> {
    return this.http.post<Receita>(this.url('/incomes'), { ...receita, recorrente });
  }

  /** Receitas que entram sozinhas todo mes. */
  receitasRecorrentes(mes: string): Observable<ContaFixa[]> {
    return this.http.get<ContaFixa[]>(this.url('/bills'), { params: { mes, tipo: 'RECEITA' } });
  }

  aReceber(): Observable<AReceber[]> {
    return this.http.get<AReceber[]>(this.url('/incomes/receivables'));
  }

  criarAReceber(conta: Omit<AReceber, 'id'>): Observable<AReceber> {
    return this.http.post<AReceber>(this.url('/incomes/receivables'), conta);
  }

  /** "Recebi": vira receita de hoje. */
  receber(id: string): Observable<Receita> {
    return this.http.post<Receita>(this.url(`/incomes/receivables/${id}/receive`), {});
  }

  excluirAReceber(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/incomes/receivables/${id}`));
  }

  excluirReceita(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/incomes/${id}`));
  }

  contasFixas(mes: string): Observable<ContaFixa[]> {
    return this.http.get<ContaFixa[]>(this.url('/bills'), { params: { mes } });
  }

  criarContaFixa(
    conta: Pick<ContaFixa, 'descricao' | 'categoria' | 'valor' | 'dia' | 'automatico' | 'compartilhado'>,
  ): Observable<void> {
    return this.http.post<void>(this.url('/bills'), conta);
  }

  excluirContaFixa(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/bills/${id}`));
  }

  /** Lanca o gasto do mes; o mesmo mes duas vezes e recusado. */
  pagarContaFixa(id: string, mes: string): Observable<Gasto> {
    return this.http.post<Gasto>(this.url(`/bills/${id}/pay`), {}, { params: { mes } });
  }

  orcamentos(mes: string): Observable<Orcamento[]> {
    return this.http.get<Orcamento[]>(this.url('/budgets'), { params: { mes } });
  }

  /** Cria ou muda o limite da categoria (Premium). */
  definirOrcamento(categoria: string, limite: number): Observable<void> {
    return this.http.put<void>(this.url('/budgets'), { categoria, limite });
  }

  excluirOrcamento(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/budgets/${id}`));
  }

  /** Receitas, gastos, sobra e taxa de poupanca do mes. */
  resumoDoMes(mes: string, visao: Visao = 'tudo'): Observable<ResumoDoMes> {
    return this.http.get<ResumoDoMes>(this.url('/reports/month'), {
      params: this.params({ mes, visao: visao === 'tudo' ? null : visao }),
    });
  }

  /** Busca global: ate 20 gastos pelo item ou estabelecimento. */
  buscarGastos(q: string): Observable<Gasto[]> {
    return this.http.get<Gasto[]>(this.url('/expenses/search'), { params: { q } });
  }

  /** Le o extrato (OFX ou CSV) e devolve a previa; nada e gravado. */
  previaDoExtrato(conteudo: string): Observable<LinhaDoExtrato[]> {
    return this.http.post<LinhaDoExtrato[]>(this.url('/expenses/import/preview'), { conteudo });
  }

  /** Mesma previa para linhas ja separadas, como os gastos lidos do banco conectado. */
  previaDeLinhas(
    linhas: Pick<LinhaDoExtrato, 'data' | 'descricao' | 'valor' | 'idExterno'>[],
  ): Observable<LinhaDoExtrato[]> {
    return this.http.post<LinhaDoExtrato[]>(this.url('/expenses/import/preview-lines'), { linhas });
  }

  importarExtrato(
    linhas: Omit<LinhaDoExtrato, 'jaImportada'>[],
    contaId: string | null,
  ): Observable<{ importados: number; pulados: number }> {
    return this.http.post<{ importados: number; pulados: number }>(this.url('/expenses/import'), {
      linhas,
      contaId,
    });
  }

  exportarGastos(mes: string): Observable<Blob> {
    return this.http.get(this.url('/expenses/export'), { params: { mes }, responseType: 'blob' });
  }

  acertoDoMes(mes: string): Observable<AcertoDeDivisao[]> {
    return this.http.get<AcertoDeDivisao[]>(this.url('/expenses/splits'), { params: { mes } });
  }

  /** Quem deve quanto a quem nos gastos divididos do mes. */
  quemDeveAQuem(mes: string): Observable<TransferenciaDoAcerto[]> {
    return this.http.get<TransferenciaDoAcerto[]>(this.url('/expenses/splits/transfers'), {
      params: { mes },
    });
  }

  /** Nulo (204) quando o produto ainda nao tem historico de preco. */
  conferirPreco(item: string, valor: number): Observable<ConferenciaDePreco | null> {
    return this.http.get<ConferenciaDePreco | null>(this.url('/products/price-check'), {
      params: { item, valor },
    });
  }

  contas(): Observable<ContaOuCartao[]> {
    return this.http.get<ContaOuCartao[]>(this.url('/accounts'));
  }

  criarConta(
    conta: Pick<ContaOuCartao, 'nome' | 'tipo' | 'fechamento' | 'vencimento' | 'compartilhado'>,
  ): Observable<ContaOuCartao> {
    return this.http.post<ContaOuCartao>(this.url('/accounts'), conta);
  }

  /** Conta ou cartao do casal: so quem criou liga ou desliga. */
  compartilharConta(id: string, compartilhado: boolean): Observable<void> {
    return this.http.put<void>(this.url(`/accounts/${id}/sharing`), { compartilhado });
  }

  excluirConta(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/accounts/${id}`));
  }

  /** Fatura do cartao que fecha no mes (AAAA-MM), com os gastos. */
  fatura(id: string, mes: string): Observable<{ fatura: Fatura; gastos: Gasto[] }> {
    return this.http.get<{ fatura: Fatura; gastos: Gasto[] }>(
      this.url(`/accounts/${id}/statement`),
      {
        params: { mes },
      },
    );
  }

  regrasDeCategoria(): Observable<RegraDeCategoria[]> {
    return this.http.get<RegraDeCategoria[]>(this.url('/categories/rules'));
  }

  /** Cria a regra; se o termo ja tem uma, troca a categoria. */
  definirRegra(termo: string, categoria: string): Observable<RegraDeCategoria> {
    return this.http.put<RegraDeCategoria>(this.url('/categories/rules'), { termo, categoria });
  }

  excluirRegra(id: string): Observable<void> {
    return this.http.delete<void>(this.url(`/categories/rules/${id}`));
  }

  /** Ano mes a mes e por categoria (Premium). */
  relatorioDoAno(ano: number): Observable<RelatorioDoAno> {
    return this.http.get<RelatorioDoAno>(this.url('/reports/year'), { params: { ano } });
  }

  /** Quantas notas a familia leu pela foto no mes e quantas sobram no gratuito. */
  usoDeLeituras(): Observable<UsoDeLeituras> {
    return this.http.get<UsoDeLeituras>(this.url('/receipts/usage'));
  }

  /** Retira a autorizacao de enviar a foto da nota ao Gemini (LGPD art. 8, par. 5). */
  retirarAutorizacaoDaLeituraDeNota(): Observable<void> {
    return this.http.delete<void>(this.url('/receipts/consent'));
  }

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
