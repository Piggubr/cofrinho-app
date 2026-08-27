let fofocoinsSaldo = 0;

function renderFofocoins() {
  const el = document.getElementById('fofocoinsSaldo');
  const elV2 = document.getElementById('topCoinsSaldoV2');

  if (el) el.textContent = fofocoinsSaldo;
  if (elV2) elV2.textContent = fofocoinsSaldo;
}

document.getElementById('btnHistoricoCoins')?.addEventListener('click', () => {
  abrirModal('Histórico de Fofocoins', `
    <p class="empty">Histórico será implementado aqui.</p>
  `);
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