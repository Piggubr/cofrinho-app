let premios = [];

function renderPremios() {
  const lista = document.getElementById('premiosLista');
  if (!lista) return;

  if (!premios.length) {
    lista.innerHTML = '<p class="empty">Nenhum prêmio cadastrado ainda.</p>';
    return;
  }

  lista.innerHTML = premios.map(p => {
    return `
      <div class="prize-item${!p.ativo ? ' prize-inactive' : ''}">
        <div class="prize-item-top">
          <div>
            <p class="prize-name">${esc(p.nome)}</p>
            <p class="prize-desc">${esc(p.descricao || '')}</p>
          </div>
          <span class="prize-price">${Number(p.preco ?? p.custoFofocoins) || 0} Fofocoins</span>
        </div>
        <div class="prize-buttons">
          <button class="redeem" ${!p.ativo ? 'disabled' : ''}>Resgatar</button>
        </div>
      </div>
    `;
  }).join('');
}
