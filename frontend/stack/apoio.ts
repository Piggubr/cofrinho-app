import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { APIRequestContext, expect, request, test as base } from '@playwright/test';

/**
 * Apoio dos testes contra a stack de verdade (scripts/smoke.sh): os sete servicos no
 * Docker, o Postgres com as migracoes reais e o front servido com o proxy de /api.
 */

/** Gateway, a porta unica da API. */
export const API = process.env['PIGGU_API'] ?? 'http://localhost:8080';

/** Nome do projeto do Compose que o scripts/smoke.sh sobe (separado do de desenvolvimento). */
const PROJETO = process.env['PIGGU_STACK_PROJETO'] ?? 'piggu-smoke';

const RAIZ = resolve(__dirname, '..', '..');

/** Le um arquivo do repositorio a partir da raiz. */
export function arquivo(caminho: string): string {
  return readFileSync(resolve(RAIZ, caminho), 'utf8');
}

/** Versao dos Termos que o front manda no cadastro, lida do proprio front. */
export const VERSAO_DOS_TERMOS = /VERSAO_DO_AVISO = '([^']+)'/.exec(
  arquivo('frontend/src/app/core/privacidade/aviso.ts'),
)![1];

export const SENHA = 'Fofura2026ok';

export interface Conta {
  nome: string;
  email: string;
  senha: string;
}

export interface Sessao {
  token: string;
  usuario: { id: string; email: string; primeiroNome: string; termosAceitos: string };
}

/** CPF com digitos verificadores certos, diferente a cada chamada. */
export function cpfValido(): string {
  const digitos = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  for (const peso of [10, 11]) {
    const soma = digitos.reduce((total, digito, i) => total + digito * (peso - i), 0);
    const resto = (soma * 10) % 11;
    digitos.push(resto === 10 ? 0 : resto);
  }
  return digitos.join('');
}

/**
 * E-mail que nao existe no banco. A parte antes do @ e diferente do nome de proposito:
 * o painel precisa cumprimentar pelo "Como quer ser chamado", nao pelo e-mail.
 */
export function novaConta(nome = 'Fofinha'): Conta {
  const sufixo = `${Date.now().toString(36)}${Math.floor(Math.random() * 1e6).toString(36)}`;
  return { nome, email: `smoke.${sufixo}@exemplo.com`, senha: SENHA };
}

/** O que a tela de cadastro manda (features/login/login.ts). */
export function pedidoDeCadastro(conta: Conta) {
  return {
    nome: conta.nome,
    email: conta.email,
    senha: conta.senha,
    pais: 'BR',
    documento: cpfValido(),
    telefone: '+55 11 98765-4321',
    versaoDosTermos: VERSAO_DOS_TERMOS,
    nascimento: '1995-05-10',
  };
}

export async function cadastrar(api: APIRequestContext, conta: Conta): Promise<void> {
  const resposta = await api.post('/api/auth/cadastro', { data: pedidoDeCadastro(conta) });
  expect(resposta.status(), await resposta.text()).toBe(202);
}

export async function entrar(api: APIRequestContext, conta: Conta): Promise<Sessao> {
  const resposta = await api.post('/api/auth/entrar', {
    data: { email: conta.email, senha: conta.senha, lembrar: true },
  });
  expect(resposta.status(), await resposta.text()).toBe(200);
  const corpo = await resposta.json();
  return { token: corpo.accessToken, usuario: corpo.usuario };
}

export function comToken(sessao: Sessao) {
  return { Authorization: `Bearer ${sessao.token}` };
}

/**
 * Sem provedor de e-mail (dev), o identity escreve no log o link que iria por e-mail.
 * Le o log do container e devolve o token do link mais recente da conta.
 *
 * @param assunto o comeco da linha do log, por exemplo "link de confirmacao"
 */
export async function linkDoLog(assunto: string, contaId: string): Promise<string> {
  const container = execFileSync('docker', [
    'ps',
    '-q',
    '--filter',
    `label=com.docker.compose.project=${PROJETO}`,
    '--filter',
    'label=com.docker.compose.service=identity',
  ])
    .toString()
    .trim();
  expect(container, `container do identity no projeto ${PROJETO}`).not.toBe('');

  const padrao = new RegExp(`${assunto} da conta ${contaId}: \\S+[?&]token=([\\w-]+)`, 'g');
  let token: string | undefined;
  await expect
    .poll(
      () => {
        const log = execFileSync('docker', ['logs', container], {
          stdio: ['ignore', 'pipe', 'pipe'],
        });
        token = [...log.toString().matchAll(padrao)].at(-1)?.[1];
        return token;
      },
      { message: `"${assunto}" da conta ${contaId} no log do identity` },
    )
    .toBeTruthy();
  return token!;
}

/** `api`: contexto novo por teste, falando direto com o gateway e com o proprio cookie. */
export const test = base.extend<{ api: APIRequestContext }>({
  api: async ({}, usar) => {
    const api = await request.newContext({ baseURL: API });
    await usar(api);
    await api.dispose();
  },
});

export { expect };
