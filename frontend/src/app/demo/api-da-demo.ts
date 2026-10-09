import {
  HttpErrorResponse,
  HttpEvent,
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Observable, delay, from, map, of, throwError } from 'rxjs';
import {
  AcertoDeDivisao,
  ContaFixa,
  ContaOuCartao,
  Familia,
  Fatura,
  Gasto,
  InfoDoPlano,
  LinhaDoExtrato,
  Orcamento,
  ParDeTokens,
  Produto,
  RelatorioDoAno,
  ResumoDoMes,
  Usuario,
} from '../core/api/models';
import { dataIso } from '../core/ui/datas';
import {
  CATEGORIAS_BASE,
  CATEGORIAS_DE_RECEITA,
  ContaDemo,
  EstadoDaDemo,
  FILMES_DO_CATALOGO,
  PRODUTOS_DO_CATALOGO,
  criarEstado,
  novoId,
} from './dados-da-demo';

/**
 * API falsa da demonstracao: responde todas as rotas /api com dados em memoria.
 *
 * <p>Escritas mudam o estado de verdade (lancar, apagar, pagar, resgatar...), entao da
 * para testar os fluxos. Recarregar o app volta tudo ao estado inicial. O que depende de
 * servico de fora (Pluggy, Stripe, apagar conta) responde que nao esta disponivel.</p>
 */

/** Erro no mesmo formato do backend: o app mostra a mensagem como mostraria a real. */
class ErroDaDemo extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
    readonly codigo = 'DEMO',
  ) {
    super(mensagem);
  }
}

const INDISPONIVEL = 'Indisponível na demonstração: aqui os dados são de exemplo.';

type Corpo = Record<string, any>;
type Rota = [string, RegExp, (pedido: HttpRequest<unknown>, partes: string[], corpo: Corpo) => unknown];

let estado: EstadoDaDemo = criarEstado();

/** Volta ao estado inicial (usado nos testes). */
export function reiniciarDemo(hoje = new Date()): void {
  estado = criarEstado(hoje);
}

/** Foto da demo: as de exemplo e as enviadas ficam em memoria como data URL. */
export function imagemDaDemo(assetId: string): string | null {
  return estado.imagens[assetId] ?? null;
}

export const apiDaDemo: HttpInterceptorFn = (pedido, proximo) => {
  const inicio = pedido.url.indexOf('/api/');
  if (inicio < 0) {
    return proximo(pedido);
  }
  const caminho = pedido.url.slice(inicio + 4).split('?')[0];
  return responder(pedido, caminho).pipe(delay(120));
};

function responder(pedido: HttpRequest<unknown>, caminho: string): Observable<HttpEvent<unknown>> {
  const corpo = (pedido.body ?? {}) as Corpo;
  for (const [metodo, padrao, tratar] of ROTAS) {
    const achou = metodo === pedido.method ? padrao.exec(caminho) : null;
    if (!achou) {
      continue;
    }
    try {
      const resposta = tratar(pedido, achou.slice(1), corpo);
      if (resposta instanceof Promise) {
        return from(resposta).pipe(map((dado) => new HttpResponse({ status: 200, body: dado, url: pedido.url })));
      }
      const status = resposta === undefined ? 204 : pedido.method === 'POST' ? 201 : 200;
      return of(new HttpResponse({ status, body: resposta ?? null, url: pedido.url }));
    } catch (erro) {
      return falhar(pedido, erro instanceof ErroDaDemo ? erro : new ErroDaDemo(500, 'Ocorreu um erro na demonstração.'));
    }
  }
  return falhar(pedido, new ErroDaDemo(404, INDISPONIVEL));
}

function falhar(pedido: HttpRequest<unknown>, erro: ErroDaDemo): Observable<never> {
  return throwError(
    () =>
      new HttpErrorResponse({
        status: erro.status,
        url: pedido.url,
        error: { erro: erro.message, codigo: erro.codigo, campos: [], momento: new Date().toISOString() },
      }),
  );
}

// ---------- Ajudantes ----------

const hojeIso = () => dataIso(new Date());
const mesAtual = () => hojeIso().slice(0, 7);
const mesDe = (pedido: HttpRequest<unknown>) => pedido.params.get('mes') || mesAtual();
const arredondar = (v: number) => Math.round(v * 100) / 100;
const somar = (valores: number[]) => arredondar(valores.reduce((s, v) => s + v, 0));
const semAcento = (texto: string) =>
  texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();

function exigir<T>(valor: T | undefined, mensagem = 'Não encontrado.'): T {
  if (valor === undefined) {
    throw new ErroDaDemo(404, mensagem, 'NAO_ENCONTRADO');
  }
  return valor;
}

function remover<T extends { id: string }>(lista: T[], id: string): void {
  const indice = lista.findIndex((item) => item.id === id);
  if (indice < 0) {
    throw new ErroDaDemo(404, 'Não encontrado.', 'NAO_ENCONTRADO');
  }
  lista.splice(indice, 1);
}

function somarMes(mes: string, passo: number): string {
  const [ano, m] = mes.split('-').map(Number);
  const data = new Date(ano, m - 1 + passo, 1);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

function diaNoMes(mes: string, dia: number): string {
  const [ano, m] = mes.split('-').map(Number);
  const ultimo = new Date(ano, m, 0).getDate();
  return `${mes}-${String(Math.min(dia, ultimo)).padStart(2, '0')}`;
}

const doMes = (mes: string) => estado.gastos.filter((g) => g.data.startsWith(mes));
const categorias = () => [...CATEGORIAS_BASE, ...estado.categoriasExtras];

function categoriaValida(nome: string | null | undefined): string {
  return categorias().find((c) => semAcento(c) === semAcento(nome ?? '')) ?? 'Outros';
}

/** Categoria automatica: regra pelo item/estabelecimento (termo mais longo) e depois a ultima compra. */
function categoriaAutomatica(item: string, estabelecimento: string): string {
  const texto = ` ${semAcento(item)} ${semAcento(estabelecimento)} `;
  const regra = estado.regras
    .filter((r) => texto.includes(r.termo))
    .sort((a, b) => b.termo.length - a.termo.length)[0];
  if (regra) {
    return regra.categoria;
  }
  const anterior = [...estado.gastos].reverse().find((g) => semAcento(g.item) === semAcento(item));
  return anterior?.categoria ?? 'Outros';
}

function usuarioPorEmail(email: string): Usuario {
  return email === estado.membro.email ? estado.membro : estado.titular;
}

// ---------- Calculos ----------

function resumo(mes: string): ResumoDoMes {
  const anterior = somarMes(mes, -1);
  const porCat = (m: string) => {
    const mapa: Record<string, number> = {};
    for (const g of doMes(m)) {
      mapa[g.categoria] = arredondar((mapa[g.categoria] ?? 0) + g.valor);
    }
    return mapa;
  };
  const agora = porCat(mes);
  const antes = porCat(anterior);
  const receitas = somar(estado.receitas.filter((r) => r.data.startsWith(mes)).map((r) => r.valor));
  const gastos = somar(Object.values(agora));
  const gastosAntes = somar(Object.values(antes));
  const sobra = arredondar(receitas - gastos);
  const hoje = new Date();
  const projecao =
    mes === mesAtual()
      ? arredondar((gastos * new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0).getDate()) / hoje.getDate())
      : null;
  return {
    mes,
    receitas,
    gastos,
    sobra,
    taxaDePoupanca: receitas > 0 ? Math.round((sobra / receitas) * 1000) / 10 : null,
    gastosMesAnterior: gastosAntes,
    variacao: gastosAntes > 0 ? Math.round(((gastos - gastosAntes) / gastosAntes) * 1000) / 10 : null,
    projecaoDeGastos: projecao,
    porCategoria: Object.entries(agora)
      .map(([categoria, total]) => ({ categoria, total, anterior: antes[categoria] ?? 0 }))
      .sort((a, b) => b.total - a.total),
  };
}

function relatorioDoAno(ano: number): RelatorioDoAno {
  const meses = Array.from({ length: 12 }, (_, i) => {
    const mes = `${ano}-${String(i + 1).padStart(2, '0')}`;
    const r = resumo(mes);
    return { mes, receitas: r.receitas, gastos: r.gastos, sobra: r.sobra };
  });
  const porCategoria: Record<string, number> = {};
  for (const g of estado.gastos.filter((g) => g.data.startsWith(String(ano)))) {
    porCategoria[g.categoria] = arredondar((porCategoria[g.categoria] ?? 0) + g.valor);
  }
  const receitas = somar(meses.map((m) => m.receitas));
  const gastos = somar(meses.map((m) => m.gastos));
  return {
    ano,
    receitas,
    gastos,
    sobra: arredondar(receitas - gastos),
    meses,
    porCategoria: Object.entries(porCategoria)
      .map(([categoria, total]) => ({ categoria, total }))
      .sort((a, b) => b.total - a.total),
  };
}

function orcamentos(mes: string): Orcamento[] {
  return estado.orcamentos
    .map((o) => {
      const gasto = somar(doMes(mes).filter((g) => g.categoria === o.categoria).map((g) => g.valor));
      const percentual = Math.round((gasto / o.limite) * 100);
      const alerta: Orcamento['alerta'] = percentual >= 100 ? 'ESTOUROU' : percentual >= 80 ? 'ATENCAO' : 'OK';
      return { id: o.id, categoria: o.categoria, limite: o.limite, gasto, percentual, alerta };
    })
    .sort((a, b) => a.categoria.localeCompare(b.categoria));
}

function contasFixas(mes: string): ContaFixa[] {
  return estado.contasFixas
    .map((c) => {
      const vencimento = diaNoMes(mes, c.dia);
      const paga = estado.contasPagas.has(`${c.id}|${mes}`);
      const situacao: ContaFixa['situacao'] = paga ? 'PAGA' : vencimento < hojeIso() ? 'VENCIDA' : 'PENDENTE';
      return { ...c, vencimento, situacao };
    })
    .sort((a, b) => a.dia - b.dia);
}

function periodo(conta: ContaDemo, mes: string): Omit<Fatura, 'total'> {
  const fechamento = diaNoMes(mes, conta.fechamento!);
  const anterior = new Date(`${diaNoMes(somarMes(mes, -1), conta.fechamento!)}T12:00:00`);
  anterior.setDate(anterior.getDate() + 1);
  const vencimento =
    conta.vencimento! > conta.fechamento! ? diaNoMes(mes, conta.vencimento!) : diaNoMes(somarMes(mes, 1), conta.vencimento!);
  return { mes, inicio: dataIso(anterior), fechamento, vencimento };
}

function gastosDaFatura(conta: ContaDemo, mes: string): { fatura: Fatura; gastos: Gasto[] } {
  const p = periodo(conta, mes);
  const gastos = estado.gastos
    .filter((g) => g.contaId === conta.id && g.data >= p.inicio && g.data <= p.fechamento)
    .sort((a, b) => a.data.localeCompare(b.data));
  return { fatura: { ...p, total: somar(gastos.map((g) => g.valor)) }, gastos };
}

function contaComFaturas(conta: ContaDemo): ContaOuCartao {
  if (conta.tipo !== 'CARTAO') {
    return { ...conta, faturaAberta: null, faturaAPagar: null };
  }
  const hoje = hojeIso();
  const aberta = hoje > diaNoMes(mesAtual(), conta.fechamento!) ? somarMes(mesAtual(), 1) : mesAtual();
  const anterior = gastosDaFatura(conta, somarMes(aberta, -1)).fatura;
  return {
    ...conta,
    faturaAberta: gastosDaFatura(conta, aberta).fatura,
    faturaAPagar: anterior.vencimento < hoje ? null : anterior,
  };
}

function acerto(mes: string): AcertoDeDivisao[] {
  const pagou: Record<string, number> = {};
  const parte: Record<string, number> = {};
  for (const g of doMes(mes)) {
    const partes = estado.partes[g.id];
    if (!partes) {
      continue;
    }
    for (const [email, valor] of Object.entries(partes)) {
      parte[email] = arredondar((parte[email] ?? 0) + valor);
      pagou[g.usuario] = arredondar((pagou[g.usuario] ?? 0) + valor);
    }
  }
  const emails = [...new Set([...Object.keys(pagou), ...Object.keys(parte)])];
  return emails
    .map((email) => {
      const p = pagou[email] ?? 0;
      const d = parte[email] ?? 0;
      return { email, pagou: p, parte: d, saldo: arredondar(p - d) };
    })
    .sort((a, b) => b.saldo - a.saldo);
}

function produtos(): Produto[] {
  const grupos: Record<string, Gasto[]> = {};
  for (const g of estado.gastos.filter((g) => !g.parcelas)) {
    (grupos[semAcento(g.item)] ??= []).push(g);
  }
  return Object.entries(grupos)
    .map(([chave, lista]) => {
      const precos = lista.map((g) => g.valor);
      const ultimo = lista[lista.length - 1];
      const anterior = lista[lista.length - 2];
      return {
        chave,
        nome: ultimo.item,
        categoria: ultimo.categoria,
        ultimo: ultimo.valor,
        menor: Math.min(...precos),
        maior: Math.max(...precos),
        media: arredondar(somar(precos) / precos.length),
        compras: lista.length,
        data: ultimo.data,
        variacao: anterior ? arredondar(ultimo.valor - anterior.valor) : 0,
      };
    })
    .sort((a, b) => b.compras - a.compras);
}

function cofrinho() {
  const depositos = [...estado.depositos].sort((a, b) => b.data.localeCompare(a.data));
  const primeiro = depositos.length ? depositos[depositos.length - 1].data : null;
  const totalDepositos = somar(depositos.map((d) => d.valor));
  const totalGastos = primeiro ? somar(estado.gastos.filter((g) => g.data >= primeiro).map((g) => g.valor)) : 0;
  return { depositos, totalDepositos, totalGastos, saldo: arredondar(totalDepositos - totalGastos) };
}

function familia(): Familia {
  const membro = (u: Usuario) => ({ id: u.id, nome: u.nome, email: u.email, foto: u.foto, papel: u.role });
  return {
    id: 'familia-demo',
    nome: estado.nomeDaFamilia,
    plano: 'PREMIUM',
    membros: [membro(estado.titular), membro(estado.membro)],
    convites: estado.convites.map((c) => ({ ...c, familia: estado.nomeDaFamilia })),
  };
}

function plano(): InfoDoPlano {
  return {
    plano: 'PREMIUM',
    premiumAte: estado.titular.premiumAte,
    origem: 'WEB',
    reembolsoAte: null,
    diasDeTeste: 0,
    assinaturaDisponivel: false,
    site: { mensal: '19,90', anual: '199,00' },
    app: { mensal: '22,89', anual: '228,85' },
    gratuito: ['Gastos, receitas e contas fixas', 'Relatório do mês', '10 leituras de nota por mês'],
    premium: ['Open Finance', 'Notas sem limite', 'Orçamento por categoria', 'Relatório anual', 'Mural de fotos'],
  };
}

function tokens(): ParDeTokens {
  return { accessToken: 'token-da-demonstracao', expiresIn: 1800, usuario: estado.titular };
}

/** Le o CSV da importacao do jeito mais simples: data, descricao, valor (debito negativo). */
function previaDoExtrato(conteudo: string): LinhaDoExtrato[] {
  if (/<OFX>/i.test(conteudo)) {
    const linhas: LinhaDoExtrato[] = [];
    for (const bloco of conteudo.split(/<STMTTRN>/i).slice(1)) {
      const campo = (nome: string) => new RegExp(`<${nome}>([^<\\r\\n]*)`, 'i').exec(bloco)?.[1]?.trim() ?? '';
      const valor = Number(campo('TRNAMT').replace(',', '.'));
      if (!(valor < 0)) {
        continue;
      }
      const d = campo('DTPOSTED');
      const descricao = campo('MEMO') || campo('NAME') || 'Sem descrição';
      linhas.push(linha(`${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`, descricao, -valor, 'ofx:' + campo('FITID')));
    }
    return linhas;
  }
  const [cabecalho, ...resto] = conteudo.replace(/^﻿/, '').split(/\r?\n/).filter((l) => l.trim());
  const sep = cabecalho?.includes(';') ? ';' : ',';
  const lidas = resto.map((l, i) => {
    const [data, descricao, valorTexto] = l.split(sep).map((c) => c.trim().replace(/^"|"$/g, ''));
    const iso = data.includes('/') ? data.split('/').reverse().join('-') : data.slice(0, 10);
    const limpo = valorTexto.replace(/[^0-9,.-]/g, '');
    const valor = Number(
      limpo.lastIndexOf(',') > limpo.lastIndexOf('.') ? limpo.replace(/\./g, '').replace(',', '.') : limpo.replace(/,/g, ''),
    );
    return { iso, descricao, valor, i };
  });
  const temSinal = lidas.some((l) => l.valor < 0);
  return lidas
    .filter((l) => !Number.isNaN(l.valor) && (!temSinal || l.valor < 0))
    .map((l) => linha(l.iso, l.descricao, Math.abs(l.valor), `csv:${l.iso}|${semAcento(l.descricao)}|${Math.abs(l.valor)}|${l.i}`));
}

function linha(data: string, descricao: string, valor: number, idExterno: string): LinhaDoExtrato {
  return {
    data,
    descricao,
    valor,
    idExterno,
    categoria: categoriaAutomatica(descricao, ''),
    jaImportada: estado.gastos.some((g) => (g as Gasto & { idExterno?: string }).idExterno === idExterno),
  };
}

function csvDoMes(mes: string): Blob {
  const celula = (v: string) => `"${(/^[=+\-@]/.test(v) ? "'" + v : v).replace(/"/g, '""')}"`;
  const linhas = doMes(mes).map((g) =>
    [g.data, celula(g.item), celula(g.categoria), String(g.valor).replace('.', ','), celula(g.estabelecimento)].join(';'),
  );
  return new Blob(['﻿' + ['data;item;categoria;valor;estabelecimento', ...linhas].join('\n')], { type: 'text/csv' });
}

/** Grava o lancamento: parcelas viram uma linha por mes; divisao em partes iguais. */
function lancar(corpo: Corpo): Gasto[] {
  const parcelas = Number(corpo['parcelas'] ?? 1) || 1;
  const reciboId = corpo['reciboId'] ?? novoId('recibo');
  const estabelecimento = corpo['estabelecimento'] ?? '';
  const novos: Gasto[] = [];
  for (const item of (corpo['itens'] ?? []) as Corpo[]) {
    const manual = (corpo['origem'] ?? 'Manual') === 'Manual' && item['categoria'];
    const categoria = manual ? categoriaValida(item['categoria']) : categoriaAutomatica(item['item'], estabelecimento);
    const valor = Number(item['valor']);
    const parcela = Math.floor((valor / parcelas) * 100) / 100;
    for (let i = 0; i < parcelas; i++) {
      const data = new Date(`${corpo['data']}T12:00:00`);
      data.setMonth(data.getMonth() + i);
      novos.push({
        id: novoId('gasto'),
        data: dataIso(data),
        reciboId,
        estabelecimento,
        item: item['item'],
        categoria,
        valor: i === parcelas - 1 ? arredondar(valor - parcela * (parcelas - 1)) : parcela,
        tipo: item['tipo'] ?? 'Variavel',
        origem: corpo['origem'] ?? 'Manual',
        usuario: estado.titular.email,
        registradoEm: new Date().toISOString(),
        contaId: corpo['contaId'] ?? null,
        parcela: parcelas > 1 ? i + 1 : null,
        parcelas: parcelas > 1 ? parcelas : null,
        moedaOriginal: item['moedaOriginal'] ?? null,
        valorOriginal: item['valorOriginal'] ?? null,
      });
    }
  }
  const dividir = (corpo['dividirCom'] ?? []) as string[];
  for (const gasto of novos) {
    if (dividir.length) {
      estado.partes[gasto.id] = Object.fromEntries(dividir.map((email) => [email, arredondar(gasto.valor / dividir.length)]));
    }
  }
  estado.gastos.push(...novos);
  return novos;
}

const COTACOES: Record<string, number> = { BRL: 1, USD: 5.5, EUR: 6.0, GBP: 7.0, ARS: 0.0055, CLP: 0.0058, UYU: 0.14, JPY: 0.036 };

// ---------- Rotas ----------

const ID = '([^/]+)';

const ROTAS: Rota[] = [
  // Sessao: sempre a Ana, titular Premium.
  ['POST', /^\/auth\/(google|refresh)$/, () => tokens()],
  ['POST', /^\/auth\/logout$/, () => undefined],
  ['GET', /^\/auth\/me$/, () => estado.titular],
  ['PUT', /^\/auth\/me\/preferences$/, (_p, _x, c) => {
    estado.titular = { ...estado.titular, preferencias: { ...estado.titular.preferencias, ...(c as Usuario['preferencias']) } };
    return estado.titular;
  }],
  ['GET', /^\/me\/export$/, () => new Blob([JSON.stringify({ aviso: 'Dados de exemplo da demonstração', gastos: estado.gastos }, null, 2)], { type: 'application/json' })],
  ['DELETE', /^\/me$/, () => { throw new ErroDaDemo(422, INDISPONIVEL); }],
  ['GET', /^\/users$/, () => [estado.titular, estado.membro]],

  // Familia
  ['GET', /^\/family$/, () => familia()],
  ['PUT', /^\/family$/, (_p, _x, c) => { estado.nomeDaFamilia = String(c['nome'] || estado.nomeDaFamilia); return familia(); }],
  ['POST', /^\/family\/invites$/, (_p, _x, c) => {
    estado.convites.push({ id: novoId('convite'), email: String(c['email']).toLowerCase(), venceEm: dataIso(new Date(Date.now() + 7 * 86_400_000)) });
    return familia();
  }],
  ['DELETE', new RegExp(`^/family/invites/${ID}$`), (_p, [id]) => { remover(estado.convites, id); return familia(); }],
  ['DELETE', new RegExp(`^/family/members/${ID}$`), () => { throw new ErroDaDemo(422, INDISPONIVEL); }],
  ['GET', /^\/family\/invites\/mine$/, () => []],
  ['POST', /^\/family\/(leave|invites\/.+\/accept)$/, () => { throw new ErroDaDemo(422, INDISPONIVEL); }],

  // Plano e pagamento: nada sai para a Stripe.
  ['GET', /^\/billing\/plan$/, () => plano()],
  ['POST', /^\/billing\/(checkout|portal|refund)$/, () => { throw new ErroDaDemo(422, 'Pagamento indisponível na demonstração: esta conta de exemplo já é Premium.'); }],

  // Open Finance: contas de exemplo; conectar banco de verdade nao.
  ['GET', /^\/banking\/status$/, () => ({ habilitado: true })],
  ['GET', /^\/banking\/accounts$/, () => estado.bancos],
  ['POST', /^\/banking\/sync$/, () => estado.bancos],
  ['POST', /^\/banking\/(connect-token|items)$/, () => { throw new ErroDaDemo(422, 'Conectar banco (Pluggy) fica indisponível na demonstração. As contas abaixo são de exemplo.'); }],
  ['DELETE', new RegExp(`^/banking/connections/${ID}$`), (_p, [id]) => { estado.bancos = estado.bancos.filter((b) => b.conexaoId !== id); return estado.bancos; }],

  // Gastos
  ['GET', /^\/expenses\/search$/, (p) => {
    const termo = semAcento(p.params.get('q') ?? '');
    return termo.length < 2 ? [] : estado.gastos
      .filter((g) => semAcento(`${g.item} ${g.estabelecimento}`).includes(termo))
      .sort((a, b) => b.data.localeCompare(a.data))
      .slice(0, 20);
  }],
  ['GET', /^\/expenses\/splits$/, (p) => acerto(mesDe(p))],
  ['GET', /^\/expenses\/export$/, (p) => csvDoMes(mesDe(p))],
  ['POST', /^\/expenses\/import\/preview$/, (_p, _x, c) => previaDoExtrato(String(c['conteudo'] ?? ''))],
  ['POST', /^\/expenses\/import$/, (_p, _x, c) => {
    const linhas = (c['linhas'] ?? []) as Corpo[];
    let importados = 0;
    for (const l of linhas) {
      if (estado.gastos.some((g) => (g as Gasto & { idExterno?: string }).idExterno === l['idExterno'])) {
        continue;
      }
      const [novo] = lancar({ data: l['data'], origem: l['categoria'] ? 'Manual' : 'Extrato', contaId: c['contaId'], itens: [{ item: l['descricao'], valor: l['valor'], categoria: l['categoria'] }] });
      Object.assign(novo, { origem: 'Extrato', idExterno: l['idExterno'] });
      importados++;
    }
    return { importados, pulados: linhas.length - importados };
  }],
  ['GET', /^\/expenses$/, (p) => {
    const mes = p.params.get('mes');
    return (mes ? doMes(mes) : estado.gastos).slice().sort((a, b) => b.data.localeCompare(a.data) || b.registradoEm.localeCompare(a.registradoEm));
  }],
  ['POST', /^\/expenses$/, (_p, _x, c) => lancar(c)],
  ['PUT', new RegExp(`^/expenses/${ID}$`), (_p, [id], c) => {
    const gasto = exigir(estado.gastos.find((g) => g.id === id), 'Gasto não encontrado.');
    Object.assign(gasto, { item: c['item'], categoria: categoriaValida(c['categoria']), valor: Number(c['valor']) });
    return gasto;
  }],
  ['DELETE', new RegExp(`^/expenses/${ID}$`), (_p, [id]) => { remover(estado.gastos, id); delete estado.partes[id]; }],

  // Categorias e regras
  ['GET', /^\/categories$/, () => ({ categorias: categorias() })],
  ['POST', /^\/categories$/, (_p, _x, c) => {
    const nome = String(c['nome'] ?? '').trim();
    if (nome.length < 2 || categorias().some((x) => semAcento(x) === semAcento(nome))) {
      throw new ErroDaDemo(422, 'Essa opção já existe.');
    }
    estado.categoriasExtras.push(nome);
    return { categorias: categorias() };
  }],
  ['GET', /^\/categories\/rules$/, () => [...estado.regras].sort((a, b) => a.termo.localeCompare(b.termo))],
  ['PUT', /^\/categories\/rules$/, (_p, _x, c) => {
    const termo = semAcento(String(c['termo'] ?? ''));
    if (termo.length < 2) {
      throw new ErroDaDemo(422, 'Digite um termo válido para a regra.');
    }
    const existente = estado.regras.find((r) => r.termo === termo);
    if (existente) {
      existente.categoria = categoriaValida(c['categoria']);
      return existente;
    }
    const nova = { id: novoId('regra'), termo, categoria: categoriaValida(c['categoria']) };
    estado.regras.push(nova);
    return nova;
  }],
  ['DELETE', new RegExp(`^/categories/rules/${ID}$`), (_p, [id]) => remover(estado.regras, id)],

  // Receitas
  ['GET', /^\/incomes\/categories$/, () => CATEGORIAS_DE_RECEITA],
  ['GET', /^\/incomes$/, (p) => estado.receitas.filter((r) => r.data.startsWith(mesDe(p))).sort((a, b) => b.data.localeCompare(a.data))],
  ['POST', /^\/incomes$/, (_p, _x, c) => {
    const receita = { id: novoId('receita'), data: c['data'], descricao: c['descricao'], categoria: c['categoria'] || 'Outros', valor: Number(c['valor']), usuario: estado.titular.email };
    estado.receitas.push(receita);
    return receita;
  }],
  ['DELETE', new RegExp(`^/incomes/${ID}$`), (_p, [id]) => remover(estado.receitas, id)],

  // Relatorios
  ['GET', /^\/reports\/month$/, (p) => resumo(mesDe(p))],
  ['GET', /^\/reports\/year$/, (p) => relatorioDoAno(Number(p.params.get('ano')) || new Date().getFullYear())],

  // Contas fixas
  ['GET', /^\/bills$/, (p) => contasFixas(mesDe(p))],
  ['POST', /^\/bills$/, (_p, _x, c) => {
    estado.contasFixas.push({ id: novoId('fixa'), descricao: c['descricao'], categoria: categoriaValida(c['categoria']), valor: Number(c['valor']), dia: Number(c['dia']), automatico: !!c['automatico'] });
  }],
  ['DELETE', new RegExp(`^/bills/${ID}$`), (_p, [id]) => remover(estado.contasFixas, id)],
  ['POST', new RegExp(`^/bills/${ID}/pay$`), (p, [id]) => {
    const conta = exigir(estado.contasFixas.find((c) => c.id === id), 'Conta não encontrada.');
    const mes = mesDe(p);
    if (estado.contasPagas.has(`${id}|${mes}`)) {
      throw new ErroDaDemo(409, 'Essa conta já foi paga neste mês.');
    }
    estado.contasPagas.add(`${id}|${mes}`);
    const [gasto] = lancar({ data: diaNoMes(mes, conta.dia), origem: 'Conta fixa', itens: [{ item: conta.descricao, categoria: conta.categoria, valor: conta.valor, tipo: 'Fixo' }] });
    gasto.categoria = conta.categoria;
    return gasto;
  }],

  // Orcamentos
  ['GET', /^\/budgets$/, (p) => orcamentos(mesDe(p))],
  ['PUT', /^\/budgets$/, (_p, _x, c) => {
    const categoria = categoriaValida(c['categoria']);
    const existente = estado.orcamentos.find((o) => o.categoria === categoria);
    if (existente) {
      existente.limite = Number(c['limite']);
    } else {
      estado.orcamentos.push({ id: novoId('orc'), categoria, limite: Number(c['limite']) });
    }
  }],
  ['DELETE', new RegExp(`^/budgets/${ID}$`), (_p, [id]) => remover(estado.orcamentos, id)],

  // Contas e cartoes
  ['GET', /^\/accounts$/, () => estado.contas.map(contaComFaturas).sort((a, b) => a.nome.localeCompare(b.nome))],
  ['POST', /^\/accounts$/, (_p, _x, c) => {
    const cartao = c['tipo'] === 'CARTAO';
    const conta: ContaDemo = { id: novoId('conta'), nome: String(c['nome']), tipo: cartao ? 'CARTAO' : 'CONTA', fechamento: cartao ? Number(c['fechamento']) : null, vencimento: cartao ? Number(c['vencimento']) : null };
    estado.contas.push(conta);
    return contaComFaturas(conta);
  }],
  ['DELETE', new RegExp(`^/accounts/${ID}$`), (_p, [id]) => {
    remover(estado.contas, id);
    estado.gastos.filter((g) => g.contaId === id).forEach((g) => (g.contaId = null));
  }],
  ['GET', new RegExp(`^/accounts/${ID}/statement$`), (p, [id]) => {
    const conta = exigir(estado.contas.find((c) => c.id === id), 'Conta ou cartão não encontrado.');
    if (conta.tipo !== 'CARTAO') {
      throw new ErroDaDemo(422, 'Fatura só existe para cartão.');
    }
    return gastosDaFatura(conta, mesDe(p));
  }],

  // Metas, cofrinho, notas, produtos
  ['GET', /^\/monthly-goals$/, () => estado.metas],
  ['PUT', /^\/monthly-goals$/, (_p, _x, c) => { estado.metas[String(c['mes'])] = Number(c['limite']); return estado.metas; }],
  ['GET', /^\/piggy-bank$/, () => cofrinho()],
  ['POST', /^\/piggy-bank\/deposits$/, (_p, _x, c) => {
    const deposito = { id: novoId('deposito'), data: c['data'] || hojeIso(), valor: Number(c['valor']), usuario: estado.titular.email, registradoEm: new Date().toISOString() };
    estado.depositos.push(deposito);
    return deposito;
  }],
  ['DELETE', new RegExp(`^/piggy-bank/deposits/${ID}$`), (_p, [id]) => remover(estado.depositos, id)],
  ['GET', /^\/notes$/, () => estado.notas],
  ['POST', /^\/notes$/, (_p, _x, c) => {
    const nota = { id: novoId('nota'), titulo: c['titulo'], texto: c['texto'] ?? '', data: c['data'] ?? null, valor: Number(c['valor'] ?? 0), categoria: c['categoria'] ?? 'Outros', gastoId: null, usuario: estado.titular.email, criadoEm: new Date().toISOString() };
    estado.notas.push(nota);
    return nota;
  }],
  ['DELETE', new RegExp(`^/notes/${ID}$`), (_p, [id]) => remover(estado.notas, id)],
  ['GET', /^\/products\/price-check$/, (p) => {
    const produto = produtos().find((x) => x.chave === semAcento(p.params.get('item') ?? ''));
    if (!produto) {
      return null;
    }
    const valor = Number(p.params.get('valor'));
    const percentual = Math.round(((valor - produto.media) / produto.media) * 100);
    return { media: produto.media, compras: produto.compras, percentual, acima: produto.compras >= 2 && percentual > 15 };
  }],
  ['GET', /^\/products$/, () => produtos()],

  // Leitura de nota: devolve um cupom de exemplo.
  ['GET', /^\/receipts\/usage$/, () => ({ usadas: estado.leiturasNoMes, limite: 10, restantes: null })],
  ['POST', /^\/receipts\/parse$/, () => {
    estado.leiturasNoMes++;
    return {
      reciboId: novoId('recibo'),
      estabelecimento: 'Supermercado Bom Preço',
      data: hojeIso(),
      itens: [
        { item: 'Arroz 5kg', categoria: 'Alimentação', valor: 27.9 },
        { item: 'Feijão carioca 1kg', categoria: 'Alimentação', valor: 8.49 },
        { item: 'Café 500g', categoria: 'Alimentação', valor: 18.9 },
        { item: 'Detergente', categoria: 'Outros', valor: 2.79 },
      ],
      origem: 'OCR',
      aviso: 'Demonstração: este é um cupom de exemplo, a foto não foi lida.',
      leiturasRestantes: null,
    };
  }],

  // Cambio sem rede
  ['GET', /^\/exchange-rate\/currencies$/, () => Object.keys(COTACOES).map((codigo) => ({ codigo, nome: codigo }))],
  ['GET', /^\/exchange-rate$/, (p) => {
    const de = p.params.get('de') ?? 'USD';
    const para = p.params.get('para') ?? 'BRL';
    return { de, para, taxa: Math.round(((COTACOES[de] ?? 1) / (COTACOES[para] ?? 1)) * 10000) / 10000, data: hojeIso(), fonte: 'Demonstração', estimativa: true, desatualizada: false };
  }],

  // Lugares
  ['GET', /^\/places\/tags$/, () => estado.marcadores],
  ['POST', /^\/places\/tags$/, (_p, _x, c) => { estado.marcadores.push(String(c['nome'])); return estado.marcadores; }],
  ['GET', /^\/places$/, () => [...estado.lugares].sort((a, b) => b.data.localeCompare(a.data))],
  ['POST', /^\/places$/, (_p, _x, c) => salvarLugar(null, c)],
  ['PUT', new RegExp(`^/places/${ID}$`), (_p, [id], c) => salvarLugar(id, c)],
  ['DELETE', new RegExp(`^/places/${ID}$`), (_p, [id]) => remover(estado.lugares, id)],

  // Filmes
  ['GET', /^\/movies\/search$/, (p) => {
    const termo = semAcento(p.params.get('busca') ?? p.params.get('q') ?? '');
    return FILMES_DO_CATALOGO.filter((f) => semAcento(f.titulo).includes(termo));
  }],
  ['GET', /^\/movies\/random$/, () => FILMES_DO_CATALOGO[Math.floor(Math.random() * FILMES_DO_CATALOGO.length)]],
  ['GET', /^\/movies$/, () => estado.filmes],
  ['POST', /^\/movies$/, (_p, _x, c) => {
    if (estado.filmes.some((f) => f.tmdbId === c['tmdbId'])) {
      throw new ErroDaDemo(422, 'Esse filme já está na lista.');
    }
    const filme = { id: novoId('filme'), tmdbId: c['tmdbId'], titulo: c['titulo'], ano: c['ano'] ?? '', poster: '', nota: Number(c['nota'] ?? 0), sinopse: c['sinopse'] ?? '', assistido: false, avaliacoes: {}, usuario: estado.titular.email };
    estado.filmes.push(filme);
    return filme;
  }],
  ['PATCH', new RegExp(`^/movies/${ID}/watched$`), (p, [id]) => {
    const filme = exigir(estado.filmes.find((f) => f.id === id));
    filme.assistido = p.params.get('assistido') !== 'false';
    return filme;
  }],
  ['PUT', new RegExp(`^/movies/${ID}/rating$`), (_p, [id], c) => {
    const filme = exigir(estado.filmes.find((f) => f.id === id));
    filme.avaliacoes = { ...filme.avaliacoes, [estado.titular.primeiroNome]: Number(c['nota']) };
    return filme;
  }],
  ['DELETE', new RegExp(`^/movies/${ID}$`), (_p, [id]) => remover(estado.filmes, id)],

  // Compras
  ['GET', /^\/shopping\/catalog$/, (p) => {
    const termo = semAcento(p.params.get('busca') ?? p.params.get('q') ?? '');
    return PRODUTOS_DO_CATALOGO.filter((x) => semAcento(`${x.nome} ${x.marca}`).includes(termo));
  }],
  ['GET', /^\/shopping\/items$/, (p) => {
    const lista = p.params.get('lista');
    return estado.compras.filter((c) => !lista || c.lista === lista);
  }],
  ['POST', /^\/shopping\/items$/, (_p, _x, c) => {
    const item = { id: novoId('compra'), item: c['item'], quantidade: c['quantidade'] ?? '', lista: c['lista'] === 'Desejos' ? 'Desejos' as const : 'Compras' as const, comprado: false, marca: c['marca'] ?? '', imagem: c['imagem'] ?? '', codigo: c['codigo'] ?? '', usuario: estado.titular.email };
    estado.compras.push(item);
    return item;
  }],
  ['PATCH', new RegExp(`^/shopping/items/${ID}/purchased$`), (p, [id]) => {
    const item = exigir(estado.compras.find((c) => c.id === id));
    item.comprado = p.params.get('comprado') !== 'false';
    return item;
  }],
  ['DELETE', new RegExp(`^/shopping/items/${ID}$`), (_p, [id]) => remover(estado.compras, id)],

  // Mural e arquivos
  ['GET', /^\/feed$/, (p) => {
    const mes = p.params.get('mes');
    return estado.fotos.filter((f) => !mes || f.mesKey === mes).sort((a, b) => b.criadoEm.localeCompare(a.criadoEm));
  }],
  ['POST', /^\/feed$/, (_p, _x, c) => {
    const assetId = guardarImagem(c);
    const foto = { id: novoId('feed'), mesKey: c['mesKey'] || mesAtual(), assetId, legenda: '', usuario: estado.titular.email, criadoEm: new Date().toISOString() };
    estado.fotos.push(foto);
    return foto;
  }],
  ['PATCH', new RegExp(`^/feed/${ID}/caption$`), (_p, [id], c) => {
    const foto = exigir(estado.fotos.find((f) => f.id === id));
    foto.legenda = String(c['legenda'] ?? '');
    return foto;
  }],
  ['DELETE', new RegExp(`^/feed/${ID}$`), (_p, [id]) => remover(estado.fotos, id)],
  ['POST', /^\/assets$/, (_p, _x, c) => ({ id: guardarImagem(c), contentType: c['mimeType'] ?? 'image/jpeg', tamanho: String(c['imageBase64'] ?? '').length, contexto: c['contexto'] ?? '', criadoEm: new Date().toISOString() })],
  ['GET', new RegExp(`^/assets/${ID}/content$`), (_p, [id]) => fetch(exigir(estado.imagens[id])).then((r) => r.blob())],
  ['DELETE', new RegExp(`^/assets/${ID}$`), (_p, [id]) => { delete estado.imagens[id]; }],

  // Fofocoins e premios
  ['GET', /^\/coins$/, () => ({ saldo: estado.moedas, historico: [...estado.movimentos].sort((a, b) => b.data.localeCompare(a.data)) })],
  ['POST', /^\/coins\/adjustments$/, (_p, _x, c) => {
    const valor = Number(c['valor']);
    estado.moedas += valor;
    estado.movimentos.push({ id: novoId('moeda'), data: hojeIso(), valor, motivo: String(c['motivo'] ?? ''), usuario: estado.titular.email });
    return { saldo: estado.moedas, historico: [...estado.movimentos].sort((a, b) => b.data.localeCompare(a.data)) };
  }],
  ['GET', /^\/prizes\/redemptions$/, () => estado.resgates],
  ['POST', new RegExp(`^/prizes/${ID}/redemptions$`), (_p, [id]) => {
    const premio = exigir(estado.premios.find((x) => x.id === id), 'Prêmio não encontrado.');
    if (estado.moedas < premio.preco) {
      throw new ErroDaDemo(422, 'Moedas insuficientes para esse prêmio.', 'SALDO_INSUFICIENTE');
    }
    estado.moedas -= premio.preco;
    estado.movimentos.push({ id: novoId('moeda'), data: hojeIso(), valor: -premio.preco, motivo: `Resgate: ${premio.nome}`, usuario: estado.titular.email });
    const resgate = { id: novoId('resgate'), premio: premio.nome, preco: premio.preco, usuario: estado.titular.email, status: 'RESGATADO', data: hojeIso(), saldo: estado.moedas };
    estado.resgates.unshift(resgate);
    return resgate;
  }],
  ['GET', /^\/prizes$/, (p) => estado.premios.filter((x) => p.params.get('todos') === 'true' || x.ativo)],
  ['POST', /^\/prizes$/, (_p, _x, c) => {
    const premio = { id: novoId('premio'), nome: c['nome'], descricao: c['descricao'] ?? '', preco: Number(c['preco']), ativo: c['ativo'] ?? true };
    estado.premios.push(premio);
    return premio;
  }],
  ['PUT', new RegExp(`^/prizes/${ID}$`), (_p, [id], c) => {
    const premio = exigir(estado.premios.find((x) => x.id === id));
    Object.assign(premio, { nome: c['nome'], descricao: c['descricao'] ?? '', preco: Number(c['preco']), ativo: c['ativo'] ?? premio.ativo });
    return premio;
  }],
  ['DELETE', new RegExp(`^/prizes/${ID}$`), (_p, [id]) => remover(estado.premios, id)],
];

function guardarImagem(c: Corpo): string {
  const id = novoId('arquivo');
  estado.imagens[id] = `data:${c['mimeType'] ?? 'image/jpeg'};base64,${c['imageBase64']}`;
  return id;
}

function salvarLugar(id: string | null, c: Corpo) {
  const existente = id ? exigir(estado.lugares.find((l) => l.id === id), 'Lugar não encontrado.') : null;
  const fotoAssetId = c['imageBase64'] ? guardarImagem(c) : (existente?.fotoAssetId ?? null);
  const lugar = {
    id: existente?.id ?? novoId('lugar'),
    nome: c['nome'],
    categoria: c['categoria'] ?? '',
    localizacao: c['localizacao'] ?? '',
    nota: Number(c['nota']),
    comentario: c['comentario'] ?? '',
    data: c['data'],
    marcacoes: c['marcacoes'] ?? [],
    fotoAssetId,
    temFoto: fotoAssetId !== null,
    valor: Number(c['valor'] ?? 0),
    usuario: existente?.usuario ?? usuarioPorEmail(estado.titular.email).email,
  };
  if (existente) {
    Object.assign(existente, lugar);
    return existente;
  }
  estado.lugares.push(lugar);
  return lugar;
}
