import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { MediaService } from '../../core/api/media.service';
import { Lugar, NovoLugar } from '../../core/api/models';
import { MoedaPipe, MoedaService } from '../../core/ui/moeda';
import { DataBrPipe } from '../../core/ui/data.pipe';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';
import { hojeIso } from '../../core/ui/datas';
import { imagemCabeNoLimite, lerImagemComoBase64 } from '../../core/ui/arquivo';

/**
 * Lugares visitados e avaliados.
 *
 * <p>A foto nao volta mais embutida na resposta: o backend devolve um identificador
 * e a imagem e buscada por URL propria, que o navegador guarda em cache por trinta
 * dias. O app antigo recebia cada foto em base64 dentro do JSON.</p>
 */
@Component({
  selector: 'app-places',
  imports: [FormsModule, MoedaPipe, DataBrPipe],
  templateUrl: './places.html',
  styleUrl: './places.scss',
})
export class Places {
  protected readonly moeda = inject(MoedaService);
  private readonly lifestyle = inject(LifestyleService);
  private readonly media = inject(MediaService);

  protected readonly notasPossiveis = [1, 2, 3, 4, 5];

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly salvando = signal(false);
  protected readonly lugares = signal<Lugar[]>([]);
  protected readonly marcadores = signal<string[]>([]);

  protected readonly formularioAberto = signal(false);
  protected readonly editandoId = signal<string | null>(null);

  protected readonly nome = signal('');
  protected readonly categoria = signal('');
  protected readonly localizacao = signal('');
  protected readonly nota = signal(5);
  protected readonly comentario = signal('');
  protected readonly data = signal(hojeIso());
  protected readonly valor = signal<number | null>(null);
  protected readonly marcacoes = signal<string[]>([]);
  protected readonly fotoBase64 = signal<string | null>(null);
  protected readonly fotoMime = signal<string | null>(null);
  protected readonly previaDaFoto = signal('');

  protected readonly novoMarcador = signal('');

  constructor() {
    forkJoin({
      lugares: this.lifestyle.listarLugares(),
      marcadores: this.lifestyle.listarMarcadores(),
    }).subscribe({
      next: ({ lugares, marcadores }) => {
        this.lugares.set(lugares);
        this.marcadores.set(marcadores);
        this.carregando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.carregando.set(false);
      },
    });
  }

  protected urlDaFoto(lugar: Lugar): string {
    return lugar.fotoAssetId ? this.media.urlDaImagem(lugar.fotoAssetId) : '';
  }

  protected abrirNovo(): void {
    this.limparFormulario();
    this.formularioAberto.set(true);
  }

  protected editar(lugar: Lugar): void {
    this.editandoId.set(lugar.id);
    this.nome.set(lugar.nome);
    this.categoria.set(lugar.categoria);
    this.localizacao.set(lugar.localizacao);
    this.nota.set(lugar.nota);
    this.comentario.set(lugar.comentario);
    this.data.set(lugar.data);
    this.valor.set(lugar.valor);
    this.marcacoes.set([...lugar.marcacoes]);
    this.fotoBase64.set(null);
    this.previaDaFoto.set(this.urlDaFoto(lugar));
    this.formularioAberto.set(true);
  }

  protected fechar(): void {
    this.formularioAberto.set(false);
    this.limparFormulario();
  }

  protected alternarMarcador(marcador: string): void {
    this.marcacoes.update((atuais) =>
      atuais.includes(marcador)
        ? atuais.filter((item) => item !== marcador)
        : [...atuais, marcador],
    );
  }

  protected async escolherFoto(evento: Event): Promise<void> {
    const arquivo = (evento.target as HTMLInputElement).files?.[0];
    if (!arquivo) {
      return;
    }
    if (!imagemCabeNoLimite(arquivo)) {
      this.erro.set('A foto é grande demais. O limite é de 5 MB.');
      return;
    }

    try {
      const { base64, mimeType, dataUrl } = await lerImagemComoBase64(arquivo);
      this.fotoBase64.set(base64);
      this.fotoMime.set(mimeType);
      this.previaDaFoto.set(dataUrl);
      this.erro.set('');
    } catch (falha) {
      this.erro.set(mensagemDeErro(falha, 'Não consegui ler essa imagem.'));
    }
  }

  protected salvar(): void {
    if (!this.nome().trim()) {
      this.erro.set('Digite o nome do lugar.');
      return;
    }
    if (!this.data()) {
      this.erro.set('Escolha a data da visita.');
      return;
    }

    const pedido: NovoLugar = {
      nome: this.nome().trim(),
      categoria: this.categoria().trim() || null,
      localizacao: this.localizacao().trim() || null,
      nota: this.nota(),
      comentario: this.comentario().trim() || null,
      data: this.data(),
      marcacoes: this.marcacoes(),
      valor: this.valor() ?? 0,
      imageBase64: this.fotoBase64(),
      mimeType: this.fotoMime(),
    };

    this.erro.set('');
    this.salvando.set(true);

    const id = this.editandoId();
    const chamada = id
      ? this.lifestyle.atualizarLugar(id, pedido)
      : this.lifestyle.criarLugar(pedido);

    chamada.subscribe({
      next: (lugar) => {
        this.lugares.update((lista) =>
          id ? lista.map((item) => (item.id === id ? lugar : item)) : [lugar, ...lista],
        );
        this.salvando.set(false);
        this.fechar();
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.salvando.set(false);
      },
    });
  }

  protected excluir(lugar: Lugar): void {
    if (!confirm(`Apagar "${lugar.nome}"?`)) {
      return;
    }
    this.lifestyle.excluirLugar(lugar.id).subscribe({
      next: () => this.lugares.update((lista) => lista.filter((item) => item.id !== lugar.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  protected criarMarcador(): void {
    const nome = this.novoMarcador().trim();
    if (nome.length < 2) {
      this.erro.set('Digite um nome válido.');
      return;
    }
    this.lifestyle.criarMarcador(nome).subscribe({
      next: (lista) => {
        this.marcadores.set(lista);
        this.novoMarcador.set('');
      },
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private limparFormulario(): void {
    this.editandoId.set(null);
    this.nome.set('');
    this.categoria.set('');
    this.localizacao.set('');
    this.nota.set(5);
    this.comentario.set('');
    this.data.set(hojeIso());
    this.valor.set(null);
    this.marcacoes.set([]);
    this.fotoBase64.set(null);
    this.fotoMime.set(null);
    this.previaDaFoto.set('');
  }
}
