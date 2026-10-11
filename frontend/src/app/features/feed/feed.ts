import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { MediaService } from '../../core/api/media.service';
import { FotoDoFeed } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { mesKey, mesPorExtenso, somarMeses } from '../../core/ui/datas';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';
import { Icone } from '../../core/ui/icone';

/**
 * Feed de fotos do mes.
 *
 * <p>As imagens sao carregadas por URL propria, com cache do navegador, em vez de
 * virem em base64 dentro do JSON como no app antigo. Cada legenda e salva sozinha,
 * sem um botao geral de salvar tudo.</p>
 */
@Component({
  selector: 'app-feed',
  imports: [Icone, FormsModule, RouterLink],
  templateUrl: './feed.html',
  styleUrl: './feed.scss',
})
export class Feed {
  protected readonly auth = inject(AuthService);
  private readonly media = inject(MediaService);

  protected readonly carregando = signal(true);
  protected readonly enviando = signal(false);
  protected readonly erro = signal('');
  protected readonly fotos = signal<FotoDoFeed[]>([]);
  protected readonly mesAtual = signal(mesKey(new Date()));

  protected readonly rotuloDoMes = computed(() => mesPorExtenso(this.mesAtual()));

  constructor() {
    this.carregar();
  }

  protected trocarMes(passo: number): void {
    const [ano, mes] = this.mesAtual().split('-').map(Number);
    this.mesAtual.set(mesKey(somarMeses(new Date(ano, mes - 1, 1), passo)));
    this.carregar();
  }

  protected urlDaFoto(foto: FotoDoFeed): string {
    return this.media.urlDaImagem(foto.assetId);
  }

  protected async enviar(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const arquivo = entrada.files?.[0];
    if (!arquivo) {
      return;
    }
    if (!imagemCabeNoLimite(arquivo)) {
      this.erro.set($localize`A foto é grande demais. O limite é de 5 MB.`);
      return;
    }

    this.erro.set('');
    this.enviando.set(true);
    try {
      const { base64, mimeType } = await lerImagemComoBase64(arquivo);
      this.media.publicarNoFeed(this.mesAtual(), base64, mimeType).subscribe({
        next: (foto) => {
          this.fotos.update((lista) => [foto, ...lista]);
          this.enviando.set(false);
          entrada.value = '';
        },
        error: (falha) => {
          this.erro.set(mensagemDeErro(falha));
          this.enviando.set(false);
        },
      });
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, $localize`Não consegui ler essa imagem.`));
      this.enviando.set(false);
    }
  }

  protected legendar(foto: FotoDoFeed, legenda: string): void {
    this.media.legendar(foto.id, legenda).subscribe({
      next: (atualizada) =>
        this.fotos.update((lista) =>
          lista.map((item) => (item.id === atualizada.id ? atualizada : item)),
        ),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected excluir(foto: FotoDoFeed): void {
    if (!confirm($localize`Apagar esta foto?`)) {
      return;
    }
    this.media.excluirDoFeed(foto.id).subscribe({
      next: () => this.fotos.update((lista) => lista.filter((item) => item.id !== foto.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    this.media.listarFeed(this.mesAtual()).subscribe({
      next: (fotos) => {
        this.fotos.set(fotos);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }
}
