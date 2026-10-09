import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { ContaOuCartao, Fatura, Gasto } from '../../core/api/models';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mesPorExtenso } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe } from '../../core/ui/moeda';

/** Contas e cartoes: no cartao, a fatura entre os fechamentos e o vencimento. */
@Component({
  selector: 'app-accounts',
  imports: [FormsModule, MoedaPipe, DataBrPipe],
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

  /** Fatura aberta na tela: um cartao por vez. */
  protected readonly aberta = signal<{ cartao: ContaOuCartao; fatura: Fatura; gastos: Gasto[] } | null>(null);

  constructor() {
    this.carregar();
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
    if (cartao && (!this.diaValido(this.fechamento()) || !this.diaValido(this.vencimento()))) {
      this.erro.set($localize`Informe os dias de fechamento e de vencimento (1 a 31).`);
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
      })
      .subscribe({
        next: () => {
          this.nome.set('');
          this.fechamento.set(null);
          this.vencimento.set(null);
          this.ocupado.set(false);
          this.carregar();
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.ocupado.set(false);
        },
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
