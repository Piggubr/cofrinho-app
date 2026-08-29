function atualizarSaudacaoV2() {
  const saudacaoEl = document.getElementById('saudacaoV2');
  if (saudacaoEl) {
    saudacaoEl.textContent = 'Bem-vindo(a) de volta!';
  }
}

function atualizarRelogios() {
  const agora = new Date();
  const horario = document.getElementById('horarioShell');
  const data = document.getElementById('dataHoje');
  const dataFormatada = new Intl.DateTimeFormat('pt-BR', {
    weekday: 'short', day: '2-digit', month: 'short', year: 'numeric'
  }).format(agora).replace('.', '');
  const horaLisboa = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'Europe/Lisbon', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(agora);
  const horaBrasil = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Bahia', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(agora);
  if (horario) horario.textContent = dataFormatada;
  if (data) data.textContent = `Lisboa ${horaLisboa} | Brasil ${horaBrasil}`;
}

function posicionarInformacoesCabecalho() {
  const meta = document.getElementById('v2Meta');
  const painel = document.getElementById('navPanelV2');
  const informacoes = document.querySelector('.info-row');
  if (!meta || !painel || !informacoes) return;

  if (window.matchMedia('(max-width: 899px)').matches) {
    if (informacoes.parentElement !== meta) meta.prepend(informacoes);
  } else {
    const moedas = painel.querySelector('.top-coins');
    if (informacoes.parentElement !== painel) painel.insertBefore(informacoes, moedas);
    painel.classList.remove('open');
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

document.getElementById('btnAbrirListinha')?.addEventListener('click', () => {
  abrirAba('compras');
});
