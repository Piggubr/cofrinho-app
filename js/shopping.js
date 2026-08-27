let listaMercado = [];

function renderListaMercado() {
  const el = document.getElementById('listaMercado');
  if (!el) return;

  if (!listaMercado.length) {
    el.innerHTML = '<p class="empty">A listinha está vazia.</p>';
    return;
  }

  el.innerHTML = listaMercado.map(item => {
    const nome = item.item || item.nome || '';
    const feito = item.comprado === true || item.feito === true;
    return `
      <div class="shopping-item${feito ? ' done' : ''}">
        <input type="checkbox" class="shopping-check" ${feito ? 'checked' : ''} data-id="${esc(item.id)}">
        <div class="shopping-copy">
          <p class="shopping-name">${esc(nome)}</p>
          ${item.marca ? `<p class="shopping-meta">${esc(item.marca)}</p>` : ''}
        </div>
        ${item.foto ? `<img class="shopping-thumb" src="${esc(item.foto)}" alt="">` : ''}
      </div>
    `;
  }).join('');

  el.querySelectorAll('.shopping-check').forEach(chk => {
    chk.addEventListener('change', () => {
      const id = chk.dataset.id;
      const item = listaMercado.find(i => String(i.id) === id);
      if (item) {
        item.feito = chk.checked;
        item.comprado = chk.checked;
        salvarEstado();
      }
    });
  });
}

function renderCompras() {
  renderListaMercado();
  const pendentes = listaMercado.filter(item => !(item.comprado || item.feito));
  const contador = document.getElementById('listaMercadoCount');
  if (contador) contador.textContent = `${pendentes.length} ${pendentes.length === 1 ? 'item' : 'itens'}`;
  renderListaMercadoDashboard();
}

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

function renderListaMercadoDashboard() {
  const alvo = document.getElementById('listaMercadoSetores');
  const contagem = document.getElementById('listaMercadoContagem');
  if (!alvo || !contagem) return;

  const pendentes = listaMercado.filter(item => !(item.comprado || item.feito));
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
    chk.addEventListener('change', () => {
      const id = chk.dataset.id;
      const item = listaMercado.find(i => String(i.id) === id);
      if (item) {
        item.feito = chk.checked;
        item.comprado = chk.checked;
        salvarEstado();
        renderCompras();
      }
    });
  });
}

document.getElementById('btnAbrirComprasDashboard')?.addEventListener('click', () => {
  abrirAba('compras');
});

function renderProdutos() {
  const sugestoes = document.getElementById('marketSuggestions');
  if (!sugestoes) return;
  sugestoes.innerHTML = produtos.slice(0, 6).map(produto =>
    `<button type="button" class="market-suggestion">${esc(produto.nome)}</button>`
  ).join('');
}
