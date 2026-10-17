import { Component, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { FinanceService } from '../../core/api/finance.service';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Retirar as autorizacoes de envio ao Google (Gemini): a foto da nota e as perguntas ao
 * assistente. A LGPD (art. 8, par. 5, e art. 18, IX) pede que retirar seja tao facil
 * quanto autorizar; depois disso o app volta a perguntar antes de enviar.
 */
@Component({
  selector: 'app-autorizacoes-de-ia',
  template: `
    @if (aviso()) {
      <p class="aviso sucesso" role="status">{{ aviso() }}</p>
    }
    @if (erro()) {
      <p class="aviso erro" role="alert">{{ erro() }}</p>
    }
    <p i18n class="linha-detalhe">
      Você autoriza o envio ao Google (Gemini) na primeira vez que lê uma nota pela foto ou pergunta
      ao assistente. Pode retirar quando quiser; o Piggu volta a perguntar antes de enviar.
    </p>
    <div class="acoes">
      <button
        i18n
        type="button"
        class="botao contorno"
        [disabled]="retirando()"
        (click)="retirar('nota')"
      >
        Retirar autorização da foto da nota
      </button>
      <button
        i18n
        type="button"
        class="botao contorno"
        [disabled]="retirando()"
        (click)="retirar('assistente')"
      >
        Retirar autorização do assistente
      </button>
    </div>
  `,
})
export class AutorizacoesDeIa {
  private readonly finance = inject(FinanceService);

  protected readonly retirando = signal(false);
  protected readonly aviso = signal('');
  protected readonly erro = signal('');

  protected retirar(qual: 'nota' | 'assistente'): void {
    const pedido: Observable<void> =
      qual === 'nota'
        ? this.finance.retirarAutorizacaoDaLeituraDeNota()
        : this.finance.retirarAutorizacaoDoAssistente();
    this.retirando.set(true);
    this.aviso.set('');
    this.erro.set('');
    pedido.subscribe({
      next: () => {
        this.retirando.set(false);
        this.aviso.set(
          qual === 'nota'
            ? $localize`Pronto. A foto da nota só vai ao Google se você autorizar de novo.`
            : $localize`Pronto. O assistente só envia dados ao Google se você autorizar de novo.`,
        );
      },
      error: (falha) => {
        this.retirando.set(false);
        this.erro.set(mensagemDeErro(falha));
      },
    });
  }
}
