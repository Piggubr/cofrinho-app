let gastos = [];
let listaLancamentosExpandida = false;

function renderLista() {
  const el = document.getElementById('lista');
  if (!el) return;

  if (!gastos.length) {
    el.innerHTML = '<p class="empty">Nenhum gasto ainda.</p>';
    atualizarBotaoTodosLancamentos(0, 0);
    return;
  }

  const ordenado = [...gastos].sort((a, b) => {
    if (b.data === a.data) return b.id - a.id;
    return b.data.localeCompare(a.data);
  });

  const LIMITE_COMPACTO = 3;
  const visiveis = listaLancamentosExpandida ? ordenado : ordenado.slice(0, LIMITE_COMPACTO);

  el.innerHTML = visiveis.map(g => {
    const cor = CATEGORIAS[g.categoria] || CATEGORIAS.Outros;

    return `
      <div class="row">
        <div class="row-left">
          <p class="row-item">${esc(g.item)}</p>
          <p class="row-date">${fmtData(g.data)}</p>
        </div>
        <div class="row-right">
          <span class="pill" style="background:${cor}26;color:${cor};">${esc(g.categoria)}</span>
          <span class="row-value">${fmt(g.valor)}</span>
        </div>
      </div>
    `;
  }).join('');

  atualizarBotaoTodosLancamentos(ordenado.length, LIMITE_COMPACTO);
}

function atualizarBotaoTodosLancamentos(total, limite) {
  const botao = document.getElementById('btnTodosLancamentos');
  if (!botao) return;

  if (total <= limite) {
    botao.style.display = 'none';
    return;
  }

  botao.style.display = '';
  botao.textContent = listaLancamentosExpandida ? 'Ver menos' : 'Ver todos';
}

document.getElementById('btnTodosLancamentos')?.addEventListener('click', () => {
  listaLancamentosExpandida = !listaLancamentosExpandida;
  renderLista();
});

document.getElementById('btnAdicionar')?.addEventListener('click', () => {
  const item = document.getElementById('novoItem')?.value.trim();
  const valor = Number(document.getElementById('novoValor')?.value || 0);
  const categoria = document.getElementById('novaCategoria')?.value || 'Outros';
  const tipo = document.getElementById('novoTipo')?.value || 'Variável';

  if (!item) {
    alert('Digite o que foi gasto.');
    return;
  }

  gastos.push({
    id: Date.now(),
    data: mesKeyDe(new Date()) + '-' + String(new Date().getDate()).padStart(2, '0'),
    item,
    valor,
    categoria,
    tipo
  });

  document.getElementById('novoItem').value = '';
  document.getElementById('novoValor').value = '';
  renderLista();
  render();
  salvarEstado();
});