import { ErrorHandler, Injectable, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { APP_CONFIG } from '../config/app-config';
import { MODO_DEMO } from '../../demo/modo-demo';

/** Quantos erros uma aba manda no maximo: um defeito em loop nao vira enxurrada. */
const MAXIMO_POR_ABA = 5;

/**
 * Manda para o servidor os erros de JavaScript que acontecem no aparelho da pessoa.
 *
 * <p>Sem isto, um defeito no app so aparecia no console do navegador de quem sofreu. O
 * erro vai para {@code POST /api/app/erros} (ErrosDoAppController no identity) e aparece
 * no painel "Erros e logs" do Grafana.</p>
 *
 * <p>Privacidade: sai so a mensagem, a pilha e a tela (com numeros e ids trocados por
 * ":id"), sem token, sem cookie e sem nenhum identificador da pessoa. E-mail, CPF e
 * telefone saem antes de enviar, e o servidor limpa de novo. Erro de chamada a API fica de
 * fora: o backend ja registra os dele. Na demo (sem backend) nada e enviado.</p>
 */
@Injectable()
export class ErrosDoApp extends ErrorHandler {
  private readonly config = inject(APP_CONFIG);
  private readonly jaEnviados = new Set<string>();

  override handleError(erro: unknown): void {
    super.handleError(erro);
    try {
      this.enviar(erro);
    } catch {
      // Relatar o erro nunca pode virar outro erro.
    }
  }

  private enviar(erro: unknown): void {
    const original = (erro as { rejection?: unknown })?.rejection ?? erro;
    if (
      MODO_DEMO ||
      original instanceof HttpErrorResponse ||
      this.jaEnviados.size >= MAXIMO_POR_ABA
    ) {
      return;
    }
    const mensagem = semDadosPessoais(
      original instanceof Error ? original.message : String(original),
    ).slice(0, 300);
    if (this.jaEnviados.has(mensagem)) {
      return;
    }
    this.jaEnviados.add(mensagem);
    const pilha =
      original instanceof Error ? semDadosPessoais(original.stack ?? '').slice(0, 2000) : '';
    void fetch(`${this.config.apiUrl}/app/erros`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mensagem, pilha, tela: telaAtual(), versao: '' }),
      credentials: 'omit',
      keepalive: true,
    }).catch(() => undefined);
  }
}

/** A tela sem o que identifica alguem: ids e numeros viram ":id" e a busca (?...) sai. */
export function telaAtual(caminho = location.pathname): string {
  return caminho
    .split('/')
    .map((parte) => (/\d/.test(parte) ? ':id' : parte))
    .join('/')
    .slice(0, 120);
}

/** Tira e-mail, CPF, telefone, token e numeros compridos de um texto. */
export function semDadosPessoais(texto: string): string {
  return semEmails(texto)
    .replace(/eyJ[\w-]*\.[\w-]+\.[\w-]*/g, '[token]')
    .replace(/\?[^\s"')]+/g, '?[...]')
    .replace(/\b\d{3}\.\d{3}\.\d{3}-\d{2}\b/g, '[cpf]')
    .replace(/\(?\b\d{2}\)?\s?\d{4,5}-\d{4}\b/g, '[telefone]')
    .replace(/\d{8,}/g, '[numero]');
}

const DO_USUARIO = /[\w.%+-]/;
const DO_DOMINIO = /[\w.-]/;
const LETRA_OU_HIFEN = /[\w-]/;

/**
 * Troca cada e-mail por "[email]". Acha o mesmo que `[\w.%+-]+@[\w-]+\.[\w.-]*[\w-]`, mas
 * andando a partir de cada "@" em vez de uma regex sem ancora, que volta atras em texto
 * comprido sem e-mail.
 */
function semEmails(texto: string): string {
  let saida = '';
  let copiado = 0;
  let arroba = texto.indexOf('@');
  while (arroba !== -1) {
    let inicio = arroba;
    while (inicio > copiado && DO_USUARIO.test(texto[inicio - 1])) inicio--;
    let fim = arroba + 1;
    while (fim < texto.length && DO_DOMINIO.test(texto[fim])) fim++;
    while (fim > arroba + 1 && !LETRA_OU_HIFEN.test(texto[fim - 1])) fim--;
    // Dominio: algo antes do primeiro ponto e algo depois dele.
    const dominio = texto.slice(arroba + 1, fim);
    const ponto = dominio.indexOf('.');
    if (inicio < arroba && ponto > 0 && ponto < dominio.length - 1) {
      saida += texto.slice(copiado, inicio) + '[email]';
      copiado = fim;
    }
    arroba = texto.indexOf('@', Math.max(arroba + 1, copiado));
  }
  return saida + texto.slice(copiado);
}
