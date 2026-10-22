import { Injectable, Pipe, PipeTransform, computed, effect, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { FamilyService } from './family.service';
import { MembroDaFamilia } from './models';

/** Autor das mudancas feitas pelo proprio Piggu (conta fixa automatica, resgate). */
export const SISTEMA = '00000000-0000-0000-0000-000000000000';

/**
 * Nome de quem esta na familia, a partir do id.
 *
 * <p>A API marca quem lancou pelo id da conta, nunca pelo e-mail: o e-mail fica so no
 * identity. Para mostrar "Ana", a tela pergunta aqui; os membros vem uma vez por familia.</p>
 */
export function nomeNaFamilia(
  membros: readonly MembroDaFamilia[],
  id: string | null | undefined,
): string {
  if (id === SISTEMA) {
    return 'Piggu';
  }
  return membros.find((membro) => membro.id === id)?.nome ?? $localize`Ex-membro`;
}

@Injectable({ providedIn: 'root' })
export class PessoasDaFamilia {
  private readonly familias = inject(FamilyService);
  private readonly auth = inject(AuthService);
  private readonly membros = signal<MembroDaFamilia[] | null>(null);
  private familiaCarregada: string | null = null;

  constructor() {
    // Outra pessoa entrou (ou a mesma trocou de familia): os nomes sao outros.
    effect(() => {
      const familia = this.auth.usuario()?.familia ?? null;
      if (familia === this.familiaCarregada) {
        return;
      }
      this.familiaCarregada = familia;
      this.membros.set(null);
      if (familia) {
        this.familias.ver().subscribe({
          next: (resposta) => this.membros.set(resposta.membros),
          error: () => this.membros.set([]),
        });
      }
    });
  }

  /** Mora mais alguem na casa: so assim faz sentido oferecer "compartilhado". */
  readonly temCompanhia = computed(() => (this.membros()?.length ?? 0) > 1);

  /** O outro do casal: para o titular, o parceiro; para o parceiro, o titular. */
  readonly parceiro = computed(() => {
    const eu = this.auth.usuario();
    const outros = (this.membros() ?? []).filter((membro) => membro.id !== eu?.id);
    const doOutroLado = (membro: MembroDaFamilia): boolean =>
      eu?.role === 'PARCEIRO'
        ? membro.papel === 'TITULAR' || membro.papel === 'ADMIN'
        : (eu?.role === 'TITULAR' || eu?.role === 'ADMIN') && membro.papel === 'PARCEIRO';
    const candidatos = outros.filter(doOutroLado);
    return candidatos.find((membro) => membro.papel === 'TITULAR') ?? candidatos[0] ?? null;
  });

  /** Vazio enquanto os membros nao chegam, para a tela nao piscar "Ex-membro". */
  nome(id: string | null | undefined): string {
    const membros = this.membros();
    if (membros === null && id !== SISTEMA) {
      return '';
    }
    return nomeNaFamilia(membros ?? [], id);
  }
}

/** {{ gasto.usuario | pessoa }} mostra o nome de quem lancou. */
@Pipe({ name: 'pessoa', pure: false })
export class PessoaPipe implements PipeTransform {
  private readonly pessoas = inject(PessoasDaFamilia);

  transform(id: string | null | undefined): string {
    return this.pessoas.nome(id);
  }
}
