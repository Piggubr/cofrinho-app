import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LifestyleService } from '../../core/api/lifestyle.service';
import { ItemDeCompra, ProdutoDoCatalogo } from '../../core/api/models';
import { mensagemDeErro } from '../../core/ui/mensagem-de-erro';

type NomeDeLista = 'Compras' | 'Desejos';

/**
 * Listas de compras e de desejos.
 *
 * <p>A busca no catalogo aberto preenche marca, imagem e codigo de barras sem
 * digitacao. O backend so aceita imagem servida por HTTPS, entao o que vier de
 * outro esquema chega vazio e a tela simplesmente nao mostra foto.</p>
 */
@Component({
  selector: 'app-shopping',
  imports: [FormsModule],
  templateUrl: './shopping.html',
  styleUrl: './shopping.scss',
})
export class Shopping {
  private readonly lifestyle = inject(LifestyleService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly itens = signal<ItemDeCompra[]>([]);
  protected readonly listaAtiva = signal<NomeDeLista>('Compras');

  protected readonly novoItem = signal('');
  protected readonly novaQuantidade = signal('');

  protected readonly busca = signal('');
  protected readonly buscando = signal(false);
  protected readonly resultados = signal<ProdutoDoCatalogo[]>([]);

  protected readonly daLista = computed(() =>
    this.itens().filter((item) => item.lista === this.listaAtiva()),
  );
  protected readonly pendentes = computed(() => this.daLista().filter((item) => !item.comprado));
  protected readonly comprados = computed(() => this.daLista().filter((item) => item.comprado));

  constructor() {
    this.carregar();
  }

  protected trocarLista(lista: NomeDeLista): void {
    this.listaAtiva.set(lista);
  }

  protected adicionar(): void {
    const item = this.novoItem().trim();
    if (!item) {
      this.erro.set($localize`Digite o que deseja adicionar.`);
      return;
    }

    this.erro.set('');
    this.lifestyle
      .criarItemDeCompra({
        item,
        quantidade: this.novaQuantidade().trim(),
        lista: this.listaAtiva(),
      })
      .subscribe({
        next: (criado) => {
          this.itens.update((lista) => [criado, ...lista]);
          this.novoItem.set('');
          this.novaQuantidade.set('');
        },
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
  }

  protected adicionarDoCatalogo(produto: ProdutoDoCatalogo): void {
    this.lifestyle
      .criarItemDeCompra({
        item: produto.nome,
        quantidade: produto.quantidade,
        lista: this.listaAtiva(),
        marca: produto.marca,
        imagem: produto.imagem,
        codigo: produto.codigo,
      })
      .subscribe({
        next: (criado) => {
          this.itens.update((lista) => [criado, ...lista]);
          this.resultados.set([]);
          this.busca.set('');
        },
        error: (falha) => this.erro.set(mensagemDeErro(falha)),
      });
  }

  protected buscarNoCatalogo(): void {
    const termo = this.busca().trim();
    if (termo.length < 2) {
      this.erro.set($localize`Digite pelo menos duas letras.`);
      return;
    }

    this.erro.set('');
    this.buscando.set(true);
    this.lifestyle.buscarNoCatalogo(termo).subscribe({
      next: (produtos) => {
        this.resultados.set(produtos);
        this.buscando.set(false);
      },
      error: (falha) => {
        this.erro.set(mensagemDeErro(falha));
        this.buscando.set(false);
      },
    });
  }

  protected alternarComprado(item: ItemDeCompra): void {
    const desejado = !item.comprado;
    // Marca na hora e desfaz se o servidor recusar: esperar a resposta para
    // riscar um item da lista deixaria a tela lenta no supermercado.
    this.aplicarComprado(item.id, desejado);

    this.lifestyle.marcarComprado(item.id, desejado).subscribe({
      error: (falha) => {
        this.aplicarComprado(item.id, !desejado);
        this.erro.set(mensagemDeErro(falha));
      },
    });
  }

  protected excluir(item: ItemDeCompra): void {
    this.lifestyle.excluirItemDeCompra(item.id).subscribe({
      next: () => this.itens.update((lista) => lista.filter((atual) => atual.id !== item.id)),
      error: (falha) => this.erro.set(mensagemDeErro(falha)),
    });
  }

  private aplicarComprado(id: string, comprado: boolean): void {
    this.itens.update((lista) =>
      lista.map((item) => (item.id === id ? { ...item, comprado } : item)),
    );
  }

  private carregar(): void {
    this.lifestyle.listarCompras().subscribe({
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
}
