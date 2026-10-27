import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { ContaOuCartao, Fatura, Gasto } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mesPorExtenso } from '../../core/ui/datas';
import { fechamentoPadrao } from '../../core/ui/fatura';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';
import { Icone } from '../../core/ui/icone';

/** Contas e cartoes: no cartao, a fatura entre os fechamentos e o vencimento. */
@Component({
  selector: 'app-accounts',
  imports: [Icone, FormsModule, MoedaPipe, DataBrPipe],
  templateUrl: './accounts.html',
})
export class Accounts {
  private readonly finance = inject(FinanceService);

  protected readonly contas = signal<ContaOuCartao[]>([]);
  protected readonly erro = signal('');
  protected readonly ocupado = signal(false);

  protected readonly nome = signal('');
  protected readonly tipo = signal<'CONTA' | 'CARTAO'>('CARTAO');
  protected readonly fechamento = signal<number | null>(null);
  protected readonly vencimento = signal<number | null>(null);
  /** Conta ou cartao do casal: o que for lancado nela ja nasce compartilhado. */
  protected readonly compartilhada = signal(false);
  protected readonly pessoas = inject(PessoasDaFamilia);
  private readonly auth = inject(AuthService);
  protected readonly eu = computed(() => this.auth.usuario()?.id ?? null);

  /** Fatura aberta na tela: um cartao por vez. */
  protected readonly aberta = signal<{
    cartao: ContaOuCartao;
    fatura: Fatura;
    gastos: Gasto[];
  } | null>(null);

  constructor() {
    this.carregar();
  }

  /** O fechamento acompanha o vencimento (7 dias antes) ate a pessoa mudar ele a mao. */
  protected trocarVencimento(dia: number | null): void {
    const anterior = this.vencimento();
    const acompanhava =
      this.fechamento() === null ||
      (anterior !== null && this.fechamento() === fechamentoPadrao(anterior));
    this.vencimento.set(dia);
    if (acompanhava && this.diaValido(dia)) {
      this.fechamento.set(fechamentoPadrao(dia!));
    }
  }

  protected rotuloDoMes(mes: string): string {
    return mesPorExtenso(mes);
  }

  protected salvar(): void {
    const cartao = this.tipo() === 'CARTAO';
    if (this.nome().trim().length < 2) {
      this.erro.set($localize`Digite o nome da conta ou do cartão.`);
      return;
    }
    if (cartao && !this.diaValido(this.vencimento())) {
      this.erro.set($localize`Informe o dia do vencimento da fatura (1 a 31).`);
      return;
    }
    if (cartao && !this.diaValido(this.fechamento())) {
      this.erro.set($localize`Informe o dia do fechamento (1 a 31).`);
      return;
    }
    this.ocupado.set(true);
    this.erro.set('');
    this.finance
      .criarConta({
        nome: this.nome().trim(),
        tipo: this.tipo(),
        fechamento: cartao ? this.fechamento() : null,
        vencimento: cartao ? this.vencimento() : null,
        compartilhado: this.pessoas.temCompanhia() && this.compartilhada(),
      })
      .subscribe({
        next: () => {
          this.nome.set('');
          this.fechamento.set(null);
          this.vencimento.set(null);
          this.compartilhada.set(false);
          this.ocupado.set(false);
          this.carregar();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.ocupado.set(false);
        },
      });
  }

  /** So quem criou liga ou desliga; os gastos ja lancados nao mudam. */
  protected alternarCompartilhamento(conta: ContaOuCartao): void {
    this.finance.compartilharConta(conta.id, !conta.compartilhado).subscribe({
      next: () => this.carregar(),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluir(conta: ContaOuCartao): void {
    if (!confirm($localize`Apagar "${conta.nome}"? Os gastos continuam, só sem a conta.`)) {
      return;
    }
    this.finance.excluirConta(conta.id).subscribe({
      next: () => {
        this.aberta.set(null);
        this.carregar();
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  /** Abre a fatura do mes (AAAA-MM); o passo anda para a anterior ou a seguinte. */
  protected verFatura(cartao: ContaOuCartao, mes: string, passo = 0): void {
    const [ano, m] = mes.split('-').map(Number);
    const alvo = new Date(ano, m - 1 + passo, 1);
    const chave = `${alvo.getFullYear()}-${String(alvo.getMonth() + 1).padStart(2, '0')}`;
    this.finance.fatura(cartao.id, chave).subscribe({
      next: ({ fatura, gastos }) => this.aberta.set({ cartao, fatura, gastos }),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  /** So tira a fatura do "a pagar" (com as anteriores); os gastos ja contaram nas compras. */
  protected alternarPagamento(cartao: ContaOuCartao, fatura: Fatura): void {
    const pedido = fatura.paga
      ? this.finance.desfazerPagamentoDaFatura(cartao.id, fatura.mes)
      : this.finance.pagarFatura(cartao.id, fatura.mes);
    this.ocupado.set(true);
    this.erro.set('');
    pedido.subscribe({
      next: () => {
        this.ocupado.set(false);
        this.verFatura(cartao, fatura.mes);
        this.carregar();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.ocupado.set(false);
      },
    });
  }

  private diaValido(dia: number | null): boolean {
    return dia !== null && Number.isInteger(dia) && dia >= 1 && dia <= 31;
  }

  private carregar(): void {
    this.finance.contas().subscribe({
      next: (lista) => this.contas.set(lista),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
