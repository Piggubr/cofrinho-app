import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BankingService } from '../api/banking.service';
import { ContaBancaria } from '../api/models';

/** Recorte minimo do widget Pluggy Connect que este app usa. */
interface PluggyConnectOpcoes {
  connectToken: string;
  includeSandbox?: boolean;
  onSuccess: (dados: { item: { id: string } }) => void;
  onError: (erro: { message?: string }) => void;
  onClose?: () => void;
}

declare global {
  interface Window {
    PluggyConnect?: new (opcoes: PluggyConnectOpcoes) => { init(): Promise<void> | void };
  }
}

// Versao fixa com hash de integridade: e o script que recebe a senha do banco do
// usuario, entao o navegador recusa qualquer byte diferente do que foi conferido.
// Para atualizar: baixe a nova versao, gere o sha384 e troque os dois juntos.
const URL_SCRIPT = 'https://cdn.pluggy.ai/pluggy-connect/v2.11.0/pluggy-connect.js';
const INTEGRIDADE = 'sha384-rDtJqPxBmZdyFCZ0p2es0NV3LAJtP1T7bhxJEKTYrz1QNNQVeOjf1AJX46jG6GDh';

/**
 * Abre o widget da Pluggy para o usuario conectar um banco.
 *
 * <p>O script so e baixado quando o usuario pede para conectar, e nao no painel
 * inteiro: cada recurso de terceiro carregado entrega IP e navegador a quem o serve.</p>
 */
@Injectable({ providedIn: 'root' })
export class PluggyConnectService {
  private readonly banking = inject(BankingService);
  private carregamento?: Promise<NonNullable<Window['PluggyConnect']>>;

  /**
   * Fluxo completo, depois que a pessoa autorizou: pede o token (gravando a
   * autorizacao), abre o widget e registra o banco conectado.
   *
   * @returns as contas atualizadas, ou null se o usuario fechou sem conectar
   */
  async conectar(versaoDoAviso: string): Promise<ContaBancaria[] | null> {
    // O script da Pluggy so carrega depois que o backend aceitou a autorizacao.
    const token = await firstValueFrom(this.banking.gerarConnectToken(versaoDoAviso));
    const PluggyConnect = await this.carregar();

    const itemId = await new Promise<string | null>((resolver, rejeitar) => {
      const widget = new PluggyConnect({
        connectToken: token.accessToken,
        includeSandbox: token.sandbox,
        onSuccess: (dados) => resolver(dados.item.id),
        onError: (erro) =>
          rejeitar(new Error(erro?.message || $localize`Nao foi possivel conectar o banco.`)),
        onClose: () => resolver(null),
      });
      widget.init();
    });

    return itemId ? firstValueFrom(this.banking.registrarItem(itemId)) : null;
  }

  private carregar(): Promise<NonNullable<Window['PluggyConnect']>> {
    if (this.carregamento) {
      return this.carregamento;
    }

    this.carregamento = new Promise((resolver, rejeitar) => {
      if (window.PluggyConnect) {
        resolver(window.PluggyConnect);
        return;
      }

      const script = document.createElement('script');
      script.src = URL_SCRIPT;
      script.integrity = INTEGRIDADE;
      script.crossOrigin = 'anonymous';
      script.async = true;
      script.onload = () =>
        window.PluggyConnect
          ? resolver(window.PluggyConnect)
          : rejeitar(new Error($localize`O widget da Pluggy carregou incompleto.`));
      script.onerror = () => {
        this.carregamento = undefined;
        rejeitar(new Error($localize`Nao consegui carregar o widget da Pluggy.`));
      };
      document.head.appendChild(script);
    });

    return this.carregamento;
  }
}
