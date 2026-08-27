let listaMercado = [];

const SETORES_MERCADO = [
  ['Mercearia', ['pao', 'leite', 'ovos', 'arroz', 'massa', 'macarrao', 'feijao', 'farinha', 'acucar', 'sal', 'azeite']],
  ['Frescos', ['banana', 'tomate', 'alface', 'batata', 'frango', 'carne', 'peixe', 'legume', 'fruta']],
  ['Pequeno-almoço', ['cafe', 'cereais', 'iogurte', 'manteiga', 'queijo']],
  ['Bebidas', ['agua', 'sumo', 'suco', 'refrigerante', 'cha', 'cerveja', 'vinho']],
  ['Higiene pessoal', ['papel higienico', 'champ', 'sabonete', 'pasta de dentes', 'desodorizante', 'escova de dentes', 'absorvente']],
  ['Limpeza da casa', ['detergente', 'esponja', 'papel de cozinha', 'sacos do lixo', 'lava-roupas', 'amaciante', 'lixivia', 'limpeza']]
];

const ORDEM_SETORES = ['Mercearia', 'Frescos', 'Pequeno-almoço', 'Bebidas', 'Higiene pessoal', 'Limpeza da casa', 'Outros'];

const CORES_ITENS_MERCADO = ['#fde6ee', '#fff1cf', '#e8f2df', '#e7effa', '#eee6f7', '#f8e4d8', '#e5f3f0'];

function identificarSetorMercado(item) {
  const nome = String(item.item || item.nome || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
  const encontrado = SETORES_MERCADO.find(setor => setor[1].some(palavra => nome.includes(palavra)));
  return encontrado ? encontrado[0] : 'Outros';
}

function renderListaMercado() {
  const montar = (lista, vazio) => {
    const itens = listaMercado
      .filter(item => (item.lista || 'Compras') === lista)
      .sort((a, b) => Number(a.comprado) - Number(b.comprado));

    if (!itens.length) return `<p class="empty">${vazio}</p>`;

    return itens.map(item => `
      <div class="shopping-item${item.comprado ? ' done' : ''}">
        <input class="shopping-check" type="checkbox" data-id="${esc(item.id)}" ${item.comprado ? 'checked' : ''} aria-label="Marcar como comprado">
        ${item.imagem ? `<img class="shopping-thumb" src="${esc(item.imagem)}" alt="" loading="lazy" decoding="async">` : ''}
        <div class="shopping-copy">
          <p class="shopping-name">${esc(item.item)}</p>
          <p class="shopping-meta">${[item.marca, item.quantidade].filter(Boolean).map(esc).join(' · ')}</p>
        </div>
        <button class="icon-btn" data-del-compra="${esc(item.id)}" aria-label="Excluir">✕</button>
      </div>
    `).join('');
  };

  const alvoCompras = document.getElementById('listaCompras');
  const alvoDesejos = document.getElementById('listaDesejos');
  if (alvoCompras) alvoCompras.innerHTML = montar('Compras', 'Nada faltando por enquanto.');
  if (alvoDesejos) alvoDesejos.innerHTML = montar('Desejos', 'Nenhum desejo adicionado ainda.');

  document.querySelectorAll('.shopping-check').forEach(chk => {
    chk.addEventListener('change', async () => {
      const item = listaMercado.find(i => String(i.id) === chk.dataset.id);
      if (!item) return;
      const anterior = item.comprado;
      item.comprado = chk.checked;
      renderCompras();
      try {
        await chamarAppsScript({ action: 'toggleShoppingItem', id: item.id, comprado: item.comprado });
      } catch (erro) {
        item.comprado = anterior;
        renderCompras();
        alert(erro.message);
      }
    });
  });

  document.querySelectorAll('[data-del-compra]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir este item?')) return;
      try {
        await chamarAppsScript({ action: 'deleteShoppingItem', id: botao.dataset.delCompra });
        listaMercado = listaMercado.filter(i => String(i.id) !== botao.dataset.delCompra);
        renderCompras();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });
}

function renderListaMercadoDashboard() {
  const alvo = document.getElementById('listaMercadoSetores');
  const contagem = document.getElementById('listaMercadoContagem');
  if (!alvo || !contagem) return;

  const pendentes = listaMercado.filter(item => (item.lista || 'Compras') === 'Compras' && !item.comprado);
  contagem.textContent = `${pendentes.length} ${pendentes.length === 1 ? 'item' : 'itens'}`;

  if (!pendentes.length) {
    alvo.innerHTML = '<p class="empty">A listinha está vazia.</p>';
    return;
  }

  const grupos = {};
  pendentes.forEach(item => {
    const setor = identificarSetorMercado(item);
    (grupos[setor] ||= []).push(item);
  });

  let indiceCor = 0;
  alvo.innerHTML = ORDEM_SETORES.filter(setor => grupos[setor]).map(setor => {
    const itensHtml = grupos[setor].map(item => {
      const cor = CORES_ITENS_MERCADO[indiceCor++ % CORES_ITENS_MERCADO.length];
      const nome = item.item || item.nome || '';
      return `
        <label class="market-sector-item" style="background:${cor}">
          <input class="shopping-check" type="checkbox" data-id="${esc(item.id)}" aria-label="Marcar como comprado">
          <span>${esc(nome)}${item.quantidade ? ` · ${esc(item.quantidade)}` : ''}</span>
        </label>
      `;
    }).join('');
    return `<section class="market-sector"><p class="market-sector-title">${esc(setor)} · ${grupos[setor].length}</p>${itensHtml}</section>`;
  }).join('');

  alvo.querySelectorAll('.shopping-check').forEach(chk => {
    chk.addEventListener('change', async () => {
      const item = listaMercado.find(i => String(i.id) === chk.dataset.id);
      if (!item) return;
      const anterior = item.comprado;
      item.comprado = chk.checked;
      renderCompras();
      try {
        await chamarAppsScript({ action: 'toggleShoppingItem', id: item.id, comprado: item.comprado });
      } catch (erro) {
        item.comprado = anterior;
        renderCompras();
        alert(erro.message);
      }
    });
  });
}

function renderCompras() {
  renderListaMercado();
  renderListaMercadoDashboard();
  renderProdutos();
}

document.getElementById('btnAbrirComprasDashboard')?.addEventListener('click', () => {
  abrirAba('compras');
});

document.getElementById('btnAdicionarCompra')?.addEventListener('click', async () => {
  const item = document.getElementById('compraItem')?.value.trim();
  const quantidade = document.getElementById('compraQuantidade')?.value.trim() || '';
  const lista = document.getElementById('compraLista')?.value || 'Compras';
  if (!item) return;

  const botao = document.getElementById('btnAdicionarCompra');
  botao.disabled = true;
  try {
    const resposta = await chamarAppsScript({ action: 'saveShoppingItem', item, quantidade, lista });
    listaMercado.push(resposta.item);
    document.getElementById('compraItem').value = '';
    document.getElementById('compraQuantidade').value = '';
    renderCompras();
  } catch (erro) {
    alert(erro.message);
  } finally {
    botao.disabled = false;
  }
});

async function adicionarItemRapido(nome, botao) {
  botao.disabled = true;
  try {
    const resposta = await chamarAppsScript({ action: 'saveShoppingItem', item: nome, quantidade: '', lista: 'Compras' });
    listaMercado.push(resposta.item);
    renderCompras();
    botao.textContent = 'Adicionado ✓';
  } catch (erro) {
    alert(erro.message);
    botao.disabled = false;
  }
}

const CATEGORIAS_MERCADO = {
  'Essenciais': ['Pão', 'Leite', 'Ovos', 'Arroz', 'Massa'],
  'Pequeno-almoço': ['Café', 'Cereais', 'Iogurte', 'Manteiga', 'Queijo'],
  'Frescos': ['Banana', 'Tomate', 'Alface', 'Batata', 'Frango'],
  'Bebidas': ['Água', 'Sumo', 'Leite', 'Chá', 'Água com gás'],
  'Limpeza': ['Detergente', 'Esponja', 'Papel de cozinha', 'Sacos do lixo', 'Lava-roupas'],
  'Higiene': ['Papel higiénico', 'Champô', 'Sabonete', 'Pasta de dentes', 'Desodorizante']
};

let categoriaMercadoAtiva = 'Essenciais';

function renderSugestoesMercado() {
  const categorias = document.getElementById('categoriasMercado');
  const sugestoes = document.getElementById('sugestoesMercado');
  if (!categorias || !sugestoes) return;

  categorias.innerHTML = Object.keys(CATEGORIAS_MERCADO).map(nome =>
    `<button class="market-category ${nome === categoriaMercadoAtiva ? 'active' : ''}" type="button" data-categoria-mercado="${esc(nome)}">${esc(nome)}</button>`
  ).join('');

  sugestoes.innerHTML = CATEGORIAS_MERCADO[categoriaMercadoAtiva].map(nome =>
    `<button class="quick-product" type="button" data-sugestao-mercado="${esc(nome)}">+ ${esc(nome)}</button>`
  ).join('');

  categorias.querySelectorAll('[data-categoria-mercado]').forEach(botao => {
    botao.addEventListener('click', () => {
      categoriaMercadoAtiva = botao.dataset.categoriaMercado;
      renderSugestoesMercado();
    });
  });

  sugestoes.querySelectorAll('[data-sugestao-mercado]').forEach(botao => {
    botao.addEventListener('click', () => adicionarItemRapido(botao.dataset.sugestaoMercado, botao));
  });
}

let resultadosMercado = [];

function renderResultadosMercado() {
  const alvo = document.getElementById('resultadosProdutos');
  if (!alvo) return;

  if (!resultadosMercado.length) {
    alvo.innerHTML = '<p class="empty">Nenhum produto encontrado.</p>';
    return;
  }

  alvo.innerHTML = resultadosMercado.map((produto, indice) => `
    <button class="market-product" type="button" data-produto-mercado="${indice}">
      ${produto.imagem
        ? `<img src="${esc(produto.imagem)}" alt="" loading="lazy" decoding="async">`
        : '<span style="height:82px;display:flex;align-items:center;justify-content:center;">Sem imagem</span>'}
      <strong>${esc(produto.nome)}</strong>
      <span>${esc([produto.marca, produto.quantidade].filter(Boolean).join(' · ') || 'Toque para adicionar')}</span>
    </button>
  `).join('');

  alvo.querySelectorAll('[data-produto-mercado]').forEach(botao => {
    botao.addEventListener('click', async () => {
      const produto = resultadosMercado[Number(botao.dataset.produtoMercado)];
      if (!produto) return;
      botao.disabled = true;
      try {
        const resposta = await chamarAppsScript({
          action: 'saveShoppingItem', item: produto.nome, quantidade: produto.quantidade,
          lista: 'Compras', marca: produto.marca, imagem: produto.imagem, codigo: produto.codigo
        });
        listaMercado.push(resposta.item);
        renderCompras();
        botao.textContent = 'Adicionado ✓';
      } catch (erro) {
        alert(erro.message);
        botao.disabled = false;
      }
    });
  });
}

async function buscarProdutosMercado() {
  const campo = document.getElementById('buscaProduto');
  const busca = campo?.value.trim();
  if (!busca || busca.length < 2) return;

  const botao = document.getElementById('btnBuscarProduto');
  botao.disabled = true;
  botao.textContent = 'Buscando...';
  document.getElementById('resultadosProdutos').innerHTML = '<p class="empty">Procurando produtos...</p>';

  try {
    const resposta = await chamarAppsScript({ action: 'searchProducts', busca });
    resultadosMercado = resposta.produtos || [];
    renderResultadosMercado();
  } catch (erro) {
    document.getElementById('resultadosProdutos').innerHTML = `<p class="empty">${esc(erro.message)}</p>`;
  } finally {
    botao.disabled = false;
    botao.textContent = 'Buscar';
  }
}

document.getElementById('btnBuscarProduto')?.addEventListener('click', buscarProdutosMercado);
document.getElementById('buscaProduto')?.addEventListener('keydown', evento => {
  if (evento.key === 'Enter') buscarProdutosMercado();
});

function renderProdutos() {
  renderSugestoesMercado();

  const alvo = document.getElementById('produtosMemoria');
  const frequentes = document.getElementById('produtosFrequentes');

  if (frequentes) {
    if (!produtos.length) {
      frequentes.innerHTML = '';
    } else {
      frequentes.innerHTML = produtos.slice(0, 8).map((produto, indice) =>
        `<button class="quick-product" type="button" data-produto-frequente="${indice}">+ ${esc(produto.nome)}</button>`
      ).join('');
      frequentes.querySelectorAll('[data-produto-frequente]').forEach(botao => {
        const produto = produtos[Number(botao.dataset.produtoFrequente)];
        botao.addEventListener('click', () => adicionarItemRapido(produto.nome, botao));
      });
    }
  }

  if (!alvo) return;

  if (!produtos.length) {
    alvo.innerHTML = '<p class="empty">Ela começa a aprender quando você confirmar o próximo recibo.</p>';
    return;
  }

  alvo.innerHTML = produtos.slice(0, 10).map(produto => {
    const variacao = Number(produto.variacao) || 0;
    let mudanca = '';
    if (Math.abs(variacao) >= 0.01) {
      mudanca = `<span class="product-memory-change ${variacao > 0 ? 'up' : 'down'}">${variacao > 0 ? 'Subiu ' : 'Baixou '}${fmt(Math.abs(variacao))}</span>`;
    }
    return `
      <div class="product-memory-row">
        <div>
          <p class="product-memory-name">${esc(produto.nome)}</p>
          <p class="product-memory-meta">${esc(produto.categoria)} · ${Number(produto.compras || 0)} compra${Number(produto.compras) === 1 ? '' : 's'}</p>
        </div>
        <div class="product-memory-price">
          <span>${fmt(Number(produto.ultimo) || 0)}</span>
          ${mudanca}
          <span class="product-memory-meta">média ${fmt(Number(produto.media) || 0)}</span>
        </div>
      </div>
    `;
  }).join('');
}
