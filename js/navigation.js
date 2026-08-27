function atualizarSaudacaoV2() {
  const hora = new Date().getHours();
  const saudacaoEl = document.getElementById('saudacaoV2');
  if (saudacaoEl) {
    saudacaoEl.textContent = 'Bem-vinda de volta';
  }
}

function feedEstaAberto() {
  return document.getElementById('tab-dump')?.classList.contains('active');
}

document.getElementById('mesPrev')?.addEventListener('click', async () => {
  viewDate.setMonth(viewDate.getMonth() - 1);
  atualizarTudo();
  if (feedEstaAberto()) {
    await carregarFotosDoMes();
  }
});

document.getElementById('mesNext')?.addEventListener('click', async () => {
  viewDate.setMonth(viewDate.getMonth() + 1);
  atualizarTudo();
  if (feedEstaAberto()) {
    await carregarFotosDoMes();
  }
});

document.getElementById('btnAbrirSurpresa')?.addEventListener('click', () => {
  abrirAba('surpresa');
});

document.getElementById('btnVoltarFilmes')?.addEventListener('click', () => {
  abrirAba('filmes');
});

function abrirAba(nomeAba) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));

  const panel = document.getElementById('tab-' + nomeAba);
  const btn = document.querySelector('.tab-btn[data-tab="' + nomeAba + '"]');

  if (panel) panel.classList.add('active');
  if (btn) btn.classList.add('active');
}

document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    abrirAba(btn.dataset.tab);
  });
});