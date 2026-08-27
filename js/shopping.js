let listaMercado = [];

function renderListaMercado() {
  const el = document.getElementById('listaMercado');
  if (!el) return;

  if (!listaMercado.length) {
    el.innerHTML = '<p class="empty">A listinha está vazia.</p>';
    return;
  }

  el.innerHTML = listaMercado.map(item => {
    return `
      <div class="shopping-item${item.feito ? ' done' : ''}">
        <input type="checkbox" class="shopping-check" ${item.feito ? 'checked' : ''} data-id="${item.id}">
        <div class="shopping-copy">
          <p class="shopping-name">${esc(item.nome)}</p>
          ${item.marca ? `<p class="shopping-meta">${esc(item.marca)}</p>` : ''}
        </div>
        ${item.foto ? `<img class="shopping-thumb" src="${esc(item.foto)}" alt="">` : ''}
      </div>
    `;
  }).join('');

  el.querySelectorAll('.shopping-check').forEach(chk => {
    chk.addEventListener('change', () => {
      const id = Number(chk.dataset.id);
      const item = listaMercado.find(i => i.id === id);
      if (item) {
        item.feito = chk.checked;
        salvarEstado();
      }
    });
  });
}