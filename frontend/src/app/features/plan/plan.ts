import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { BillingService } from '../../core/api/billing.service';
import { InfoDoPlano, Periodo } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { Icone } from '../../core/ui/icone';
import { Carregando } from '../../core/ui/carregando';

export type QualPlano = 'GRATUITO' | 'PREMIUM' | 'CASAL' | 'FAMILIA';

/**
 * Planos do Piggu.
 *
 * <p>O pagamento acontece na pagina do provedor. Voltar de la nao libera nada: o
 * Premium so muda quando o provedor avisa o backend, e o token novo traz o plano.</p>
 */
@Component({
  selector: 'app-plan',
  imports: [Icone, DataBrPipe, Carregando],
  templateUrl: './plan.html',
  styleUrl: './plan.scss',
})
export class Plan implements OnInit {
  private readonly billing = inject(BillingService);
  protected readonly auth = inject(AuthService);

  protected readonly info = signal<InfoDoPlano | null>(null);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly ocupado = signal(false);
  private readonly parametros = inject(ActivatedRoute).snapshot.queryParamMap;
  protected readonly voltouDoPagamento = this.parametros.get('assinatura') === 'ok';
  /** Campanha do link de divulgacao (/plano?promo=CODIGO): pode liberar o teste gratis de novo. */
  private readonly campanha = this.parametros.get('promo');

  /**
   * O cartao a marcar como "Seu plano". Nulo para o administrador, que usa tudo do Premium
   * sem ter plano nenhum.
   */
  protected seuPlano(plano: InfoDoPlano): QualPlano | null {
    if (plano.plano === 'GRATUITO') {
      return this.auth.ehPremium() ? null : 'GRATUITO';
    }
    if (plano.planoFamilia) {
      return 'FAMILIA';
    }
    return plano.planoCasal ? 'CASAL' : 'PREMIUM';
  }

  /** Nome do plano no quadro de cima, para quem ja e Premium. */
  protected nomeDoPlano(plano: InfoDoPlano): string {
    switch (this.seuPlano(plano)) {
      case 'FAMILIA':
        return $localize`Premium família`;
      case 'CASAL':
        return $localize`Premium casal`;
      default:
        return $localize`Premium individual`;
    }
  }

  ngOnInit(): void {
    void this.carregar();
  }

  /** Renova o token antes de ler o plano: e ele que leva o Premium aos outros servicos. */
  protected async carregar(): Promise<void> {
    this.erro.set('');
    try {
      if (this.voltouDoPagamento) {
        await firstValueFrom(this.auth.renovar());
      }
      this.info.set(await firstValueFrom(this.billing.plano(this.campanha)));
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha));
    }
  }

  protected async assinar(periodo: Periodo): Promise<void> {
    await this.abrir(() => this.billing.checkout(periodo, this.campanha));
  }

  /** Arrependimento (CDC art. 49): cancela agora e devolve tudo o que foi pago. */
  protected async pedirReembolso(): Promise<void> {
    if (!confirm($localize`Cancelar o Premium agora e receber de volta todo o valor pago?`)) {
      return;
    }
    this.ocupado.set(true);
    this.erro.set('');
    try {
      const { reembolsado } = await firstValueFrom(this.billing.reembolso());
      await firstValueFrom(this.auth.renovar());
      this.info.set(await firstValueFrom(this.billing.plano()));
      this.aviso.set(
        reembolsado
          ? $localize`Premium cancelado. O reembolso aparece na fatura do cartão em alguns dias.`
          : $localize`Premium cancelado. Você estava no teste grátis, então nada foi cobrado.`,
      );
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

  /**
   * Separado para os testes nao saírem da pagina. So segue para a Stripe: um endereco
   * de outro lugar (resposta adulterada) nunca tira a pessoa do Piggu.
   */
  protected irPara(url: string): void {
    if (!ehDaStripe(url)) {
      this.erro.set($localize`Endereço de pagamento inválido. Tente de novo.`);
      this.ocupado.set(false);
      return;
    }
    window.location.assign(url);
  }
}

/** Checkout e portal da Stripe: https e dominio stripe.com. */
export function ehDaStripe(url: string): boolean {
  try {
    const endereco = new URL(url);
    return (
      endereco.protocol === 'https:' &&
      (endereco.hostname === 'stripe.com' || endereco.hostname.endsWith('.stripe.com'))
    );
  } catch {
    return false;
  }
}
