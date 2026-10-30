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
 * que o servidor recusa por dado invalido (400 ou 422) sai da fila e conta em
 * {@link recusados}, para a pessoa saber; qualquer outra falha (sem rede, servidor fora,
 * sessao encerrada) para a fila ali e guarda o resto.</p>
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
    // Outra aba mexeu na fila: le de novo para nao gravar por cima do que ela guardou.
    window.addEventListener('storage', (evento) => {
      if (evento.key === CHAVE) {
        this.todos.set(ler());
      }
    });
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
          if (!recusado(erro)) {
            break;
          }
          recusados++;
        }
        // Pelo conteudo: a lista pode ter sido relida de outra aba durante o envio.
        const enviado = JSON.stringify(gasto);
        this.todos.update((lista) => lista.filter((g) => JSON.stringify(g) !== enviado));
        gravar(this.todos());
      }
    } finally {
      this.enviando = false;
      this.enviados.set(enviados);
      this.recusados.set(recusados);
    }
  }
}

/**
 * So 400 e 422 dizem que o gasto e invalido, e o 404 do Piggu que a conta ou o cartao foi apagado
 * (D-147): nunca vai passar. Sessao encerrada, limite, sem rede ou 404 de proxy (sem o codigo): depois.
 */
function recusado(erro: unknown): boolean {
  return (
    erro instanceof HttpErrorResponse &&
    (erro.status === 400 || erro.status === 422 || (erro.status === 404 && erro.error?.codigo === 'NotFoundException'))
  );
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
