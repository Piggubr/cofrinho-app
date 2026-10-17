import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FinanceService } from '../api/finance.service';
import { NovoLancamento } from '../api/models';
import { AuthService } from '../auth/auth.service';

const CHAVE = 'piggu_fila_de_gastos';

interface GastoNaFila {
  /** De quem e: so essa pessoa envia, mesmo que outra entre no mesmo aparelho. */
  usuario: string;
  pedido: NovoLancamento;
  guardadoEm: string;
}

/**
 * Gastos lancados sem sinal (na fila do mercado, no metro).
 *
 * <p>Ficam neste aparelho e vao para o servidor, na ordem, assim que a conexao volta:
 * no evento "online" do navegador, na abertura do app e quando a tela pede. Um envio
 * que falha por falta de rede para a fila ali; um que o servidor recusa (dado invalido)
 * sai da fila e conta em {@link recusados}, para a pessoa saber.</p>
 */
@Injectable({ providedIn: 'root' })
export class FilaDeGastos {
  private readonly finance = inject(FinanceService);
  private readonly auth = inject(AuthService);

  private readonly todos = signal<GastoNaFila[]>(ler());
  private enviando = false;

  /** Os da pessoa logada, esperando conexao. */
  readonly pendentes = computed(() =>
    this.todos().filter((g) => g.usuario === this.auth.usuario()?.id),
  );
  /** Quantos foram enviados no ultimo envio (para a tela recarregar e avisar). */
  readonly enviados = signal(0);
  readonly recusados = signal(0);

  constructor() {
    window.addEventListener('online', () => void this.enviar());
  }

  guardar(pedido: NovoLancamento): void {
    const usuario = this.auth.usuario()?.id;
    if (!usuario) {
      return;
    }
    this.todos.update((lista) => [
      ...lista,
      { usuario, pedido, guardadoEm: new Date().toISOString() },
    ]);
    gravar(this.todos());
  }

  async enviar(): Promise<void> {
    if (this.enviando || !this.pendentes().length) {
      return;
    }
    this.enviando = true;
    let enviados = 0;
    let recusados = 0;
    try {
      for (const gasto of this.pendentes()) {
        try {
          await firstValueFrom(this.finance.lancarGastos(gasto.pedido));
          enviados++;
        } catch (erro) {
          if (erro instanceof HttpErrorResponse && (erro.status === 0 || erro.status >= 500)) {
            break; // ainda sem rede (ou servidor fora): tenta de novo depois
          }
          recusados++;
        }
        this.todos.update((lista) => lista.filter((g) => g !== gasto));
        gravar(this.todos());
      }
    } finally {
      this.enviando = false;
      this.enviados.set(enviados);
      this.recusados.set(recusados);
    }
  }
}

/** Falha de rede: o pedido nem chegou. Vale guardar e mandar depois. */
export function semConexao(erro: unknown): boolean {
  return erro instanceof HttpErrorResponse && erro.status === 0;
}

function ler(): GastoNaFila[] {
  try {
    return JSON.parse(localStorage.getItem(CHAVE) ?? '[]') as GastoNaFila[];
  } catch {
    return [];
  }
}

function gravar(lista: GastoNaFila[]): void {
  try {
    if (lista.length) {
      localStorage.setItem(CHAVE, JSON.stringify(lista));
    } else {
      localStorage.removeItem(CHAVE);
    }
  } catch {
    // Sem armazenamento: a fila vale ate fechar o app.
  }
}
