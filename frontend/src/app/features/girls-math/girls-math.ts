import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { MediaService } from '../../core/api/media.service';
import { ItemDoGirlsMath } from '../../core/api/models';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { hojeIso, mesKey } from '../../core/ui/datas';
import { Icone } from '../../core/ui/icone';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { Carregando } from '../../core/ui/carregando';
import { Vazio } from '../../core/ui/vazio';
import { CampoDeValor } from '../../core/ui/campo-de-valor';

/** Quanto a pessoa deixou de gastar no mes (AAAA-MM) e desde sempre. */
export function totaisDoGirlsMath(
  itens: readonly ItemDoGirlsMath[],
  mes: string,
): { noMes: number; total: number } {
  let noMes = 0;
  let total = 0;
  for (const item of itens) {
    total += item.valor;
    if (item.data.startsWith(mes)) {
      noMes += item.valor;
    }
  }
  return { noMes, total };
}

/**
 * Girls Math: o que a pessoa viu, quis e nao comprou, e quanto isso deixou no bolso.
 *
 * <p>Como no app antigo: produto, loja, valor e uma foto opcional. E so de quem lancou;
 * nem a familia ve. Nao vira gasto nem receita, e so um placar de economia. O membro da
 * familia usa sem foto.</p>
 */
@Component({
  selector: 'app-girls-math',
  imports: [CampoDeValor, FormsModule, Icone, MoedaPipe, DataBrPipe, Carregando, Vazio],
  templateUrl: './girls-math.html',
  styleUrl: './girls-math.scss',
})
export class GirlsMath {
  protected readonly moeda = inject(MoedaService);
  private readonly lifestyle = inject(LifestyleService);
  private readonly media = inject(MediaService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  protected readonly itens = signal<ItemDoGirlsMath[]>([]);

  protected readonly produto = signal('');
  protected readonly loja = signal('');
  protected readonly valor = signal<number | null>(null);
  protected readonly fotoBase64 = signal<string | null>(null);
  protected readonly fotoMime = signal<string | null>(null);
  protected readonly previaDaFoto = signal('');

  protected readonly totais = computed(() => totaisDoGirlsMath(this.itens(), mesKey(new Date())));

  constructor() {
    this.lifestyle.listarGirlsMath().subscribe({
      next: (itens) => {
        this.itens.set(itens);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  protected urlDaFoto(item: ItemDoGirlsMath): string {
    return item.fotoAssetId ? this.media.urlDaImagem(item.fotoAssetId) : '';
  }

  protected async escolherFoto(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const arquivo = campo.files?.[0];
    if (!arquivo) {
      return;
    }
    if (!imagemCabeNoLimite(arquivo)) {
      this.erro.set($localize`A foto é grande demais. O limite é de 5 MB.`);
      return;
    }
    try {
      const { base64, mimeType, dataUrl } = await lerImagemComoBase64(arquivo);
      this.fotoBase64.set(base64);
      this.fotoMime.set(mimeType);
      this.previaDaFoto.set(dataUrl);
      this.erro.set('');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Não consegui ler essa imagem.`));
    } finally {
      campo.value = '';
    }
  }

  protected tirarFoto(): void {
    this.fotoBase64.set(null);
    this.fotoMime.set(null);
    this.previaDaFoto.set('');
  }

  protected guardar(): void {
    const produto = this.produto().trim();
    const valor = Number(this.valor());
    if (!produto) {
      this.erro.set($localize`Digite o nome do produto.`);
      return;
    }
    if (!(valor > 0)) {
      this.erro.set($localize`Digite quanto custava.`);
      return;
    }
    this.erro.set('');
    this.salvando.set(true);
    this.lifestyle
      .criarNoGirlsMath({
        produto,
        loja: this.loja().trim() || null,
        valor,
        data: hojeIso(),
        imageBase64: this.fotoBase64(),
        mimeType: this.fotoMime(),
      })
      .subscribe({
        next: (item) => {
          this.itens.update((lista) => [item, ...lista]);
          this.produto.set('');
          this.loja.set('');
          this.valor.set(null);
          this.tirarFoto();
          this.salvando.set(false);
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.salvando.set(false);
        },
      });
  }

  protected excluir(item: ItemDoGirlsMath): void {
    if (!confirm($localize`Apagar "${item.produto}"?`)) {
      return;
    }
    this.lifestyle.excluirDoGirlsMath(item.id).subscribe({
      next: () => this.itens.update((lista) => lista.filter((i) => i.id !== item.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }
}
