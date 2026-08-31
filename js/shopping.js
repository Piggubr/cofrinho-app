let listaMercado = [];
let resultadosMercado = [];
let categoriaMercadoAtiva = 'Essenciais';
let shoppingLocal = estadoComprasVazio();
let shoppingLocalKey = '';

const CATEGORIAS_MERCADO = {
  'Essenciais': ['Pão', 'Leite', 'Ovos', 'Arroz', 'Feijão', 'Massa', 'Farinha', 'Açúcar', 'Sal', 'Azeite'],
  'Pequeno-almoço': ['Café', 'Cereais', 'Aveia', 'Iogurte', 'Manteiga', 'Queijo', 'Geleia', 'Biscoitos'],
  'Frutas e verduras': ['Banana', 'Maçã', 'Laranja', 'Uva', 'Tomate', 'Alface', 'Batata', 'Cenoura', 'Cebola', 'Alho'],
  'Carnes e proteínas': ['Frango', 'Carne bovina', 'Peixe', 'Atum', 'Presunto', 'Tofu'],
  'Bebidas': ['Água', 'Suco', 'Leite', 'Chá', 'Água com gás', 'Refrigerante'],
  'Limpeza': ['Detergente', 'Esponja', 'Papel de cozinha', 'Sacos do lixo', 'Lava-roupas', 'Desinfetante'],
  'Higiene': ['Papel higiênico', 'Xampu', 'Condicionador', 'Sabonete', 'Pasta de dentes', 'Desodorante']
};

const EMOJIS_PRODUTOS = {
  pao:'🥖', leite:'🥛', ovos:'🥚', ovo:'🥚', arroz:'🍚', massa:'🍝', macarrao:'🍝', cafe:'☕',
  cereais:'🥣', iogurte:'🥣', manteiga:'🧈', queijo:'🧀', banana:'🍌', tomate:'🍅', alface:'🥬',
  batata:'🥔', frango:'🍗', carne:'🥩', peixe:'🐟', agua:'💧', sumo:'🧃', suco:'🧃', cha:'🍵',
  detergente:'🧴', esponja:'🧽', sabonete:'🧼', champo:'🧴', papel:'🧻', pasta:'🪥'
};

function estadoComprasVazio() { return { carrinho:[], ativa:null, historico:[], desejos:[] }; }
function usuarioComprasLocal() {
  return String(usuarioDetalhes?.email || usuarioAtual || (window.PIGGU_DEMO_MODE ? 'demo' : 'pending')).trim().toLowerCase().replace(/[^a-z0-9@._-]/g, '_') || 'pending';
}
function chaveComprasLocal() { return `piggu_shopping_v2_${usuarioComprasLocal()}`; }
function garantirComprasLocais() {
  const chave = chaveComprasLocal();
  if (chave === shoppingLocalKey) return;
  shoppingLocalKey = chave; shoppingLocal = estadoComprasVazio();
  if (window.PIGGU_DEMO_MODE || chave.endsWith('_pending')) return;
  try { const salvo = JSON.parse(localStorage.getItem(chave)); if (salvo && typeof salvo === 'object') shoppingLocal = { ...estadoComprasVazio(), ...salvo }; }
  catch (erro) { console.warn('Não foi possível abrir as listas locais.', erro); }
}
function salvarComprasLocais() {
  if (window.PIGGU_DEMO_MODE || shoppingLocalKey.endsWith('_pending')) return;
  try { localStorage.setItem(shoppingLocalKey, JSON.stringify(shoppingLocal)); }
  catch (_) { alert('Não foi possível salvar a lista neste dispositivo.'); }
}
function normalizarProduto(valor) { return String(valor || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, ''); }
function emojiProduto(nome) {
  const normalizado = normalizarProduto(nome);
  return Object.entries(EMOJIS_PRODUTOS).find(([termo]) => normalizado.includes(termo))?.[1] || '🛒';
}
function categoriaProduto(nome) {
  const normalizado = normalizarProduto(nome);
  return Object.entries(CATEGORIAS_MERCADO).find(([, itens]) => itens.some(item => normalizado.includes(normalizarProduto(item))))?.[0] || 'Outros';
}
function novoItemCompra(nome, quantidade, unidade, extras = {}) {
  return { id:`local-${Date.now()}-${Math.random().toString(16).slice(2)}`, item:String(nome).trim(), quantidade:String(quantidade || '1').trim(), unidade:unidade || 'un', emoji:emojiProduto(nome), categoria:categoriaProduto(nome), comprado:false, marca:extras.marca || '', imagem:extras.imagem || '' };
}
function textoQuantidade(item) { return [item.quantidade || '1', item.unidade || 'un'].join(' '); }
function linhaCompra(item, modo) {
  const checkbox = modo === 'ativa' ? `<input class="shopping-check" type="checkbox" data-check-local="${esc(item.id)}" ${item.comprado ? 'checked' : ''} aria-label="Marcar ${esc(item.item)}">` : '<span class="shopping-checkbox-placeholder" aria-hidden="true"></span>';
  return `<div class="shopping-item shopping-item-${modo}${item.comprado ? ' done' : ''}">${checkbox}<span class="shopping-quantity">${esc(textoQuantidade(item))}</span><span class="shopping-emoji" aria-hidden="true">${esc(item.emoji || emojiProduto(item.item))}</span><div class="shopping-copy"><p class="shopping-name">${esc(item.item)}</p><p class="shopping-meta">${esc(item.categoria || categoriaProduto(item.item))}${item.marca ? ` · ${esc(item.marca)}` : ''}</p></div>${modo === 'rascunho' ? `<button class="icon-btn" data-edit-local="${esc(item.id)}" aria-label="Editar ${esc(item.item)}">Editar</button><button class="icon-btn" data-remove-local="${esc(item.id)}" aria-label="Remover ${esc(item.item)}">×</button>` : ''}</div>`;
}

function renderCarrinho() {
  const alvo = document.getElementById('listaCarrinho'); const contagem = document.getElementById('carrinhoContagem'); const finalizar = document.getElementById('btnFinalizarCarrinho'); if (!alvo) return;
  const itens = shoppingLocal.carrinho || [];
  if (contagem) contagem.textContent = `${itens.length} ${itens.length === 1 ? 'item' : 'itens'}`;
  alvo.innerHTML = itens.length ? itens.map(item => linhaCompra(item, 'rascunho')).join('') : '<p class="empty">Seu carrinho ainda está vazio.</p>';
  if (finalizar) finalizar.disabled = !itens.length || Boolean(shoppingLocal.ativa);
  alvo.querySelectorAll('[data-remove-local]').forEach(botao => botao.addEventListener('click', () => { shoppingLocal.carrinho = itens.filter(item => item.id !== botao.dataset.removeLocal); salvarComprasLocais(); renderCompras(); }));
  alvo.querySelectorAll('[data-edit-local]').forEach(botao => botao.addEventListener('click', () => {
    const item = itens.find(produto => produto.id === botao.dataset.editLocal); if (!item) return;
    const nome = prompt('Nome do item', item.item); if (!nome?.trim()) return;
    const quantidade = prompt('Quantidade', item.quantidade || '1'); if (quantidade === null) return;
    item.item = nome.trim(); item.quantidade = quantidade.trim() || '1'; item.emoji = emojiProduto(item.item); item.categoria = categoriaProduto(item.item); salvarComprasLocais(); renderCompras();
  }));
}

function renderListaAtiva() {
  const alvo = document.getElementById('listaCompras'); const card = document.getElementById('listaAtivaCard'); const resumo = document.getElementById('listaAtivaResumo'); const concluir = document.getElementById('btnConcluirCompra'); if (!alvo || !card) return;
  const ativa = shoppingLocal.ativa; card.classList.toggle('has-active-list', Boolean(ativa));
  if (!ativa) { alvo.innerHTML = '<p class="empty">Finalize o carrinho para criar uma lista.</p>'; if (resumo) resumo.textContent = 'Nenhuma lista ativa.'; if (concluir) concluir.hidden = true; return; }
  const marcados = ativa.itens.filter(item => item.comprado).length;
  if (resumo) resumo.textContent = `${marcados} de ${ativa.itens.length} marcados`;
  alvo.innerHTML = ativa.itens.map(item => linhaCompra(item, 'ativa')).join(''); if (concluir) concluir.hidden = false;
  alvo.querySelectorAll('[data-check-local]').forEach(check => check.addEventListener('change', () => { const item = ativa.itens.find(produto => produto.id === check.dataset.checkLocal); if (!item) return; item.comprado = check.checked; salvarComprasLocais(); renderCompras(); }));
}

function renderDesejos() {
  const alvo = document.getElementById('listaDesejos'); if (!alvo) return; const desejos = shoppingLocal.desejos || []; const legados = listaMercado.filter(item => item.lista === 'Desejos');
  alvo.innerHTML = desejos.length || legados.length ? desejos.map(item => `<div class="shopping-item"><span class="shopping-emoji">${esc(item.emoji)}</span><div class="shopping-copy"><p class="shopping-name">${esc(item.item)}</p></div><button class="icon-btn" data-remove-wish="${esc(item.id)}">×</button></div>`).join('') + legados.map(item => `<div class="shopping-item${item.comprado ? ' done' : ''}"><input class="shopping-check" type="checkbox" data-check-legacy="${esc(item.id)}" ${item.comprado ? 'checked' : ''}><span class="shopping-emoji">${emojiProduto(item.item)}</span><div class="shopping-copy"><p class="shopping-name">${esc(item.item)}</p><p class="shopping-meta">${esc(item.quantidade || '')}</p></div><button class="icon-btn" data-remove-legacy="${esc(item.id)}">×</button></div>`).join('') : '<p class="empty">Nenhum desejo adicionado ainda.</p>';
  alvo.querySelectorAll('[data-remove-wish]').forEach(botao => botao.addEventListener('click', () => { shoppingLocal.desejos = desejos.filter(item => item.id !== botao.dataset.removeWish); salvarComprasLocais(); renderCompras(); }));
  ligarAcoesLegadas(alvo);
}

function renderHistoricoCompras() {
  const alvo = document.getElementById('historicoCompras'); if (!alvo) return; const historico = shoppingLocal.historico || [];
  alvo.innerHTML = historico.length ? historico.map(lista => `<article class="shopping-history-row"><div><strong>${new Date(lista.concluidaEm).toLocaleDateString('pt-BR')}</strong><span>${lista.itens.length} ${lista.itens.length === 1 ? 'item' : 'itens'}</span></div><button class="button-secondary" data-reuse-list="${esc(lista.id)}">Reutilizar lista</button></article>`).join('') : '<p class="empty">As compras concluídas aparecerão aqui.</p>';
  alvo.querySelectorAll('[data-reuse-list]').forEach(botao => botao.addEventListener('click', () => { const lista = historico.find(item => item.id === botao.dataset.reuseList); if (!lista) return; shoppingLocal.carrinho = lista.itens.map(item => ({ ...item, id:`local-${Date.now()}-${Math.random().toString(16).slice(2)}`, comprado:false })); salvarComprasLocais(); renderCompras(); }));
}

function renderLegado() {
  const alvo = document.getElementById('listaComprasLegada'); if (!alvo) return; const itens = listaMercado.filter(item => (item.lista || 'Compras') === 'Compras');
  alvo.innerHTML = itens.length ? itens.map(item => `<div class="shopping-item${item.comprado ? ' done' : ''}"><input class="shopping-check" type="checkbox" data-check-legacy="${esc(item.id)}" ${item.comprado ? 'checked' : ''}><span class="shopping-emoji">${emojiProduto(item.item)}</span><div class="shopping-copy"><p class="shopping-name">${esc(item.item)}</p><p class="shopping-meta">${esc(item.quantidade || '')}</p></div><button class="icon-btn" data-remove-legacy="${esc(item.id)}">×</button></div>`).join('') : '<p class="empty">Nenhum item anterior.</p>';
  ligarAcoesLegadas(alvo);
}
function ligarAcoesLegadas(alvo) {
  alvo.querySelectorAll('[data-check-legacy]').forEach(check => check.addEventListener('change', async () => {
    const item = listaMercado.find(produto => String(produto.id) === check.dataset.checkLegacy); if (!item) return; const anterior = item.comprado; item.comprado = check.checked; renderCompras();
    try { await chamarAppsScript({ action:'toggleShoppingItem', id:item.id, comprado:item.comprado }); } catch (erro) { item.comprado = anterior; renderCompras(); alert(erro.message); }
  }));
  alvo.querySelectorAll('[data-remove-legacy]').forEach(botao => botao.addEventListener('click', async () => {
    if (!confirm('Excluir este item?')) return;
    try { await chamarAppsScript({ action:'deleteShoppingItem', id:botao.dataset.removeLegacy }); listaMercado = listaMercado.filter(item => String(item.id) !== botao.dataset.removeLegacy); renderCompras(); } catch (erro) { alert(erro.message); }
  }));
}
function itensDashboard() { return shoppingLocal.ativa ? shoppingLocal.ativa.itens.filter(item => !item.comprado) : listaMercado.filter(item => (item.lista || 'Compras') === 'Compras' && !item.comprado); }
function renderListaMercadoDashboard() {
  const alvo = document.getElementById('listaMercadoSetores'); const contagem = document.getElementById('listaMercadoContagem'); if (!alvo || !contagem) return;
  const itens = itensDashboard(); contagem.textContent = `${itens.length} ${itens.length === 1 ? 'item' : 'itens'}`;
  if (!itens.length) { alvo.innerHTML = '<p class="empty">A listinha está vazia.</p>'; return; }
  const grupos = {}; itens.forEach(item => { const categoria = item.categoria || categoriaProduto(item.item); (grupos[categoria] ||= []).push(item); });
  alvo.innerHTML = Object.entries(grupos).map(([categoria, produtos]) => `<section class="market-sector"><p class="market-sector-title">${esc(categoria)} · ${produtos.length}</p>${produtos.map(item => `<div class="market-sector-item"><span class="shopping-emoji">${esc(item.emoji || emojiProduto(item.item))}</span><span>${esc(item.item)} · ${esc(textoQuantidade(item))}</span></div>`).join('')}</section>`).join('');
}

function adicionarAoCarrinho(nome, quantidade = '1', unidade = 'un', extras = {}) { garantirComprasLocais(); shoppingLocal.carrinho.push(novoItemCompra(nome, quantidade, unidade, extras)); salvarComprasLocais(); renderCompras(); }
function renderCompras() { garantirComprasLocais(); renderCarrinho(); renderListaAtiva(); renderDesejos(); renderHistoricoCompras(); renderLegado(); renderListaMercadoDashboard(); renderProdutos(); }

document.getElementById('btnAbrirComprasDashboard')?.addEventListener('click', () => abrirAba('compras'));
document.getElementById('btnAdicionarCompra')?.addEventListener('click', () => {
  garantirComprasLocais(); const campo = document.getElementById('compraItem'); const nome = campo?.value.trim(); if (!nome) return;
  const quantidade = document.getElementById('compraQuantidade')?.value.trim() || '1'; const unidade = document.getElementById('compraUnidade')?.value || 'un';
  if (document.getElementById('compraLista')?.value === 'Desejos') { shoppingLocal.desejos.push(novoItemCompra(nome, quantidade, unidade)); salvarComprasLocais(); renderCompras(); } else adicionarAoCarrinho(nome, quantidade, unidade);
  campo.value = ''; document.getElementById('compraQuantidade').value = '';
});
document.getElementById('btnFinalizarCarrinho')?.addEventListener('click', () => { if (!shoppingLocal.carrinho.length || shoppingLocal.ativa) return; shoppingLocal.ativa = { id:`lista-${Date.now()}`, criadaEm:new Date().toISOString(), itens:shoppingLocal.carrinho.map(item => ({ ...item, comprado:false })) }; shoppingLocal.carrinho = []; salvarComprasLocais(); renderCompras(); });
document.getElementById('btnConcluirCompra')?.addEventListener('click', () => { if (!shoppingLocal.ativa || !confirm('Concluir esta compra e guardar a lista no histórico?')) return; shoppingLocal.historico.unshift({ ...shoppingLocal.ativa, concluidaEm:new Date().toISOString() }); shoppingLocal.ativa = null; salvarComprasLocais(); renderCompras(); });

function renderSugestoesMercado() {
  const categorias = document.getElementById('categoriasMercado'); const sugestoes = document.getElementById('sugestoesMercado'); if (!categorias || !sugestoes) return;
  categorias.innerHTML = Object.keys(CATEGORIAS_MERCADO).map(nome => `<button class="market-category ${nome === categoriaMercadoAtiva ? 'active' : ''}" type="button" data-categoria-mercado="${esc(nome)}">${esc(nome)}</button>`).join('');
  sugestoes.innerHTML = CATEGORIAS_MERCADO[categoriaMercadoAtiva].map(nome => {
    const selecionado = shoppingLocal.carrinho.some(item => normalizarProduto(item.item) === normalizarProduto(nome));
    return `<label class="catalog-product-row"><input type="checkbox" data-sugestao-mercado="${esc(nome)}" ${selecionado ? 'checked' : ''}><span class="shopping-quantity">1 un</span><span class="shopping-emoji" aria-hidden="true">${emojiProduto(nome)}</span><strong>${esc(nome)}</strong></label>`;
  }).join('');
  categorias.querySelectorAll('[data-categoria-mercado]').forEach(botao => botao.addEventListener('click', () => { categoriaMercadoAtiva = botao.dataset.categoriaMercado; renderSugestoesMercado(); }));
  sugestoes.querySelectorAll('[data-sugestao-mercado]').forEach(campo => campo.addEventListener('change', () => {
    const nome = campo.dataset.sugestaoMercado;
    if (campo.checked) adicionarAoCarrinho(nome);
    else { shoppingLocal.carrinho = shoppingLocal.carrinho.filter(item => normalizarProduto(item.item) !== normalizarProduto(nome)); salvarComprasLocais(); renderCompras(); }
  }));
}
function renderResultadosMercado() {
  const alvo = document.getElementById('resultadosProdutos'); if (!alvo) return;
  if (!resultadosMercado.length) { alvo.innerHTML = '<p class="empty">Nenhum produto encontrado.</p>'; return; }
  alvo.innerHTML = resultadosMercado.map((produto, indice) => `<button class="market-product" type="button" data-produto-mercado="${indice}">${produto.imagem ? `<img src="${esc(produto.imagem)}" alt="" loading="lazy">` : `<span class="market-product-emoji">${emojiProduto(produto.nome)}</span>`}<strong>${esc(produto.nome)}</strong><span>${esc([produto.marca, produto.quantidade].filter(Boolean).join(' · ') || 'Adicionar ao carrinho')}</span></button>`).join('');
  alvo.querySelectorAll('[data-produto-mercado]').forEach(botao => botao.addEventListener('click', () => { const produto = resultadosMercado[Number(botao.dataset.produtoMercado)]; if (produto) adicionarAoCarrinho(produto.nome, produto.quantidade || '1', 'un', produto); }));
}
async function buscarProdutosMercado() {
  const campo = document.getElementById('buscaProduto'); const busca = campo?.value.trim(); if (!busca || busca.length < 2) return;
  const botao = document.getElementById('btnBuscarProduto'); botao.disabled = true; botao.textContent = 'Buscando…';
  const termo = normalizarProduto(busca);
  const locais = [...new Set(Object.values(CATEGORIAS_MERCADO).flat())]
    .filter(nome => normalizarProduto(nome).includes(termo) || termo.includes(normalizarProduto(nome)))
    .map(nome => ({ nome, quantidade:'1', categoria:categoriaProduto(nome) }));
  resultadosMercado = locais; renderResultadosMercado();
  try {
    const resposta = await chamarAppsScript({ action:'searchProducts', busca });
    const externos = resposta.produtos || [];
    resultadosMercado = [...locais, ...externos.filter(item => !locais.some(local => normalizarProduto(local.nome) === normalizarProduto(item.nome)))];
    renderResultadosMercado();
  } catch (_) {
    if (!locais.length) document.getElementById('resultadosProdutos').innerHTML = '<p class="empty">Nenhum item no catálogo local. Você ainda pode adicionar um produto personalizado no carrinho.</p>';
  }
  finally { botao.disabled = false; botao.textContent = 'Buscar'; }
}
document.getElementById('btnBuscarProduto')?.addEventListener('click', buscarProdutosMercado);
document.getElementById('buscaProduto')?.addEventListener('keydown', evento => { if (evento.key === 'Enter') buscarProdutosMercado(); });
function renderProdutos() {
  renderSugestoesMercado(); const alvo = document.getElementById('produtosMemoria'); const frequentes = document.getElementById('produtosFrequentes');
  if (frequentes) frequentes.innerHTML = produtos.slice(0,8).map((produto, indice) => `<button class="quick-product" type="button" data-produto-frequente="${indice}"><span>${emojiProduto(produto.nome)}</span>${esc(produto.nome)}</button>`).join('');
  frequentes?.querySelectorAll('[data-produto-frequente]').forEach(botao => botao.addEventListener('click', () => adicionarAoCarrinho(produtos[Number(botao.dataset.produtoFrequente)].nome)));
  if (!alvo) return;
  if (!produtos.length) { alvo.innerHTML = '<p class="empty">O histórico começa depois da confirmação de um recibo.</p>'; return; }
  alvo.innerHTML = produtos.slice(0,10).map(produto => `<div class="product-memory-row"><div><p class="product-memory-name">${esc(produto.nome)}</p><p class="product-memory-meta">${esc(produto.categoria)} · ${Number(produto.compras || 0)} compra${Number(produto.compras) === 1 ? '' : 's'}</p></div><div class="product-memory-price"><span>${fmt(Number(produto.ultimo) || 0)}</span><span class="product-memory-meta">média ${fmt(Number(produto.media) || 0)}</span></div></div>`).join('');
}
