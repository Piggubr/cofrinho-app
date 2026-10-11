import { Injectable, signal } from '@angular/core';

/**
 * Aviso de que algo foi lancado fora da tela aberta (pelo "+").
 *
 * <p>O painel de lancamento abre por cima de qualquer tela; as telas que mostram gastos
 * ou receitas observam {@link versao} e recarregam quando ela muda.</p>
 */
@Injectable({ providedIn: 'root' })
export class Lancamentos {
  private readonly contador = signal(0);
  readonly versao = this.contador.asReadonly();

  avisar(): void {
    this.contador.update((n) => n + 1);
  }
}
