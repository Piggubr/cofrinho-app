import { Injectable, inject } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';
import { firstValueFrom } from 'rxjs';
import { UsersService } from '../api/users.service';

/**
 * Notificacao no iPhone para os avisos do dia (fatura, orcamento, conta do mes).
 *
 * <p>So existe no app iOS. Pede a permissao quando a pessoa liga o aviso no perfil e,
 * a cada abertura do app, renova o registro: o token do aparelho muda quando o sistema
 * quer, e o backend fica sempre com o ultimo.</p>
 */
@Injectable({ providedIn: 'root' })
export class AvisosNoCelular {
  private readonly users = inject(UsersService);
  private ouvindo = false;

  readonly disponivel =
    Capacitor.getPlatform() === 'ios' && Capacitor.isPluginAvailable('PushNotifications');

  /** @return se a pessoa deu a permissao */
  async ativar(): Promise<boolean> {
    if (!this.disponivel) {
      return false;
    }
    const permissao = await PushNotifications.requestPermissions();
    if (permissao.receive !== 'granted') {
      return false;
    }
    await this.registrar();
    return true;
  }

  /** Na abertura: so renova se a permissao ja foi dada, sem perguntar de novo. */
  async renovar(): Promise<void> {
    if (!this.disponivel) {
      return;
    }
    const permissao = await PushNotifications.checkPermissions();
    if (permissao.receive === 'granted') {
      await this.registrar();
    }
  }

  private async registrar(): Promise<void> {
    if (!this.ouvindo) {
      this.ouvindo = true;
      await PushNotifications.addListener('registration', ({ value }) => {
        firstValueFrom(this.users.registrarAparelho(value)).catch(() => {
          // Sem rede agora: a proxima abertura do app registra de novo.
        });
      });
    }
    await PushNotifications.register();
  }
}
