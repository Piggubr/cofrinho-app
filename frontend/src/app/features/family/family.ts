import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { FamilyService } from '../../core/api/family.service';
import { ConviteDaFamilia, Familia, MembroDaFamilia, PigguRole } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

const PAPEIS: Record<PigguRole, string> = { ADMIN: 'Admin', TITULAR: 'Titular', MEMBRO: 'Membro' };

/**
 * Familia: quem faz parte, convites e saida.
 *
 * <p>Cada pessoa tem conta propria. O titular convida pelo e-mail; quem entra com
 * esse e-mail pela primeira vez cai na familia como membro.</p>
 */
@Component({
  selector: 'app-family',
  imports: [FormsModule, DataBrPipe],
  templateUrl: './family.html',
  styleUrl: './family.scss',
})
export class Family {
  private readonly familias = inject(FamilyService);
  protected readonly auth = inject(AuthService);

  protected readonly familia = signal<Familia | null>(null);
  protected readonly convitesRecebidos = signal<ConviteDaFamilia[]>([]);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly email = signal('');
  protected readonly nome = signal('');
  protected readonly ocupado = signal(false);

  constructor() {
    this.familias.ver().subscribe({
      next: (familia) => this.mostrar(familia),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
    this.familias.convitesParaMim().subscribe({
      next: (convites) => this.convitesRecebidos.set(convites),
      error: () => this.convitesRecebidos.set([]),
    });
  }

  /** Entrar em outra familia apaga o que e so seu na familia de agora. */
  protected aceitar(convite: ConviteDaFamilia): void {
    const texto =
      `Entrar na ${convite.familia}? Você sai da família atual: se for a última pessoa dela, ` +
      'tudo o que está lá é apagado; se outras pessoas ficarem, o que você lançou fica com elas. ' +
      'Depois disso é preciso entrar de novo.';
    if (!confirm(texto)) {
      return;
    }
    this.familias.aceitarConvite(convite.id).subscribe({
      next: () => void this.auth.encerrarLocalmente(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected papel(papel: PigguRole): string {
    return PAPEIS[papel];
  }

  protected convidar(): void {
    const email = this.email().trim();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      this.erro.set('Digite um e-mail válido.');
      return;
    }
    this.executar(
      this.familias.convidar(email),
      'Convite criado. Avise a pessoa para entrar com esse e-mail.',
    );
    this.email.set('');
  }

  protected renomear(): void {
    const nome = this.nome().trim();
    if (!nome) {
      this.erro.set('Digite o nome da família.');
      return;
    }
    this.executar(this.familias.renomear(nome), 'Nome salvo.');
  }

  protected cancelarConvite(id: string): void {
    this.executar(this.familias.cancelarConvite(id), 'Convite cancelado.');
  }

  protected remover(membro: MembroDaFamilia): void {
    if (!confirm(`Tirar ${membro.nome} da família? O que essa pessoa lançou continua aqui.`)) {
      return;
    }
    this.executar(this.familias.removerMembro(membro.id), 'Pessoa removida da família.');
  }

  protected sairDaFamilia(): void {
    if (!confirm('Sair da família? Você passa a ter uma família só sua e precisa entrar de novo.')) {
      return;
    }
    this.familias.sair().subscribe({
      next: () => void this.auth.sair(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private executar(pedido: Observable<Familia>, mensagem: string): void {
    this.ocupado.set(true);
    this.erro.set('');
    this.aviso.set('');
    pedido.subscribe({
      next: (familia) => {
        this.mostrar(familia);
        this.aviso.set(mensagem);
        this.ocupado.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.ocupado.set(false);
      },
    });
  }

  private mostrar(familia: Familia): void {
    this.familia.set(familia);
    this.nome.set(familia.nome);
  }
}
