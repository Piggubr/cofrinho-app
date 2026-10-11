import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { APP_CONFIG } from '../core/config/app-config';
import { Gasto, MembroDaFamilia } from '../core/api/models';
import { PessoasDaFamilia, nomeNaFamilia } from '../core/api/pessoas-da-familia';

/** Id da titular de teste (o mesmo do usuarioDeTeste) e de uma segunda pessoa da familia. */
export const ID_DA_TITULAR = '11111111-1111-1111-1111-111111111111';
export const ID_DA_BIA = '33333333-3333-3333-3333-333333333333';

const MEMBROS_DE_TESTE: MembroDaFamilia[] = [
  { id: ID_DA_TITULAR, nome: 'Titular', email: 'titular@piggu.test', foto: null, papel: 'TITULAR' },
  { id: ID_DA_BIA, nome: 'Bia', email: 'bia@piggu.test', foto: null, papel: 'MEMBRO' },
];

/** O basico de toda tela que fala com a API: HttpClient de teste, rotas, a config e os nomes. */
export const PROVEDORES_DE_TELA: (Provider | EnvironmentProviders)[] = [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideRouter([]),
  { provide: APP_CONFIG, useValue: { apiUrl: '/api', googleClientId: 'x' } },
  // Nomes sem ir a API: cada tela testa o proprio pedido, nao o da familia.
  {
    provide: PessoasDaFamilia,
    useValue: { nome: (id: string | null) => nomeNaFamilia(MEMBROS_DE_TESTE, id) },
  },
];

/** Escreve num campo e avisa o Angular, como a pessoa digitando. */
export function digitar(pagina: HTMLElement, seletor: string, valor: string): void {
  const campo = pagina.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
    seletor,
  );
  if (!campo) {
    throw new Error(`Campo nao encontrado: ${seletor}`);
  }
  campo.value = valor;
  campo.dispatchEvent(new Event(campo instanceof HTMLSelectElement ? 'change' : 'input'));
}

/** Clica no botao cujo texto (ou aria-label) contem o informado. */
export function clicar(pagina: HTMLElement, texto: string, indice = 0): void {
  const botoes = [...pagina.querySelectorAll<HTMLButtonElement>('button')].filter(
    (b) => b.textContent?.includes(texto) || b.getAttribute('aria-label') === texto,
  );
  if (!botoes[indice]) {
    throw new Error(`Botao nao encontrado: ${texto} (${indice})`);
  }
  botoes[indice].click();
}

/** Um gasto completo, para telas que so leem alguns campos. */
export function gastoDeTeste(mudancas: Partial<Gasto> = {}): Gasto {
  return {
    id: 'g1',
    data: '2026-10-01',
    reciboId: '',
    estabelecimento: 'Mercado',
    item: 'Compras',
    categoria: 'Mercado',
    valor: 10,
    tipo: 'Variável',
    origem: 'MANUAL',
    usuario: ID_DA_TITULAR,
    registradoEm: '2026-10-01T10:00:00Z',
    ...mudancas,
  };
}
