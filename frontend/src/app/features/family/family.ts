import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { FamilyService } from '../../core/api/family.service';
import { HistoryService } from '../../core/api/history.service';
import {
  ConviteDaFamilia,
  EventoDeAuditoria,
  Familia,
  MembroDaFamilia,
  PigguRole,
} from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

const ACOES: Record<EventoDeAuditoria['acao'], string> = {
  CRIOU: $localize`lançou`,
  EDITOU: $localize`alterou`,
  APAGOU: $localize`apagou`,
  IMPORTOU: $localize`importou`,
  PAGOU: $localize`pagou`,
  MUDOU_PAPEL: $localize`mudou o papel de`,
  REMOVEU: $localize`tirou da família`,
  ENTROU: $localize`entrou na família`,
  SAIU: $localize`saiu da família`,
  CONVIDOU: $localize`convidou`,
};

const ENTIDADES: Record<string, string> = {
  gasto: $localize`gasto`,
  receita: $localize`receita`,
  meta: $localize`meta do mês`,
  orcamento: $localize`orçamento`,
  'conta-fixa': $localize`conta fixa`,
  deposito: $localize`depósito no cofrinho`,
  pessoa: '',
};

const PAPEIS: Record<PigguRole, string> = {
  ADMIN: $localize`Admin`,
  TITULAR: $localize`Titular`,
  PARCEIRO: $localize`Parceiro`,
  MEMBRO: $localize`Membro`,
};

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
  private readonly historicoDaApi = inject(HistoryService);
  protected readonly auth = inject(AuthService);

  protected readonly familia = signal<Familia | null>(null);
  protected readonly convitesRecebidos = signal<ConviteDaFamilia[]>([]);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly email = signal('');
  protected readonly nome = signal('');
  protected readonly ocupado = signal(false);
  /** Nulo ate a pessoa pedir: o historico so e buscado quando alguem quer ver. */
  protected readonly historico = signal<EventoDeAuditoria[] | null>(null);

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
      $localize`Entrar na ${convite.familia}? Você sai da família atual: se for a última pessoa dela, ` +
      $localize`tudo o que está lá é apagado; se outras pessoas ficarem, o que você lançou fica com elas. ` +
      $localize`Depois disso é preciso entrar de novo.`;
    if (!confirm(texto)) {
      return;
    }
    this.familias.aceitarConvite(convite.id).subscribe({
      next: () => void this.auth.encerrarLocalmente(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected verHistorico(): void {
    this.historicoDaApi.daFamilia().subscribe((eventos) => this.historico.set(eventos));
  }

  /** "Bia apagou gasto" com o nome de quem esta na familia; quem saiu aparece pelo e-mail. */
  protected descrever(evento: EventoDeAuditoria): string {
    const autor =
      evento.autor === 'sistema'
        ? 'Piggu'
        : evento.autor === 'conta-excluida'
          ? $localize`Conta excluída`
          : (this.familia()?.membros.find((m) => m.email === evento.autor)?.nome ?? evento.autor);
    const entidade = ENTIDADES[evento.entidade] ?? evento.entidade;
    return [autor, ACOES[evento.acao] ?? evento.acao, entidade].filter(Boolean).join(' ');
  }

  protected quando(evento: EventoDeAuditoria): string {
    return new Date(evento.quando).toLocaleString('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  }

  protected papel(papel: PigguRole): string {
    return PAPEIS[papel];
  }

  protected convidar(): void {
    const email = this.email().trim();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      this.erro.set($localize`Digite um e-mail válido.`);
      return;
    }
    this.executar(
      this.familias.convidar(email),
      $localize`Convite criado. Avise a pessoa para entrar com esse e-mail.`,
    );
    this.email.set('');
  }

  protected renomear(): void {
    const nome = this.nome().trim();
    if (!nome) {
      this.erro.set($localize`Digite o nome da família.`);
      return;
    }
    this.executar(this.familias.renomear(nome), $localize`Nome salvo.`);
  }

  protected cancelarConvite(id: string): void {
    this.executar(this.familias.cancelarConvite(id), $localize`Convite cancelado.`);
  }

  /** O parceiro lanca e edita como o titular, mas nao mexe no plano nem nas pessoas. */
  protected mudarPapel(membro: MembroDaFamilia): void {
    const papel = membro.papel === 'PARCEIRO' ? 'MEMBRO' : 'PARCEIRO';
    const texto =
      papel === 'PARCEIRO'
        ? $localize`Tornar ${membro.nome} parceiro? Essa pessoa passa a lançar e editar gastos, receitas, contas e orçamentos da família. Plano, convites e pessoas continuam só com você.`
        : $localize`Voltar ${membro.nome} a membro? Essa pessoa passa a ver só o painel e o cofrinho.`;
    if (!confirm(texto)) {
      return;
    }
    this.executar(
      this.familias.mudarPapel(membro.id, papel),
      $localize`Papel alterado. A mudança vale quando a pessoa entrar de novo.`,
    );
  }

  protected remover(membro: MembroDaFamilia): void {
    if (
      !confirm($localize`Tirar ${membro.nome} da família? O que essa pessoa lançou continua aqui.`)
    ) {
      return;
    }
    this.executar(this.familias.removerMembro(membro.id), $localize`Pessoa removida da família.`);
  }

  protected sairDaFamilia(): void {
    if (
      !confirm(
        $localize`Sair da família? Você passa a ter uma família só sua e precisa entrar de novo.`,
      )
    ) {
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
