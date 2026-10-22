import { Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FinanceService } from '../../core/api/finance.service';
import { PessoasDaFamilia } from '../../core/api/pessoas-da-familia';
import { AuthService } from '../../core/auth/auth.service';
import { hojeIso } from '../../core/ui/datas';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

/** Receita gravada, com a mensagem e o mes em que ela entrou. */
export interface ReceitaLancada {
  mensagem: string;
  mes: string;
}

/** Formulario completo de receita: o mesmo na tela de Receitas e no painel do "+". */
@Component({
  selector: 'app-formulario-de-receita',
  imports: [FormsModule],
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

  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  protected readonly categorias = signal<string[]>([]);

  protected readonly data = signal(hojeIso());
  protected readonly descricao = signal('');
  protected readonly categoria = signal('Salário');
  protected readonly valor = signal<number | null>(null);
  /** Receita que entra sozinha todo mes no mesmo dia. */
  protected readonly repetir = signal(false);
  /** Nula segue o padrao do perfil. */
  protected readonly compartilhada = signal<boolean | null>(null);
  protected readonly compartilhadaEfetiva = computed(
    () => this.compartilhada() ?? this.auth.usuario()?.compartilhamento?.compartilharPorPadrao ?? false,
  );
  protected readonly diaDaReceita = computed(() => Number(this.data().slice(8, 10)));

  constructor() {
    this.finance.categoriasDeReceita().subscribe({ next: (lista) => this.categorias.set(lista ?? []) });
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
    if (valor === null || valor <= 0) {
      this.erro.set($localize`Digite um valor válido.`);
      return;
    }
    this.salvando.set(true);
    this.erro.set('');
    this.finance
      .lancarReceita(
        {
          data: this.data(),
          descricao,
          categoria: this.categoria(),
          valor,
          compartilhado: this.pessoas.temCompanhia() && this.compartilhadaEfetiva(),
        },
        this.repetir(),
      )
      .subscribe({
        next: () => {
          const mensagem = this.repetir()
            ? $localize`Receita lançada. Ela entra sozinha todo dia ${this.diaDaReceita()}:dia:.`
            : $localize`Receita lançada.`;
          this.descricao.set('');
          this.valor.set(null);
          this.repetir.set(false);
          this.compartilhada.set(null);
          this.salvando.set(false);
          this.lancado.emit({ mensagem, mes: this.data().slice(0, 7) });
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }
}
