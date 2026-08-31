let fofocoinsSaldo = 0;

function renderFofocoins() {
  const el = document.getElementById('fofocoinsSaldo');

  if (el) el.textContent = fofocoinsSaldo;
}

document.getElementById('btnHistoricoCoins')?.addEventListener('click', () => {
  const historico = Array.isArray(fofocoins?.historico) ? fofocoins.historico : [];
  abrirModal('Histórico de Fofocoins', historico.length ? historico.map(item => `
    <div class="row">
      <div class="row-left"><p class="row-item">${esc(item.motivo || 'Movimentação')}</p><p class="row-date">${esc(item.data || '')}</p></div>
      <div class="row-right"><span class="row-value">${Number(item.valor) > 0 ? '+' : ''}${Number(item.valor) || 0}</span></div>
    </div>
  `).join('') : '<p class="empty">Nenhuma movimentação de Fofocoins.</p>');
});

document.getElementById('btnAjustarCoins')?.addEventListener('click', () => {
  const novoValor = prompt('Novo saldo de Fofocoins:', fofocoinsSaldo);
  if (novoValor === null) return;
  const val = Number(novoValor);
  if (!Number.isFinite(val) || val < 0) {
    alert('Valor inválido.');
    return;
  }
  fofocoinsSaldo = val;
  renderFofocoins();
  salvarEstado();
});

document.getElementById('btnTopResgatar')?.addEventListener('click', () => {
  abrirAba('premios');
});
