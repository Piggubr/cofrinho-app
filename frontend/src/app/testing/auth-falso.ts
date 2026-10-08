import { computed, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Preferencias, Usuario } from '../core/api/models';

/** Usuario de teste com as preferencias padrao de uma conta nova. */
export function usuarioDeTeste(mudancas: Partial<Usuario> = {}): Usuario {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'titular@piggu.test',
    nome: 'Titular',
    primeiroNome: 'Titular',
    apelido: null,
    foto: null,
    role: 'TITULAR',
    ativo: true,
    permissoes: {},
    preferencias: { moeda: 'EUR', moedaConversao: 'BRL', mostrarCotacao: true },
    plano: 'PREMIUM',
    premiumAte: '2026-12-31T00:00:00Z',
    familia: '22222222-2222-2222-2222-222222222222',
    ...mudancas,
  };
}

/**
 * AuthService falso para testes de tela: so o estado que as telas leem, sem Google,
 * storage nem roteador.
 */
export class AuthFalso {
  readonly usuario = signal<Usuario | null>(usuarioDeTeste());
  readonly ehMembro = computed(() => this.usuario()?.role === 'MEMBRO');
  readonly ehAdmin = computed(() => this.usuario()?.role === 'ADMIN');
  readonly ehTitular = computed(() => this.usuario()?.role === 'TITULAR' || this.ehAdmin());
  readonly ehPremium = computed(
    () => this.usuario()?.role === 'ADMIN' || this.usuario()?.plano === 'PREMIUM',
  );
  readonly renovacoes = signal(0);

  renovar(): Observable<unknown> {
    this.renovacoes.update((n) => n + 1);
    return of({});
  }

  atualizarUsuario(usuario: Usuario): void {
    this.usuario.set(usuario);
  }

  preferir(preferencias: Partial<Preferencias>): void {
    const atual = this.usuario()!;
    this.usuario.set({ ...atual, preferencias: { ...atual.preferencias, ...preferencias } });
  }

  async sair(): Promise<void> {}
}
