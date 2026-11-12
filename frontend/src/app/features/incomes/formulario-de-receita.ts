import { Component, ElementRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { CampoDeValor } from '../../core/ui/campo-de-valor';
import { hojeIso } from '../../core/ui/datas';
import { TETO_DE_ENTRADA, confirmarData, erroDeData, erroDeValor } from '../../core/ui/limites';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Receita gravada, com a mensagem e o mes em que ela entrou. */
export interface ReceitaLancada {
  mensagem: string;
  mes: string;
}

/** Formulario completo de receita: o mesmo na tela de Receitas e no painel do "+". */
@Component({
  selector: 'app-formulario-de-receita',
  imports: [CampoDeValor, FormsModule],
  templateUrl: './formulario-de-receita.html',
  styles: `
    :host {
      display: block;
    }
    .repetir {
      margin-top: 0.85rem;
    }
  `,
})
export class FormularioDeReceita {
  private readonly finance = inject(FinanceService);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  protected readonly pessoas = inject(PessoasDaFamilia);
  private readonly auth = inject(AuthService);

  /** Prefixo dos ids: a tela de Receitas e o painel do "+" podem estar abertos juntos. */
  readonly prefixo = input('');
  readonly lancado = output<ReceitaLancada>();
  /** Apareceu um erro: quem abriu o formulario tira a mensagem de sucesso anterior. */
  readonly falhou = output<void>();

  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  protected readonly categorias = signal<string[]>([]);

  protected readonly data = signal(hojeIso());
  protected readonly descricao = signal('');
  protected readonly categoria = signal('Salário');
  protected readonly valor = signal<number | null>(null);
  /** Receita que entra sozinha todo mes no mesmo dia. */
  protected readonly repetir = signal(false);
  /** So na recorrente: o salario que entra em duas vezes; o valor acima e a primeira parcela. */
  protected readonly emDuasParcelas = signal(false);
  protected readonly segundoDia = signal<number | null>(null);
  protected readonly segundoValor = signal<number | null>(null);
  /** Nula segue o padrao do perfil. */
  protected readonly compartilhada = signal<boolean | null>(null);
  protected readonly compartilhadaEfetiva = computed(
    () =>
      this.compartilhada() ?? this.auth.usuario()?.compartilhamento?.compartilharPorPadrao ?? false,
  );
  protected readonly diaDaReceita = computed(() => Number(this.data().slice(8, 10)));
  /** Id do ultimo envio e o pedido que ele levou: tentar de novo igual reaproveita o id. */
  private envio: { pedido: string; id: string } | null = null;

  constructor() {
    effect(() => {
      if (this.erro()) this.falhou.emit();
    });
    this.finance
      .categoriasDeReceita()
      .subscribe({ next: (lista) => this.categorias.set(lista ?? []) });
  }

  focarValor(): void {
    (this.elemento.nativeElement as HTMLElement)
      .querySelector<HTMLInputElement>(`#${this.prefixo()}valor-receita`)
      ?.focus();
  }

  protected lancar(): void {
    const descricao = this.descricao().trim();
    const valor = this.valor();
    if (!descricao) {
      this.erro.set($localize`Digite a descrição da receita.`);
      return;
    }
    const erroNoValor = erroDeValor(valor, TETO_DE_ENTRADA);
    if (erroNoValor) {
      this.erro.set(erroNoValor);
      return;
    }
    const dividir = this.repetir() && this.emDuasParcelas();
    const segundoDia = this.segundoDia();
    const segundoValor = this.segundoValor();
    if (
      dividir &&
      (segundoDia === null || !Number.isInteger(segundoDia) || segundoDia < 1 || segundoDia > 31)
    ) {
      this.erro.set($localize`Informe o dia da 2ª parcela (1 a 31).`);
      return;
    }
    if (dividir && segundoValor === null) {
      this.erro.set($localize`Digite o valor da 2ª parcela.`);
      return;
    }
    const erroNaSegunda = dividir ? erroDeValor(segundoValor, TETO_DE_ENTRADA) : null;
    if (erroNaSegunda) {
      this.erro.set(erroNaSegunda);
      return;
    }
    const erroNaData = erroDeData(this.data());
    if (erroNaData) {
      this.erro.set(erroNaData);
      return;
    }
    if (!confirmarData(this.data())) {
      return;
    }
    const receita = {
      data: this.data(),
      descricao,
      categoria: this.categoria(),
      valor: valor!,
      compartilhado: this.pessoas.temCompanhia() && this.compartilhadaEfetiva(),
    };
    const parcela = dividir ? { segundoDia: segundoDia!, segundoValor: segundoValor! } : undefined;
    const pedido = JSON.stringify([receita, this.repetir(), parcela]);
    if (this.envio?.pedido !== pedido) {
      this.envio = { pedido, id: crypto.randomUUID() };
    }
    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .lancarReceita(receita, this.repetir(), parcela, this.envio.id)
      .subscribe({
        next: () => {
          const mensagem = this.repetir()
            ? dividir
              ? $localize`Receita lançada. Ela entra sozinha todo dia ${this.diaDaReceita()}:dia: e a 2ª parcela todo dia ${segundoDia}:segundoDia:.`
              : $localize`Receita lançada. Ela entra sozinha todo dia ${this.diaDaReceita()}:dia:.`
            : $localize`Receita lançada.`;
          const mes = this.data().slice(0, 7);
          this.envio = null;
          this.descricao.set('');
          this.valor.set(null);
          // Como no gasto (BUG-003): a proxima receita e de hoje.
          this.data.set(hojeIso());
          this.repetir.set(false);
          this.emDuasParcelas.set(false);
          this.segundoDia.set(null);
          this.segundoValor.set(null);
          this.compartilhada.set(null);
          this.salvando.set(false);
          this.lancado.emit({ mensagem, mes });
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }
}
