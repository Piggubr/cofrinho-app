import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { MetaDoMes, NovaMetaDoMes } from '../../core/api/models';
import { mesKey, mesPorExtenso } from '../../core/ui/datas';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { AnelDasMetas } from './anel-das-metas';

/** Quanto do mes ja foi cumprido: metas concluidas sobre o total, de 0 a 100. */
export function percentualConcluido(metas: readonly MetaDoMes[]): number {
  if (!metas.length) {
    return 0;
  }
  return Math.round((metas.filter((meta) => meta.concluida).length / metas.length) * 100);
}

/** Texto curto para mandar por mensagem: so titulo e progresso, nada de dinheiro. */
export function textoParaCompartilhar(metas: readonly MetaDoMes[], rotuloDoMes: string): string {
  const linhas = metas.map(
    (meta) => `${meta.concluida ? '✅' : '⏳'} ${meta.titulo}: ${meta.atual} de ${meta.alvo}`,
  );
  // O ":" fica fora: logo depois de uma interpolacao ele abriria metadados do $localize.
  return [$localize`Minhas metas de ${rotuloDoMes}` + ':', ...linhas].join('\n');
}

/**
 * Minhas metas do mes: metas pessoais com alvo e progresso, como no app antigo.
 *
 * <p>Sao so de quem criou (nem a familia ve) e valem para o mes: quando ele vira, a
 * lista comeca vazia de novo. O anel conta metas concluidas, nao a soma do progresso,
 * para uma meta grande nao esconder as pequenas.</p>
 */
@Component({
  selector: 'app-minhas-metas',
  imports: [FormsModule, Icone, AnelDasMetas],
  templateUrl: './minhas-metas.html',
  styleUrl: './minhas-metas.scss',
})
export class MinhasMetas {
  private readonly lifestyle = inject(LifestyleService);

  protected readonly rotuloDoMes = mesPorExtenso(mesKey(new Date()));

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly aviso = signal('');
  protected readonly salvando = signal(false);
  protected readonly metas = signal<MetaDoMes[]>([]);

  protected readonly formularioAberto = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly titulo = signal('');
  protected readonly alvo = signal<number | null>(null);
  protected readonly atual = signal<number | null>(0);

  protected readonly concluidas = computed(() => this.metas().filter((m) => m.concluida).length);
  protected readonly percentual = computed(() => percentualConcluido(this.metas()));
  protected readonly mensagem = computed(() => {
    const total = this.metas().length;
    const feitas = this.concluidas();
    if (!total) {
      return $localize`Crie sua jornada de metas.`;
    }
    if (feitas === total) {
      return $localize`Você concluiu todas as metas do mês.`;
    }
    return $localize`Continue no seu ritmo.`;
  });

  constructor() {
    this.lifestyle.listarMetasDoMes().subscribe({
      next: (metas) => {
        this.metas.set(metas);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  protected progresso(meta: MetaDoMes): number {
    return Math.min(100, Math.round((meta.atual / meta.alvo) * 100));
  }

  protected abrirNova(): void {
    this.editandoId.set(null);
    this.titulo.set('');
    this.alvo.set(null);
    this.atual.set(0);
    this.formularioAberto.set(true);
  }

  protected editar(meta: MetaDoMes): void {
    this.editandoId.set(meta.id);
    this.titulo.set(meta.titulo);
    this.alvo.set(meta.alvo);
    this.atual.set(meta.atual);
    this.formularioAberto.set(true);
  }

  protected fechar(): void {
    this.formularioAberto.set(false);
    this.editandoId.set(null);
  }

  protected salvar(): void {
    const titulo = this.titulo().trim();
    const alvo = Number(this.alvo());
    const atual = Number(this.atual() ?? 0);
    if (!titulo) {
      this.erro.set($localize`Dê um nome para a meta.`);
      return;
    }
    if (!Number.isInteger(alvo) || alvo < 1) {
      this.erro.set($localize`A quantidade desejada precisa ser um número inteiro a partir de 1.`);
      return;
    }
    if (!Number.isInteger(atual) || atual < 0) {
      this.erro.set($localize`O progresso precisa ser um número inteiro, zero ou mais.`);
      return;
    }
    this.gravar(this.editandoId(), { titulo, alvo, atual }, () => this.fechar());
  }

  /** O atalho do dia a dia: mais um passo sem abrir o formulario. */
  protected avancar(meta: MetaDoMes): void {
    this.gravar(meta.id, { titulo: meta.titulo, alvo: meta.alvo, atual: meta.atual + 1 });
  }

  protected excluir(meta: MetaDoMes): void {
    if (!confirm($localize`Apagar a meta "${meta.titulo}"?`)) {
      return;
    }
    this.lifestyle.excluirMetaDoMes(meta.id).subscribe({
      next: () => this.metas.update((lista) => lista.filter((m) => m.id !== meta.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  /** Pelo menu de compartilhar do aparelho; sem ele, copia o texto. */
  protected async compartilhar(): Promise<void> {
    const texto = textoParaCompartilhar(this.metas(), this.rotuloDoMes);
    this.aviso.set('');
    this.erro.set('');
    try {
      if (navigator.share) {
        await navigator.share({ title: $localize`Minhas metas`, text: texto });
        return;
      }
      await navigator.clipboard.writeText(texto);
      this.aviso.set($localize`Metas copiadas. Agora é só colar na conversa.`);
    } catch (falha) {
      // Fechar o menu de compartilhar sem escolher nada nao e erro.
      if ((falha as DOMException)?.name !== 'AbortError') {
        this.erro.set($localize`Não consegui compartilhar agora.`);
      }
    }
  }

  private gravar(id: string | null, meta: NovaMetaDoMes, depois?: () => void): void {
    this.erro.set('');
    this.salvando.set(true);
    const chamada = id
      ? this.lifestyle.atualizarMetaDoMes(id, meta)
      : this.lifestyle.criarMetaDoMes(meta);
    chamada.subscribe({
      next: (salva) => {
        this.metas.update((lista) =>
          id ? lista.map((m) => (m.id === id ? salva : m)) : [...lista, salva],
        );
        this.salvando.set(false);
        depois?.();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }
}
