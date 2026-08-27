let gastos = [];

function renderLista() {
  const el = document.getElementById('lista');
  if (!el) return;

  if (!gastos.length) {
    el.innerHTML = '<p class="empty">Nenhum gasto ainda.</p>';
    return;
  }

  const ordenado = [...gastos].sort((a, b) => {
    if (b.data === a.data) return b.id - a.id;
    return b.data.localeCompare(a.data);
  });

  el.innerHTML = ordenado.map(g => {
    return `
      <div class="row">
        <div class="row-left">
          <p class="row-item">${esc(g.item)}</p>
          <p class="row-date">${fmtData(g.data)} • ${esc(g.categoria)}</p>
        </div>
        <div class="row-right">
          <span class="row-value">${fmt(g.valor)}</span>
        </div>
      </div>
    `;
  }).join('');
}

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