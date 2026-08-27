function render() {
  const mesKey = mesKeyDe(viewDate);
  const gastosDoMes = gastos.filter(g => g.data.startsWith(mesKey));
  const total = gastosDoMes.reduce((s, g) => s + g.valor, 0);

  document.getElementById('mTotal').textContent = fmt(total);

  const porCategoria = {};
  gastosDoMes.forEach(g => {
    porCategoria[g.categoria] = (porCategoria[g.categoria] || 0) + g.valor;
  });

  const entradas = Object.entries(porCategoria).sort((a, b) => b[1] - a[1]);
  document.getElementById('mCategoria').textContent = entradas.length ? entradas[0][0] : '—';

  const dias = new Set(gastosDoMes.map(g => g.data));
  document.getElementById('mMedia').textContent = fmt(total / (dias.size || 1));

  const totalFixos = gastosDoMes.filter(g => g.tipo === 'Fixo').reduce((s, g) => s + g.valor, 0);
  document.getElementById('mFixos').textContent = fmt(totalFixos);

  renderMeta(total);
}

function renderMeta(totalDisponivel) {
  // Implementação simplificada; ajustar conforme sua lógica original de meta
  const metaEl = document.getElementById('metaValor');
  const barraEl = document.getElementById('metaBarra');
  const statusEl = document.getElementById('metaStatus');

  if (!metaEl || !barraEl || !statusEl) return;

  const metaDefinida = typeof window.metaValor === 'number' && window.metaValor > 0;
  metaEl.textContent = metaDefinida ? fmt(window.metaValor) : 'Ainda não definida';

  if (metaDefinida && totalDisponivel > 0) {
    const pct = Math.min(100, (totalDisponivel / window.metaValor) * 100);
    barraEl.style.width = pct + '%';
    barraEl.classList.toggle('over', totalDisponivel > window.metaValor);
    statusEl.textContent = totalDisponivel > window.metaValor
      ? 'Você ultrapassou o orçamento do mês.'
      : 'Defina quanto está disponível para este mês.';
  } else {
    barraEl.style.width = '0%';
    barraEl.classList.remove('over');
    statusEl.textContent = 'Defina quanto está disponível para este mês.';
  }
}