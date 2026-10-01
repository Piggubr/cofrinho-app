import { computed, signal } from '@angular/core';
import { Preferencias, Usuario } from '../core/api/models';

/** Usuario de teste com as preferencias padrao de uma conta nova. */
export function usuarioDeTeste(mudancas: Partial<Usuario> = {}): Usuario {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'beatriz@piggu.test',
    nome: 'Beatriz',
    primeiroNome: 'Beatriz',
    apelido: null,
    foto: null,
    role: 'BEATRIZ',
    ativo: true,
    permissoes: {},
    preferencias: { moeda: 'EUR', moedaConversao: 'BRL', mostrarCotacao: true },
    ...mudancas,
  };
}

/**
 * AuthService falso para testes de tela: so o estado que as telas leem, sem Google,
 * storage nem roteador.
 */
export class AuthFalso {
  readonly usuario = signal<Usuario | null>(usuarioDeTeste());
  readonly ehFamiliar = computed(() => this.usuario()?.role === 'FAMILIAR');
  readonly ehAdmin = computed(() => this.usuario()?.role === 'ADMIN');

  atualizarUsuario(usuario: Usuario): void {
    this.usuario.set(usuario);
  }

  preferir(preferencias: Partial<Preferencias>): void {
    const atual = this.usuario()!;
    this.usuario.set({ ...atual, preferencias: { ...atual.preferencias, ...preferencias } });
  }

  async sair(): Promise<void> {}
}
