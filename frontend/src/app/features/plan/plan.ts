import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { BillingService } from '../../core/api/billing.service';
import { InfoDoPlano, Periodo } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/**
 * Planos do Piggu.
 *
 * <p>O pagamento acontece na pagina do provedor. Voltar de la nao libera nada: o
 * Premium so muda quando o provedor avisa o backend, e o token novo traz o plano.</p>
 */
@Component({
  selector: 'app-plan',
  imports: [DataBrPipe],
  templateUrl: './plan.html',
  styleUrl: './plan.scss',
})
export class Plan {
  private readonly billing = inject(BillingService);
  protected readonly auth = inject(AuthService);

  protected readonly info = signal<InfoDoPlano | null>(null);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly ocupado = signal(false);
  protected readonly voltouDoPagamento =
    inject(ActivatedRoute).snapshot.queryParamMap.get('assinatura') === 'ok';

  constructor() {
    void this.carregar();
  }

  /** Renova o token antes de ler o plano: e ele que leva o Premium aos outros servicos. */
  protected async carregar(): Promise<void> {
    this.erro.set('');
    try {
      if (this.voltouDoPagamento) {
        await firstValueFrom(this.auth.renovar());
      }
      this.info.set(await firstValueFrom(this.billing.plano()));
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
    }
  }

  protected async assinar(periodo: Periodo): Promise<void> {
    await this.abrir(() => this.billing.checkout(periodo));
  }

  /** Arrependimento (CDC art. 49): cancela agora e devolve tudo o que foi pago. */
  protected async pedirReembolso(): Promise<void> {
    if (!confirm($localize`Cancelar o Premium agora e receber de volta todo o valor pago?`)) {
      return;
    }
    this.ocupado.set(true);
    this.erro.set('');
    try {
      await firstValueFrom(this.billing.reembolso());
      await firstValueFrom(this.auth.renovar());
      this.info.set(await firstValueFrom(this.billing.plano()));
      this.aviso.set($localize`Premium cancelado. O reembolso aparece na fatura do cartão em alguns dias.`);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
    } finally {
      this.ocupado.set(false);
    }
  }

  protected async gerenciar(): Promise<void> {
    await this.abrir(() => this.billing.portal());
  }

  private async abrir(pedido: () => ReturnType<BillingService['portal']>): Promise<void> {
    this.ocupado.set(true);
    this.erro.set('');
    try {
      this.irPara((await firstValueFrom(pedido())).url);
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
      this.ocupado.set(false);
    }
  }

  /** Separado para os testes nao saírem da pagina. */
  protected irPara(url: string): void {
    window.location.assign(url);
  }
}
