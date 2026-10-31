import { Component, inject, signal } from '@angular/core';
import { PluggyConnectService } from '../../core/banking/pluggy-connect.service';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Para onde o app nativo espera a resposta: o ASWebAuthenticationSession do iOS fecha nela, e o Android abre o app. */
const VOLTA = 'piggu://banco-conectado';

/**
 * Conectar banco pelo app nativo (D-091).
 *
 * <p>O widget da Pluggy so existe em JavaScript e avisa o resultado por callback, sem
 * redirecionar. O app iOS ou Android pede o token (gravando a autorizacao da pessoa), abre esta pagina
 * num navegador do sistema com o token no fragmento (#token=..., que nao vai ao servidor nem
 * aos logs) e recebe o item de volta em piggu://banco-conectado. A pagina nao tem sessao e
 * nao chama a API: so abre o widget e devolve o que ele disse.</p>
 */
@Component({
  selector: 'app-conectar-banco',
  template: `
    <main class="conectar">
      <h1 i18n>Conectar banco</h1>
      @if (erro()) {
        <p class="aviso erro" role="alert">{{ erro() }}</p>
      } @else {
        <p i18n role="status">Abrindo a Pluggy para você entrar no seu banco…</p>
      }
      <p>
        <a i18n class="botao contorno" [href]="volta()">Voltar ao app</a>
      </p>
    </main>
  `,
  styles: `
    .conectar {
      max-width: 32rem;
      margin: 3rem auto;
      padding: 0 1rem;
      text-align: center;
    }
  `,
})
export class ConectarBanco {
  private readonly pluggy = inject(PluggyConnectService);

  protected readonly erro = signal('');
  protected readonly volta = signal(`${VOLTA}?fechou=1`);

  constructor() {
    const parametros = new URLSearchParams(window.location.hash.slice(1));
    const token = parametros.get('token');
    // O token nao fica no historico do navegador.
    history.replaceState(null, '', window.location.pathname);
    if (!token) {
      this.erro.set($localize`Este link não veio do app do Piggu. Abra a conexão pelo app.`);
      return;
    }
    void this.abrir(token, parametros.get('sandbox') === '1');
  }

  private async abrir(token: string, sandbox: boolean): Promise<void> {
    try {
      const item = await this.pluggy.abrirWidget(token, sandbox);
      // O Chrome do Android pode barrar a ida sozinha ao app: o botao leva o mesmo resultado.
      this.volta.set(item ? `${VOLTA}?item=${encodeURIComponent(item)}` : `${VOLTA}?fechou=1`);
      this.ir(this.volta());
    } catch (falha) {
      const mensagem = mensagemDeErro(falha, falha instanceof Error ? falha.message : undefined);
      this.erro.set(mensagem);
      this.volta.set(`${VOLTA}?erro=${encodeURIComponent(mensagem)}`);
      this.ir(this.volta());
    }
  }

  protected ir(destino: string): void {
    window.location.href = destino;
  }
}
